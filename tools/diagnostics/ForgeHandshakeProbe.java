import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.time.OffsetDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Read-only JDI observation of Forge login; never invokes target methods or writes target fields. */
public final class ForgeHandshakeProbe {
    private static final Set<String> WATCHED = Set.of(
            "net.minecraftforge.network.NetworkInstance",
            "net.minecraftforge.network.NetworkHooks",
            "net.minecraftforge.network.HandshakeHandler",
            "net.minecraft.network.Connection",
            "net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl");
    private static final Set<String> BREAKS = Set.of(
            "registerClientLoginChannel", "onCustomPayload", "dispatch", "handleError",
            "channelRead0", "setProtocol", "m_129498_");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: ForgeHandshakeProbe <loopback-port> <seconds>");
        int port = Integer.parseInt(args[0]);
        long deadline = System.nanoTime() + Long.parseLong(args[1]) * 1_000_000_000L;
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        VirtualMachine vm = null;
        while (vm == null && System.nanoTime() < deadline) {
            var options = connector.defaultArguments();
            options.get("hostname").setValue("127.0.0.1");
            options.get("port").setValue(Integer.toString(port));
            options.get("timeout").setValue("1000");
            try { vm = connector.attach(options); }
            catch (java.io.IOException unavailable) { Thread.sleep(250); }
        }
        if (vm == null) throw new IllegalStateException("No owned loopback JDWP target became available");
        try {
            emit("ATTACHED " + vm.name() + " " + vm.version());
            if (Boolean.getBoolean("forge.probe.snapshotOnly")) {
                snapshot(vm);
                return;
            }
            var requests = vm.eventRequestManager();
            for (String name : WATCHED) {
                var prepare = requests.createClassPrepareRequest();
                prepare.addClassFilter(name);
                prepare.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                prepare.enable();
                for (ReferenceType type : vm.classesByName(name)) watch(vm, type);
            }
            var exceptions = requests.createExceptionRequest(null, true, true);
            exceptions.addClassFilter("net.minecraftforge.network.*");
            exceptions.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            exceptions.enable();
            vm.resume();
            boolean alive = true;
            while (alive && System.nanoTime() < deadline) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                try {
                    for (Event event : events) {
                        if (event instanceof ClassPrepareEvent prepared) watch(vm, prepared.referenceType());
                        else if (event instanceof BreakpointEvent breakpoint) observe(breakpoint);
                        else if (event instanceof ExceptionEvent exception) {
                            emit("EXCEPTION " + describe(exception.exception()) + " at " + exception.location());
                            stack(exception.thread());
                        } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) alive = false;
                    }
                } finally { events.resume(); }
            }
        } catch (VMDisconnectedException normalExit) {
            emit("TARGET_DISCONNECTED");
        } finally {
            try { vm.dispose(); } catch (VMDisconnectedException alreadyClosed) { }
        }
    }

    private static void watch(VirtualMachine vm, ReferenceType type) throws Exception {
        if (!WATCHED.contains(type.name())) return;
        for (Method method : type.methods()) {
            boolean query = type.name().endsWith("ClientHandshakePacketListenerImpl")
                    && method.argumentTypeNames().equals(List.of("net.minecraft.network.protocol.login.ClientboundCustomQueryPacket"));
            if ((BREAKS.contains(method.name()) || query) && !method.isAbstract() && !method.isNative()) {
                var breakpoint = vm.eventRequestManager().createBreakpointRequest(method.location());
                breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                breakpoint.enable();
                if (query) {
                    Path directory = Path.of(System.getProperty("forge.probe.output", "build/handshake-probe-runtime"));
                    Files.createDirectories(directory);
                    String name = type.name() + "-" + type.classObject().uniqueID();
                    Files.write(directory.resolve(name + ".bytecode"), method.bytecodes());
                    if (vm.canGetConstantPool()) Files.write(directory.resolve(name + ".constant-pool"), type.constantPool());
                    Files.writeString(directory.resolve(name + ".txt"), method.name() + method.signature()
                            + "\nmodule=" + type.module().name() + "\nconstantPoolCount=" + type.constantPoolCount());
                }
            }
        }
        emit("WATCHING " + type.name() + " loader=" + (type.classLoader() == null ? "bootstrap" : type.classLoader().uniqueID()));
    }

    private static void observe(BreakpointEvent event) throws Exception {
        var frame = event.thread().frame(0);
        String method = event.location().method().name();
        emit("ENTER " + event.location() + " thread=" + event.thread().name());
        ObjectReference self = frame.thisObject();
        if (self != null && self.referenceType().name().endsWith("NetworkInstance")) {
            emit("CHANNEL " + field(self, "channelName"));
            Value bus = value(self, "networkEventBus");
            if (bus instanceof ObjectReference object) {
                for (Field f : object.referenceType().allFields()) {
                    if (!f.isStatic() && f.typeName().equals("boolean")) emit("BUS " + f.name() + "=" + object.getValue(f));
                }
            }
        }
        var arguments = event.location().method().isBridge() ? List.<Value>of() : frame.getArgumentValues();
        for (Value argument : arguments) {
            if (argument instanceof ObjectReference object && object.referenceType().name().endsWith("CustomQueryPacket")) {
                for (Field f : object.referenceType().allFields()) {
                    if (!f.isStatic() && !f.typeName().contains("ByteBuf")) emit("QUERY " + f.name() + "=" + describe(object.getValue(f)));
                }
            }
        }
        if (self != null && self.referenceType().name().equals("net.minecraft.network.Connection")) {
            for (Field f : self.referenceType().allFields()) {
                if (!f.isStatic() && f.typeName().equals("net.minecraft.network.PacketListener")) emit("PACKET_LISTENER " + describe(self.getValue(f)));
            }
        }
        if (method.equals("handleError")) {
            emit("DISPATCH_ERROR " + describe(arguments.get(arguments.size() - 1)));
            stack(event.thread());
        }
    }

    private static void snapshot(VirtualMachine vm) throws Exception {
        vm.suspend();
        try {
            for (ReferenceType type : vm.classesByName("net.minecraft.client.Minecraft")) {
                for (Field singleton : type.allFields()) {
                    if (!singleton.isStatic() || !singleton.typeName().equals(type.name())) continue;
                    if (!(type.getValue(singleton) instanceof ObjectReference minecraft)) continue;
                    for (Field screenField : minecraft.referenceType().allFields()) {
                        if (screenField.isStatic() || !screenField.typeName().equals("net.minecraft.client.gui.screens.Screen")) continue;
                        if (!(minecraft.getValue(screenField) instanceof ObjectReference screen)) continue;
                        emit("SCREEN " + describe(screen));
                        for (Field connectionField : screen.referenceType().allFields()) {
                            if (connectionField.isStatic() || !connectionField.typeName().equals("net.minecraft.network.Connection")) continue;
                            if (!(screen.getValue(connectionField) instanceof ObjectReference connection)) continue;
                            for (Field f : connection.referenceType().allFields()) {
                                if (f.isStatic()) continue;
                                Value data = connection.getValue(f);
                                if (data instanceof PrimitiveValue || f.typeName().equals("net.minecraft.network.PacketListener")) emit("CONNECTION " + f.name() + "=" + describe(data));
                                if (!f.typeName().equals("io.netty.channel.Channel") || !(data instanceof ObjectReference channel)) continue;
                                if (value(channel, "config") instanceof ObjectReference config) emit("AUTO_READ " + field(config, "autoRead"));
                                if (value(channel, "attributes") instanceof ArrayReference attributes) {
                                    for (Value entry : attributes.getValues()) {
                                        if (entry instanceof ObjectReference attribute) emit("ATTRIBUTE " + field(attribute, "key") + " value=" + field(attribute, "value"));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (ThreadReference thread : vm.allThreads()) {
                if (thread.name().equals("Render thread") || thread.name().startsWith("Netty Client IO")) {
                    emit("THREAD " + thread.name());
                    stack(thread);
                }
            }
            for (ReferenceType hooks : vm.classesByName("net.minecraftforge.server.ServerLifecycleHooks")) {
                for (Field f : hooks.allFields()) {
                    if (!f.isStatic() || !f.typeName().equals("net.minecraft.server.MinecraftServer")) continue;
                    if (!(hooks.getValue(f) instanceof ObjectReference server)) continue;
                    for (Field sf : server.referenceType().allFields()) {
                        if (sf.isStatic() || !sf.typeName().equals("net.minecraft.server.network.ServerConnectionListener")) continue;
                        if (!(server.getValue(sf) instanceof ObjectReference listener)) continue;
                        for (Field lf : listener.referenceType().allFields()) {
                            if (lf.isStatic() || !lf.typeName().equals("java.util.List")) continue;
                            if (!(listener.getValue(lf) instanceof ObjectReference list)) continue;
                            for (Value entry : listValues(list, 0)) {
                                if (entry instanceof ObjectReference connection && connection.referenceType().name().equals("net.minecraft.network.Connection")) {
                                    emit("SERVER_CONNECTION");
                                    for (Field cf : connection.referenceType().allFields()) {
                                        if (cf.isStatic()) continue;
                                        Value data = connection.getValue(cf);
                                        if (data instanceof PrimitiveValue || cf.typeName().equals("net.minecraft.network.PacketListener")) emit("SERVER " + cf.name() + "=" + describe(data));
                                        if (cf.typeName().equals("io.netty.channel.Channel") && data instanceof ObjectReference channel) {
                                            if (value(channel, "config") instanceof ObjectReference config) emit("SERVER_AUTO_READ " + field(config, "autoRead"));
                                            if (value(channel, "attributes") instanceof ArrayReference attrs) for (Value attribute : attrs.getValues()) {
                                                if (attribute instanceof ObjectReference a) emit("SERVER_ATTRIBUTE " + field(a, "key") + " value=" + field(a, "value"));
                                            }
                                            if (value(channel, "unsafe") instanceof ObjectReference unsafe) emit("SERVER_OUTBOUND " + field(unsafe, "outboundBuffer"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally { vm.resume(); }
    }

    private static List<Value> listValues(ObjectReference list, int depth) {
        if (depth > 4) return List.of();
        for (String field : List.of("elementData", "a")) {
            if (value(list, field) instanceof ArrayReference array) {
                int length = array.length();
                if (value(list, "size") instanceof IntegerValue size) length = Math.min(length, size.value());
                return array.getValues(0, length);
            }
        }
        for (String field : List.of("list", "c")) {
            if (value(list, field) instanceof ObjectReference wrapped) return listValues(wrapped, depth + 1);
        }
        return List.of();
    }

    private static Value value(ObjectReference object, String name) {
        return object.referenceType().allFields().stream().filter(f -> !f.isStatic() && f.name().equals(name))
                .findFirst().map(object::getValue).orElse(null);
    }
    private static String field(ObjectReference object, String name) { return describe(value(object, name)); }
    private static String describe(Value value) {
        if (value == null) return "null";
        if (value instanceof StringReference text) return text.value();
        if (value instanceof PrimitiveValue) return value.toString();
        if (value instanceof ObjectReference object) {
            StringBuilder text = new StringBuilder(object.referenceType().name());
            for (Field f : object.referenceType().allFields()) {
                if (f.isStatic()) continue;
                Value content = object.getValue(f);
                if (content instanceof StringReference string) text.append(' ').append(f.name()).append('=').append(string.value());
                else if (content instanceof PrimitiveValue primitive) text.append(' ').append(f.name()).append('=').append(primitive);
            }
            return text.toString();
        }
        return value.type().name();
    }
    private static void stack(ThreadReference thread) throws IncompatibleThreadStateException {
        for (StackFrame frame : thread.frames()) emit("  at " + frame.location());
    }
    private static void emit(String line) { System.out.println(OffsetDateTime.now() + " " + line); }
}

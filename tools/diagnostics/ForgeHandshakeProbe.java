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
        var arguments = frame.getArgumentValues();
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

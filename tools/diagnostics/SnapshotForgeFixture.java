import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Late-attach fixture observation without JDWP startup flags, class redefinition, or game-state writes. */
public final class SnapshotForgeFixture {
    private static Instrumentation agent;
    private static final StringBuilder output = new StringBuilder();

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: SnapshotForgeFixture <pid> <expected-game-directory>");
        Path jar = Path.of(SnapshotForgeFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        VirtualMachine target = VirtualMachine.attach(args[0]);
        try { target.loadAgent(jar.toString(), Path.of(args[1]).toRealPath().toString()); }
        finally { target.detach(); }
    }

    public static void agentmain(String expectedDirectory, Instrumentation instrumentation) throws Exception {
        agent = instrumentation;
        Class<?> minecraftType = Arrays.stream(agent.getAllLoadedClasses())
                .filter(c -> c.getName().equals("net.minecraft.client.Minecraft")).findFirst().orElseThrow();
        Object minecraft = null;
        for (Field f : minecraftType.getDeclaredFields()) if (Modifier.isStatic(f.getModifiers()) && f.getType() == minecraftType) minecraft = read(f, null);
        if (minecraft == null) throw new IllegalStateException("No native Minecraft client instance");
        Object directory = readNamed(minecraft, "gameDirectory", "f_91069_");
        Path expected = Path.of(expectedDirectory).toRealPath();
        if (!(directory instanceof java.io.File file) || !file.toPath().toRealPath().equals(expected)) throw new SecurityException("Unexpected native game directory");
        output.append(OffsetDateTime.now()).append(" native fixture ").append(expected).append('\n');
        for (Field f : fields(minecraft.getClass())) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType().getName().equals("net.minecraft.client.gui.screens.Screen")) {
                Object screen = read(f, minecraft);
                if (screen == null) continue;
                emit("SCREEN", screen.getClass().getName());
                for (Field cf : fields(screen.getClass())) {
                    if (!Modifier.isStatic(cf.getModifiers()) && cf.getType().getName().equals("net.minecraft.network.Connection")) {
                        Object connection = read(cf, screen);
                        if (connection == null) continue;
                        for (Field vf : fields(connection.getClass())) {
                            if (Modifier.isStatic(vf.getModifiers())) continue;
                            Object value = read(vf, connection);
                            if (vf.getType().isPrimitive()) emit("CONNECTION " + vf.getName(), value);
                            if (vf.getType().getName().equals("net.minecraft.network.PacketListener") && value != null) emit("PACKET_LISTENER", value.getClass().getName());
                            if (vf.getType().getName().equals("io.netty.channel.Channel") && value != null) {
                                Object config = readNamed(value, "config");
                                emit("AUTO_READ", readNamed(config, "autoRead"));
                                emit("READ_PENDING", readNamed(value, "readPending"));
                                Object key = readNamed(value, "selectionKey");
                                if (key != null) for (Field kf : fields(key.getClass())) {
                                    if (!Modifier.isStatic(kf.getModifiers()) && kf.getType().isPrimitive()) emit("SELECTION_KEY " + kf.getName(), read(kf, key));
                                }
                                Object attrs = readNamed(value, "attributes");
                                if (attrs instanceof Object[] entries) for (Object entry : entries) if (entry != null) {
                                    Object attributeKey = readNamed(entry, "key");
                                    Object name = readNamed(attributeKey, "name");
                                    Object data = readNamed(entry, "value");
                                    emit("ATTRIBUTE " + name, data == null || data instanceof String || data instanceof Enum<?> ? data : data.getClass().getName());
                                }
                            }
                        }
                    }
                }
            }
        }
        Files.writeString(expected.resolve("native-connection-snapshot.txt"), output.toString());
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) result.addAll(Arrays.asList(c.getDeclaredFields()));
        return result;
    }
    private static Object readNamed(Object object, String... names) throws Exception {
        if (object == null) return null;
        for (String name : names) for (Field f : fields(object.getClass())) if (f.getName().equals(name)) return read(f, object);
        return null;
    }
    private static Object read(Field field, Object object) throws Exception {
        if (!field.trySetAccessible()) {
            Module owner = field.getDeclaringClass().getModule();
            agent.redefineModule(owner, Set.of(), Map.of(), Map.of(field.getDeclaringClass().getPackageName(), Set.of(SnapshotForgeFixture.class.getModule())), Set.of(), Map.of());
            if (!field.trySetAccessible()) throw new IllegalAccessException(field.toString());
        }
        return field.get(object);
    }
    private static void emit(String key, Object value) { output.append(key).append('=').append(value).append('\n'); }
}

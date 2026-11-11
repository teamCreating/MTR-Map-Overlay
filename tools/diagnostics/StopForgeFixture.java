import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;

/** Graceful native halt of an explicitly identified local Forge fixture whose console pipe was lost. */
public final class StopForgeFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: StopForgeFixture <pid> <expected-fixture-directory>");
        Path directory = Path.of(args[1]).toRealPath();
        Path jar = Path.of(StopForgeFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        VirtualMachine target = VirtualMachine.attach(args[0]);
        try { target.loadAgent(jar.toString(), directory.toString()); }
        finally { target.detach(); }
    }

    public static void agentmain(String expectedDirectory, Instrumentation instrumentation) throws Exception {
        Path expected = Path.of(expectedDirectory).toRealPath();
        Path actual = Path.of(System.getProperty("user.dir")).toRealPath();
        if (!actual.equals(expected)) throw new SecurityException("Refusing to stop a JVM outside the named fixture directory");
        Class<?> hooks = Arrays.stream(instrumentation.getAllLoadedClasses())
                .filter(c -> c.getName().equals("net.minecraftforge.server.ServerLifecycleHooks"))
                .findFirst().orElseThrow(() -> new IllegalStateException("No running Forge server hooks"));
        Object server = hooks.getMethod("getCurrentServer").invoke(null);
        if (server == null) throw new IllegalStateException("No current native server");
        Method halt = Arrays.stream(server.getClass().getMethods())
                .filter(m -> (m.getName().equals("halt") || m.getName().equals("m_7570_"))
                        && Arrays.equals(m.getParameterTypes(), new Class<?>[]{boolean.class}))
                .findFirst().orElseThrow(() -> new NoSuchMethodException("Native Forge fixture halt API"));
        halt.invoke(server, false);
        System.out.println("[FixtureControl] Requested native halt and save for " + actual);
    }
}

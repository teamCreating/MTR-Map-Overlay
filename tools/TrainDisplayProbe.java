import io.github.teamcreating.mtrmap.train.TrainSnapshot.*;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Synthetic buffer query/build costs only: no GPU, map host, network or TSC simulation.
 * Run with build/classes/java/main on the classpath. Optional first arg is a baseline buffer class. */
public final class TrainDisplayProbe {
    private static volatile long blackhole;

    public static void main(String[] args) throws Throwable {
        Class<?> type = Class.forName(args.length == 0 ? "io.github.teamcreating.mtrmap.train.TrainAnchorBuffer" : args[0]);
        Object buffer = type.getConstructor().newInstance();
        var cars = new ArrayList<Car>();
        for (int i = 0; i < 8; i++) cars.add(new Car("model", 20, 3, 0));
        type.getMethod("define", List.class).invoke(buffer,
                List.of(new NamedDefinition(1, new Definition(cars, "route", "end", 0x12AB34))));
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle accept = lookup.findVirtual(type, "accept", MethodType.methodType(boolean.class, Frame.class, long.class)).bindTo(buffer);
        MethodHandle query = lookup.findVirtual(type, "cars", MethodType.methodType(List.class, long.class,
                double.class, double.class, double.class, double.class, double.class)).bindTo(buffer);
        var first = poses(0); var second = poses(10);
        boolean ignored = (boolean) accept.invokeExact(new Frame(1, 1000, 250, first), 0L);
        ignored = (boolean) accept.invokeExact(new Frame(2, 1250, 500, second), 250_000_000L);
        System.out.println("buffer=" + type.getSimpleName() + ", fleet=2000x8, JVM=" + Runtime.version());
        measure(query, "local", -100, -100, 400, 400, 1, 1500);
        measure(query, "whole-world", -100, -100, 200_000, 40_000, .1, 200);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) ignored = (boolean) accept.invokeExact(new Frame(i + 3, 1500L + i * 250,
                750L + i * 250, (i & 1) == 0 ? first : second), 500_000_000L + i * 250_000_000L);
        System.out.printf(Locale.ROOT, "packet-accept=%.3f ms/frame%n", (System.nanoTime() - start) / 1e8);
    }

    private static List<Pose> poses(int displacement) {
        var poses = new ArrayList<Pose>();
        for (int id = 1; id <= 2000; id++) {
            float[] anchors = new float[48];
            for (int i = 0; i < 8; i++) { anchors[i * 6] = -i * 21; anchors[i * 6 + 3] = -i * 21 - 12; }
            poses.add(new Pose(id, 1, false, false, ((id - 1) % 100) * 2000 + displacement, 64,
                    ((id - 1) / 100) * 2000, anchors));
        }
        return poses;
    }

    private static void measure(MethodHandle query, String label, double minX, double minZ,
            double maxX, double maxZ, double scale, int repetitions) throws Throwable {
        for (int i = 0; i < repetitions; i++) blackhole += ((List<?>) query.invokeExact(375_000_000L, minX, minZ, maxX, maxZ, scale)).size();
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long allocated = bean.getCurrentThreadAllocatedBytes(), start = System.nanoTime();
        int count = 0;
        for (int i = 0; i < repetitions; i++) {
            List<?> result = (List<?>) query.invokeExact(375_000_000L, minX, minZ, maxX, maxZ, scale);
            count = result.size(); blackhole += count;
        }
        System.out.printf(Locale.ROOT, "%s: %.3f ms/query, %.1f KiB/query, %d car symbols%n", label,
                (System.nanoTime() - start) / (repetitions * 1e6),
                (bean.getCurrentThreadAllocatedBytes() - allocated) / (repetitions * 1024.0), count);
    }
}

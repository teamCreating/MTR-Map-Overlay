import io.github.teamcreating.mtrmap.mapdata.*;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/** Headless geometry/cache workload. Excludes rendering, network, Core and map UI. */
public final class PlanarCacheProbe {
    private static volatile long blackHole;

    public static void main(String[] args) {
        final List<MapTrack> tracks = new ArrayList<>();
        final Map<String, List<TrackRoutePalette.Entry>> palette = new HashMap<>();
        final var lanes = List.of(
                new TrackRoutePalette.Entry("a", "a", 0xFF0000, RouteLineStyle.NORMAL),
                new TrackRoutePalette.Entry("b", "b", 0x00FF00, RouteLineStyle.NORMAL),
                new TrackRoutePalette.Entry("c", "c", 0x0000FF, RouteLineStyle.NORMAL),
                new TrackRoutePalette.Entry("d", "d", 0xFFFF00, RouteLineStyle.NORMAL));
        for (int i = 0; i < 100; i++) {
            final List<double[]> points = new ArrayList<>();
            for (int j = 0; j <= 100; j++) points.add(new double[] {j * 8.0, i * 8.0, 64});
            final MapTrack track = new MapTrack("r" + i, points);
            tracks.add(track);
            palette.put(track.id, lanes);
        }
        // 40 immutable pose revisions, held for 3 frames each: 20Hz poses at 60 render frames/s.
        final List<List<MapCar>> revisions = new ArrayList<>();
        for (int pose = 0; pose < 40; pose++) {
            final List<MapCar> cars = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                final double x = (i % 25) * 30 + 10 + pose * 0.05, z = (i / 25) * 30 + 10;
                cars.add(new MapCar(i, 0, 1, true, "model", "route", "dest", 0xFF0000,
                        new MapVertex(x + 10, 64, z + 1.5), new MapVertex(x - 10, 64, z + 1.5),
                        new MapVertex(x - 10, 64, z - 1.5), new MapVertex(x + 10, 64, z - 1.5)));
            }
            revisions.add(List.copyOf(cars));
        }
        final Object source = new Object();
        final PlanarMapCache warmup = new PlanarMapCache();
        for (int i = 0; i < 12; i++) {
            consume(PlanarMapScene.build(tracks, palette, Map.of(), revisions.get(i),
                    true, true, 1, -100, -100, 1000, 1000));
            consume(warmup.get(source, tracks, palette, Map.of(), revisions.get(i),
                    true, true, 1, -100, -100, 1000, 1000));
        }
        System.out.println("mode,frames,staticBuilds,carBuilds,geometryMs,threadAllocatedMiB");
        final int frames = 120;
        measure("uncached", frames, frames, frames, frame -> PlanarMapScene.build(tracks, palette,
                Map.of(), revisions.get(frame / 3), true, true, 1, -100, -100, 1000, 1000));
        final PlanarMapCache cached = new PlanarMapCache();
        final Result result = run(frames, frame -> cached.get(source, tracks, palette, Map.of(),
                revisions.get(frame / 3), true, true, 1, -100, -100, 1000, 1000));
        print("cached", frames, cached.staticBuilds(), cached.carBuilds(), result);
    }

    private static void measure(String name, int frames, long staticBuilds, long carBuilds,
            IntFunction<PlanarMapScene> operation) {
        print(name, frames, staticBuilds, carBuilds, run(frames, operation));
    }

    private static Result run(int frames, IntFunction<PlanarMapScene> operation) {
        final var bean = ManagementFactory.getThreadMXBean();
        final com.sun.management.ThreadMXBean allocations = bean instanceof com.sun.management.ThreadMXBean extended
                && extended.isThreadAllocatedMemorySupported() ? extended : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        final long thread = Thread.currentThread().threadId();
        final long before = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread);
        final long start = System.nanoTime();
        for (int frame = 0; frame < frames; frame++) consume(operation.apply(frame));
        final long elapsed = System.nanoTime() - start;
        final long bytes = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread) - before;
        return new Result(elapsed / 1_000_000.0, bytes < 0 ? -1 : bytes / 1048576.0);
    }

    private static void consume(PlanarMapScene scene) {
        blackHole += scene.quads().size();
        if (scene.quads().size() != 52000) throw new AssertionError("Geometry changed");
    }

    private static void print(String name, int frames, long staticBuilds, long carBuilds, Result result) {
        System.out.printf(java.util.Locale.ROOT, "%s,%d,%d,%d,%.3f,%.3f%n", name, frames,
                staticBuilds, carBuilds, result.millis, result.allocatedMiB);
    }

    private record Result(double millis, double allocatedMiB) {
    }
}

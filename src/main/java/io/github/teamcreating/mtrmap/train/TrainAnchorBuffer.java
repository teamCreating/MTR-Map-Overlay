package io.github.teamcreating.mtrmap.train;

import io.github.teamcreating.mtrmap.mapdata.MapCar;
import io.github.teamcreating.mtrmap.mapdata.MapVertex;
import io.github.teamcreating.mtrmap.mapdata.TrainDisplayScale;
import java.util.*;
import static io.github.teamcreating.mtrmap.train.TrainSnapshot.*;

/** Two measured frames, no velocity integration, rail data, or simulation. */
public final class TrainAnchorBuffer {
    private final Map<Integer, Definition> definitions = new HashMap<>();
    private final Map<Integer, Pose> previous = new HashMap<>();
    private Frame latest;
    private TrainViewportIndex viewport = new TrainViewportIndex(List.of());
    private int unlocatable;
    private boolean motion;
    private boolean slow;
    private long receivedNanos, sampledNanos, intervalNanos = 250_000_000L;

    public void define(List<NamedDefinition> values) {
        for (var d : values) {
            var existing = definitions.get(d.id());
            if (existing != null && !existing.equals(d.value())) throw new IllegalArgumentException("Definition ID reused");
        }
        if (definitions.size() + values.stream().filter(d -> !definitions.containsKey(d.id())).count() > MAX_TRAINS * 2L)
            throw new IllegalArgumentException("Definition cache limit");
        for (var d : values) definitions.put(d.id(), d.value());
    }

    public boolean accept(Frame frame, long now) {
        return accept(frame, now, 0);
    }

    public boolean accept(Frame frame, long now, long sourceAgeNanos) {
        if (latest != null && frame.sequence() <= latest.sequence()) return false;
        // Validate everything before publishing any of the new membership.
        for (Pose p : frame.trains()) if (p.cars() > 0) {
            Definition d = definitions.get(p.definition);
            if (d == null || d.cars().size() != p.cars()) throw new IllegalArgumentException("Missing train definition");
        }
        previous.clear();
        slow = sourceAgeNanos > 750_000_000L || (latest != null && frame.captureMillis() - latest.captureMillis() > 500);
        if (latest != null && frame.coreMillis() > latest.coreMillis() && frame.captureMillis() > latest.captureMillis()) {
            for (Pose p : latest.trains()) previous.put(p.handle, p);
            intervalNanos = Math.min(1_000_000_000L, Math.max(50_000_000L,
                    (frame.captureMillis() - latest.captureMillis()) * 1_000_000L));
        }
        boolean fresh = latest == null || frame.coreMillis() != latest.coreMillis();
        latest = frame; receivedNanos = now;
        if (fresh) sampledNanos = now - Math.max(0, sourceAgeNanos);
        unlocatable = 0; motion = false;
        var visible = new ArrayList<TrainViewportIndex.Entry>();
        for (Pose p : frame.trains()) {
            if (p.cars() == 0) unlocatable++;
            Pose before = previous.get(p.handle);
            if (p.cars() == 0) continue;
            if (before == null || before.definition != p.definition || before.reversed != p.reversed
                    || before.cars() != p.cars() || Math.hypot(before.x - p.x, before.z - p.z) > 1024) before = p;
            if (!p.sameAnchors(before)) motion = true;
            visible.add(new TrainViewportIndex.Entry(before, p, definitions.get(p.definition)));
        }
        viewport = new TrainViewportIndex(visible);
        // Server only sends definitions while referenced; do not retain every historic service forever.
        var active = new HashSet<Integer>();
        for (Pose p : frame.trains()) if (p.definition > 0) active.add(p.definition);
        definitions.keySet().retainAll(active);
        return true;
    }

    public List<MapCar> cars(long now, double minX, double minZ, double maxX, double maxZ, double scale) {
        if (latest == null || !Double.isFinite(scale) || scale <= 0) return List.of();
        double t = Math.clamp((now - receivedNanos) / (double) intervalNanos, 0, 1);
        double margin = Math.max(2, 20 / Math.max(scale, 1.0E-6));
        var result = new ArrayList<MapCar>();
        viewport.visit(minX - margin, minZ - margin, maxX + margin, maxZ + margin, entry -> {
            Pose p = entry.current(), a = entry.before();
            var d = entry.definition();
            for (int i = 0; i < p.cars(); i++) {
                var car = d.cars().get(i); int k = i * 6;
                // Same LOD as TrainCarGeometry: do not allocate hidden trailing-car symbols at world scale.
                if (TrainDisplayScale.marker(car.length(), scale) && i != (p.reversed ? p.cars() - 1 : 0)) continue;
                // Coarse swept bounds before any per-car allocation; include both sample endpoints.
                double radius = car.length() + Math.abs(car.offset()) + margin;
                double left = Math.min(Math.min(a.coordinate(k), a.coordinate(k + 3)), Math.min(p.coordinate(k), p.coordinate(k + 3)));
                double right = Math.max(Math.max(a.coordinate(k), a.coordinate(k + 3)), Math.max(p.coordinate(k), p.coordinate(k + 3)));
                double top = Math.min(Math.min(a.coordinate(k + 2), a.coordinate(k + 5)), Math.min(p.coordinate(k + 2), p.coordinate(k + 5)));
                double bottom = Math.max(Math.max(a.coordinate(k + 2), a.coordinate(k + 5)), Math.max(p.coordinate(k + 2), p.coordinate(k + 5)));
                if (right + radius < minX || left - radius > maxX || bottom + radius < minZ || top - radius > maxZ) continue;
                double ax = lerp(a, p, k, t), az = lerp(a, p, k + 2, t);
                double bx = lerp(a, p, k + 3, t), bz = lerp(a, p, k + 5, t);
                double dx = bx - ax, dz = bz - az, length = Math.hypot(dx, dz);
                if (length < 1.0E-6) { dx = p.coordinate(k + 3) - p.coordinate(k); dz = p.coordinate(k + 5) - p.coordinate(k + 2); length = Math.hypot(dx, dz); }
                if (length < 1.0E-6) continue;
                dx /= length; dz /= length;
                double x = (ax + bx) / 2 - dx * car.offset(), z = (az + bz) / 2 - dz * car.offset();
                double y = (lerp(a, p, k + 1, t) + lerp(a, p, k + 4, t)) / 2;
                double lx = dx * car.length() / 2, lz = dz * car.length() / 2;
                double wx = dz * car.width() / 2, wz = -dx * car.width() / 2;
                result.add(new MapCar(p.handle, i, p.cars(), i == (p.reversed ? p.cars() - 1 : 0),
                        car.model(), d.route(), d.destination(), d.color(),
                        new MapVertex(x + lx + wx, y, z + lz + wz), new MapVertex(x - lx + wx, y, z - lz + wz),
                        new MapVertex(x - lx - wx, y, z - lz - wz), new MapVertex(x + lx - wx, y, z + lz - wz), p.reversed));
            }
        });
        return List.copyOf(result);
    }
    private static double lerp(Pose a, Pose b, int i, double t) { return a.coordinate(i) + (b.coordinate(i) - a.coordinate(i)) * t; }
    public int trains() { return latest == null ? 0 : latest.trains().size(); }
    public int unlocatable() { return unlocatable; }
    public boolean animating(long now) { return motion && now - receivedNanos < intervalNanos; }
    public boolean slow() { return slow; }
    public long ageNanos(long now) { return latest == null ? Long.MAX_VALUE : Math.max(0, now - sampledNanos); }
    public long sequence() { return latest == null ? 0 : latest.sequence(); }
    public void clear() { definitions.clear(); previous.clear(); latest = null; viewport = new TrainViewportIndex(List.of()); receivedNanos = 0; unlocatable = 0; motion = slow = false; }
}

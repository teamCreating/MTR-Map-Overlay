package io.github.teamcreating.mtrmap.train;

import java.util.List;
import java.util.Arrays;

/** Map display data only. No simulation or client/server game classes. */
public final class TrainSnapshot {
    public static final int MAX_TRAINS = 20_000, MAX_CARS = 256, MAX_TOTAL_CARS = 100_000;
    public static final int MAX_BYTES = 16 * 1024 * 1024, CHUNK_BYTES = 192 * 1024;
    public static final byte DEFINITIONS = 0, FRAME = 1;

    private TrainSnapshot() { }

    public record Car(String model, float length, float width, float offset) {
        public Car {
            if (model == null || model.length() > 1024 || !Float.isFinite(length) || length <= 0 || length > 1024
                    || !Float.isFinite(width) || width <= 0 || width > 1024
                    || !Float.isFinite(offset) || Math.abs(offset) > 1024) throw new IllegalArgumentException("Invalid car definition");
        }
    }

    /** Content-addressed by the server; identical consists/services share an ID. */
    public record Definition(List<Car> cars, String route, String destination, int color) {
        public Definition {
            cars = List.copyOf(cars);
            if (cars.isEmpty() || cars.size() > MAX_CARS || route == null || destination == null
                    || route.length() > 4096 || destination.length() > 4096) throw new IllegalArgumentException("Invalid train definition");
        }
    }

    public record NamedDefinition(int id, Definition value) {
        public NamedDefinition { if (id <= 0 || value == null) throw new IllegalArgumentException("Invalid definition ID"); }
    }

    public static final class Pose {
        public final int handle, definition;
        public final boolean reversed, manual;
        public final double x, y, z;
        private final float[] anchors;

        public Pose(int handle, int definition, boolean reversed, boolean manual,
                double x, double y, double z, float[] anchors) {
            if (handle <= 0 || definition < 0 || !finite(x) || !finite(y) || !finite(z)
                    || anchors.length % 6 != 0 || anchors.length > MAX_CARS * 6
                    || (anchors.length != 0 && definition == 0)) throw new IllegalArgumentException("Invalid train pose");
            for (float value : anchors) if (!Float.isFinite(value) || Math.abs(value) > 1_000_000)
                throw new IllegalArgumentException("Invalid relative anchor");
            this.handle = handle; this.definition = definition; this.reversed = reversed; this.manual = manual;
            this.x = x; this.y = y; this.z = z; this.anchors = anchors.clone();
        }

        public int cars() { return anchors.length / 6; }
        public float relative(int index) { return anchors[index]; }
        public boolean sameAnchors(Pose other) {
            return other != null && x == other.x && y == other.y && z == other.z && Arrays.equals(anchors, other.anchors);
        }
        public double coordinate(int index) {
            return (index % 3 == 0 ? x : index % 3 == 1 ? y : z) + anchors[index];
        }
        private static boolean finite(double value) { return Double.isFinite(value) && Math.abs(value) <= 60_000_000; }
    }

    public record Frame(long sequence, long coreMillis, long captureMillis, List<Pose> trains) {
        public Frame {
            trains = List.copyOf(trains);
            if (sequence <= 0 || trains.size() > MAX_TRAINS) throw new IllegalArgumentException("Invalid frame");
            var handles = new java.util.HashSet<Integer>();
            int count = 0;
            for (Pose pose : trains) {
                if (!handles.add(pose.handle) || (count += pose.cars()) > MAX_TOTAL_CARS)
                    throw new IllegalArgumentException("Duplicate train or excessive car count");
            }
        }
    }
}

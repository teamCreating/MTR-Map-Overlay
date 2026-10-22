package com.lx862.mtrmap.train;

import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Vector;
import java.util.*;
import static com.lx862.mtrmap.train.TrainSnapshot.*;

/** Only called on Simulator.run's thread. Exports Core results, never advances vehicles. */
public final class CoreTrainAdapter {
    public record Capture(Frame frame, Map<Integer, Definition> definitions) { }
    private record Key(long siding, long vehicle) { }
    private final Map<Key, Integer> handles = new HashMap<>();
    private final Map<Definition, Integer> definitionIds = new HashMap<>();
    private int nextHandle = 1, nextDefinition = 1;
    private long sequence;
    private final java.util.function.Function<org.mtr.core.data.Siding, ? extends Iterable<Vehicle>> vehicles;
    public CoreTrainAdapter() { this(siding -> ((com.lx862.mtrmap.mixin.SidingVehiclesAccessor) (Object) siding).mtrmap$getVehicles()); }
    CoreTrainAdapter(java.util.function.Function<org.mtr.core.data.Siding, ? extends Iterable<Vehicle>> vehicles) { this.vehicles = vehicles; }

    public Capture capture(Simulator simulator) {
        var poses = new ArrayList<Pose>(); var seen = new HashSet<Key>();
        var definitions = new HashMap<Integer, Definition>();
        simulator.sidings.forEach(siding -> vehicles.apply(siding).forEach(vehicle -> {
            if (vehicle.getTransportMode() != TransportMode.TRAIN) return;
            if (poses.size() >= MAX_TRAINS) throw new IllegalArgumentException("Global train capacity exceeded");
            var key = new Key(siding.getId(), vehicle.getId()); seen.add(key);
            int handle = handles.computeIfAbsent(key, ignored -> nextHandle++);
            Pose pose;
            try {
                var extra = vehicle.vehicleExtraData;
                if (extra.immutablePath.isEmpty()) throw new IllegalArgumentException("No Core path");
                var positions = vehicle.getVehicleCarsAndPositions();
                if (positions.isEmpty() || positions.size() > MAX_CARS) throw new IllegalArgumentException("Invalid Core consist");
                var cars = new ArrayList<Car>(positions.size());
                double[] coordinates = new double[positions.size() * 6];
                for (int i = 0; i < positions.size(); i++) {
                    var car = positions.get(i).left(); var bogies = positions.get(i).right();
                    if (bogies.isEmpty() || bogies.size() > 2) throw new IllegalArgumentException("Invalid bogies");
                    Vector a, b;
                    if (bogies.size() == 1) {
                        a = bogies.get(0).left();
                        b = bogies.get(0).right();
                    } else {
                        a = midpoint(bogies.get(0)); b = midpoint(bogies.get(1));
                    }
                    int k = i * 6;
                    coordinates[k] = a.x; coordinates[k + 1] = a.y; coordinates[k + 2] = a.z;
                    coordinates[k + 3] = b.x; coordinates[k + 4] = b.y; coordinates[k + 5] = b.z;
                    cars.add(new Car(car.getVehicleId(), (float) car.getLength(), (float) car.getWidth(),
                            (float) ((car.getBogie1Position() + car.getBogie2Position()) / 2)));
                }
                // A missing path lookup can produce Core's progress-based placeholder points.
                // Validate their proximity to a separately resolved real head, not just finite values.
                var head = vehicle.getHeadPosition();
                if (head == null) throw new IllegalArgumentException("No Core head location");
                double limit = Math.max(16, extra.getTotalVehicleLength() * 2 + 16);
                for (int k = 0; k < coordinates.length; k += 3) {
                    double dx = coordinates[k] - head.x, dy = coordinates[k + 1] - head.y, dz = coordinates[k + 2] - head.z;
                    if (!Double.isFinite(dx + dy + dz) || Math.sqrt(dx * dx + dy * dy + dz * dz) > limit)
                        throw new IllegalArgumentException("Unlocatable Core car");
                }
                var definition = new Definition(cars, extra.getThisRouteName(), extra.getThisRouteDestination(),
                        extra.getThisRouteName().isBlank() ? 0xA6B3C1 : extra.getThisRouteColor());
                int definitionId = definitionIds.computeIfAbsent(definition, ignored -> nextDefinition++);
                float[] relative = new float[coordinates.length];
                for (int k = 0; k < relative.length; k++) relative[k] = (float) (coordinates[k] - coordinates[k % 3]);
                pose = new Pose(handle, definitionId, vehicle.getReversed(), extra.getIsCurrentlyManual(),
                        coordinates[0], coordinates[1], coordinates[2], relative);
                definitions.put(definitionId, definition);
            } catch (RuntimeException e) {
                // Preserve membership on a per-vehicle failure. Never draw fabricated (0,0,0) cars.
                pose = new Pose(handle, 0, vehicle.getReversed(), false, 0, 0, 0, new float[0]);
            }
            poses.add(pose);
        }));
        handles.keySet().retainAll(seen);
        definitionIds.keySet().retainAll(new HashSet<>(definitions.values()));
        return new Capture(new Frame(++sequence, simulator.getCurrentMillis(), System.nanoTime() / 1_000_000L, poses), Map.copyOf(definitions));
    }

    private static Vector midpoint(org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair<Vector, Vector> bogie) {
        return Vector.getAverage(bogie.left(), bogie.right());
    }
}

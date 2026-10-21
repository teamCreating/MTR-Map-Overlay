package com.lx862.mtrmap.train;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.data.*;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreTrainAdapterTest {
    @TempDir Path root;

    @Test @SuppressWarnings("unchecked")
    void exportsRemoteParkedSingleAndDoubleBogieCarsWithoutMinecraftEntities() throws Exception {
        var sim = new Simulator("minecraft/overworld", new String[]{"minecraft/overworld"}, root, false);
        try {
            var start = new Position(200_000, 75, -200_000);
            var end = new Position(200_512, 75, -200_000);
            var rail = Rail.newSidingRail(start, Angle.E, end, Angle.W, Rail.Shape.QUADRATIC,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new ObjectArrayList<>(), TransportMode.TRAIN);
            assertNotNull(rail); sim.rails.add(rail); sim.sync();
            var path = new PathData(rail, 0, 0, 0, 0, rail.railMath.getLength(), start, Angle.E, end, Angle.W);
            var cars = new ObjectArrayList<VehicleCar>();
            cars.add(new VehicleCar("single", 20, 3, 100, 2, 2, .5, .5));
            cars.add(new VehicleCar("double", 20, 3, 100, 6, -4, .5, .5));
            var extra = VehicleExtraData.create(1, 2, rail.railMath.getLength(), cars,
                    new ObjectArrayList<>(), new ObjectArrayList<>(), new ObjectArrayList<>(), path, false, .001, .001, false, .1, 0);
            var vehicle = new Vehicle(extra, null, TransportMode.TRAIN, sim);
            var state = new JsonObject(); state.addProperty("railProgress", 200); vehicle.updateData(new JsonReader(state));
            assertFalse(vehicle.getIsOnRoute(), "The parked/default-position state must not be filtered out");
            var siding = new Siding(start, end, rail.railMath.getLength(), TransportMode.TRAIN, sim);
            // Fixture injection into the source-known private registry; production only uses iterateVehicles.
            var field = Siding.class.getDeclaredField("vehicleIdMap"); field.setAccessible(true);
            var vehicles = (Map<Long, Vehicle>) field.get(siding); vehicles.put(vehicle.getId(), vehicle);
            sim.sidings.add(siding);
            var adapter = new CoreTrainAdapter(); var snapshot = adapter.capture(sim);
            assertEquals(1, snapshot.frame().trains().size());
            var pose = snapshot.frame().trains().getFirst(); assertEquals(2, pose.cars());
            assertTrue(pose.x > 100_000); assertEquals(75, pose.y, 1.0E-6);
            var nativePositions = vehicle.getVehicleCarsAndPositions();
            assertEquals(nativePositions.get(0).right().getFirst().positionAndTiltAngle1().position().x(), pose.coordinate(0), 1.0E-5);
            var b = nativePositions.get(1).right().getFirst();
            assertEquals((b.positionAndTiltAngle1().position().x() + b.positionAndTiltAngle2().position().x()) / 2, pose.coordinate(6), 1.0E-5);
            assertEquals(2, snapshot.definitions().get(pose.definition).cars().getFirst().offset());
            var next = adapter.capture(sim); assertEquals(pose.handle, next.frame().trains().getFirst().handle);
            vehicles.clear(); assertTrue(adapter.capture(sim).frame().trains().isEmpty());
        } finally { sim.stop(); }
    }
}

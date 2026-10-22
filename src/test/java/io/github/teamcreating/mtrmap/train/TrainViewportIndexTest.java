package io.github.teamcreating.mtrmap.train;

import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.teamcreating.mtrmap.train.TrainSnapshot.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainViewportIndexTest {
    private static final Definition DEFINITION = new Definition(List.of(new Car("model", 20, 3, 5)), "route", "end", 1);

    private static Pose pose(int id, double x, double z, boolean vertical) {
        return new Pose(id, 1, false, false, x, 70, z,
                vertical ? new float[]{0, 0, 0, 0, 0, 12} : new float[]{0, 0, 0, 12, 0, 0});
    }

    @Test void randomizedQueriesMatchExhaustiveSweptBoundsIncludingNegativeFarCoordinates() {
        var random = new Random(314159);
        var entries = new ArrayList<TrainViewportIndex.Entry>();
        for (int id = 1; id <= 2000; id++) {
            double x = random.nextDouble(-30_000_000, 30_000_000), z = random.nextDouble(-30_000_000, 30_000_000);
            entries.add(new TrainViewportIndex.Entry(pose(id, x, z, false),
                    pose(id, x + random.nextDouble(-800, 800), z + random.nextDouble(-800, 800), true), DEFINITION));
        }
        var index = new TrainViewportIndex(entries);
        for (int n = 0; n < 100; n++) {
            var selected = entries.get(n * 17);
            double x = selected.minX() - 100, z = selected.minZ() - 100, width = n % 2 == 0 ? 400 : 8_000_000;
            Set<Integer> expected = new HashSet<>(), actual = new HashSet<>();
            for (var e : entries) if (e.maxX() >= x && e.minX() <= x + width && e.maxZ() >= z && e.minZ() <= z + width)
                expected.add(e.current().handle);
            index.visit(x, z, x + width, z + width, e -> assertTrue(actual.add(e.current().handle)));
            assertEquals(expected, actual);
        }
    }

    @Test void coincidentTrainsRemainCompleteWithoutDuplicateVisitsOrPathologicalDepth() {
        var entries = new ArrayList<TrainViewportIndex.Entry>();
        for (int id = 1; id <= MAX_TRAINS; id++) {
            Pose p = pose(id, 0, 0, false);
            entries.add(new TrainViewportIndex.Entry(p, p, DEFINITION));
        }
        var index = new TrainViewportIndex(entries);
        Set<Integer> ids = new HashSet<>();
        index.visit(-1, -1, 1, 1, e -> assertTrue(ids.add(e.current().handle)));
        assertEquals(MAX_TRAINS, ids.size());
        index.visit(100, 100, 200, 200, e -> fail("Invisible train was visited"));
    }

    @Test void turningAndOffsetCarsStayVisibleBetweenMeasuredFramesAndDeletionClearsIndex() {
        var buffer = new TrainAnchorBuffer();
        buffer.define(List.of(new NamedDefinition(1, DEFINITION)));
        buffer.accept(new Frame(1, 1000, 250, List.of(pose(1, 0, 0, false))), 0);
        buffer.accept(new Frame(2, 1250, 500, List.of(pose(1, 100, 0, true))), 250_000_000);
        var cars = buffer.cars(375_000_000, 40, -15, 60, 20, 3);
        assertEquals(1, cars.size());
        assertTrue(cars.get(0).intersects(40, -15, 60, 20));
        buffer.accept(new Frame(3, 1500, 750, List.of()), 500_000_000);
        assertTrue(buffer.cars(500_000_000, -100, -100, 200, 200, 3).isEmpty());
        buffer.clear();
        assertTrue(buffer.cars(500_000_000, -100, -100, 200, 200, 3).isEmpty());
    }
}

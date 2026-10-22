package io.github.teamcreating.mtrmap.train;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static io.github.teamcreating.mtrmap.train.TrainSnapshot.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainStreamTest {
    private static final Definition DEFINITION = new Definition(List.of(new Car("model", 20, 3, 2)), "线路", "终点", 0x12AB34);
    private static Pose pose(int id, int definition, double x, boolean reversed) {
        return new Pose(id, definition, reversed, false, x, 75, -29_000_000.125,
                new float[]{0, 0, 0, 0, 0.125f, -12});
    }
    private static Frame frame(long sequence, long core, Pose... poses) {
        return new Frame(sequence, core, sequence * 250, List.of(poses));
    }
    private static TrainAnchorBuffer buffer() {
        var result = new TrainAnchorBuffer(); result.define(List.of(new NamedDefinition(1, DEFINITION))); return result;
    }
    private static List<io.github.teamcreating.mtrmap.mapdata.MapCar> cars(TrainAnchorBuffer buffer, long time) {
        return buffer.cars(time, -60_000_000, -60_000_000, 60_000_000, 60_000_000, 1);
    }

    @Test void farCoordinatesSurviveLocalFloatEncodingAndMovingOrigins() throws Exception {
        var first = frame(1, 1000, pose(1, 1, 29_000_000.125, false));
        var second = frame(2, 1250, pose(1, 1, 29_000_010.375, false));
        var decoded = TrainFrameCodec.frame(TrainFrameCodec.frame(second));
        assertEquals(29_000_010.375, decoded.trains().getFirst().coordinate(0));
        assertEquals(-29_000_012.125, decoded.trains().getFirst().coordinate(5));
        var buffer = buffer(); buffer.accept(first, 0); buffer.accept(decoded, 250_000_000);
        assertEquals(29_000_005.25, cars(buffer, 375_000_000).getFirst().center().x(), 1.0E-8);
    }

    @Test void interpolationStopsAtLatestMeasuredPositionAndKeepsRigidLength() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, pose(1, 1, 0, false)), 0);
        buffer.accept(frame(2, 1250, pose(1, 1, 20, false)), 250_000_000);
        var halfway = cars(buffer, 375_000_000).getFirst();
        assertEquals(10, halfway.center().x(), 1.0E-8);
        assertEquals(20, halfway.frontRight().distanceTo(halfway.backRight()), 1.0E-8);
        assertEquals(20, cars(buffer, 20_000_000_000L).getFirst().center().x(), 1.0E-8);
    }

    @Test void reversedAndTimeResetNeverInterpolateAcrossDiscontinuity() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, pose(1, 1, 0, false)), 0);
        buffer.accept(frame(2, 1250, pose(1, 1, 20, true)), 250_000_000);
        assertEquals(20, cars(buffer, 250_000_000).getFirst().center().x(), 1.0E-8);
        assertTrue(cars(buffer, 250_000_000).getFirst().reversed());
        buffer.accept(frame(3, 100, pose(1, 1, 40, true)), 500_000_000);
        assertEquals(40, cars(buffer, 500_000_000).getFirst().center().x(), 1.0E-8);
    }

    @Test void parkedTrainsRemainUntilACompleteFrameActuallyRemovesThem() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, pose(1, 1, 0, false)), 0);
        buffer.accept(frame(2, 60_000, pose(1, 1, 0, false)), 60_000_000_000L);
        assertEquals(1, cars(buffer, 100_000_000_000L).size());
        assertFalse(buffer.accept(frame(1, 1000), 100_000_000_000L));
        assertEquals(1, buffer.trains());
        buffer.accept(frame(3, 60_250), 60_250_000_000L);
        assertEquals(0, buffer.trains());
    }

    @Test void missingDefinitionRejectsWholeFrameWithoutRemovingExistingTrain() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, pose(1, 1, 0, false)), 0);
        assertThrows(IllegalArgumentException.class, () -> buffer.accept(frame(2, 1250, pose(2, 9, 0, false)), 250_000_000));
        assertEquals(1, buffer.sequence()); assertEquals(1, buffer.trains());
        assertEquals(1, cars(buffer, 0).getFirst().vehicleId());
    }

    @Test void unlocatableVehiclePreservesMembershipWithoutOriginMarker() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, new Pose(7, 0, false, false, 0, 0, 0, new float[0])), 0);
        assertEquals(1, buffer.trains()); assertTrue(cars(buffer, 0).isEmpty());
    }

    @Test void staleSourceTimeAndServerQueueDelayAreNotFreshenedByNewPacketSequence() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, pose(1, 1, 0, false)), 5_000_000_000L, 4_000_000_000L);
        assertEquals(4_000_000_000L, buffer.ageNanos(5_000_000_000L));
        assertTrue(buffer.slow());
        buffer.accept(frame(2, 1000, pose(1, 1, 0, false)), 8_000_000_000L);
        assertEquals(7_000_000_000L, buffer.ageNanos(8_000_000_000L));
    }

    @Test void visibilityOnlyFiltersDrawingNotWholeWorldMembership() {
        var buffer = buffer(); buffer.accept(frame(1, 1000, pose(1, 1, 0, false), pose(2, 1, 200_000, false)), 0);
        assertEquals(2, buffer.trains());
        assertEquals(1, buffer.cars(0, 199_990, -29_000_030, 200_010, -28_999_990, 1).size());
        assertEquals(2, cars(buffer, 0).size());
    }

    @Test void overviewUsesActualLeadingEndAndFullConsistReturnsWhenZoomedIn() {
        var definition = new Definition(Collections.nCopies(3, new Car("model", 20, 3, 0)), "route", "end", 1);
        var buffer = new TrainAnchorBuffer();
        buffer.define(List.of(new NamedDefinition(1, definition)));
        float[] anchors = {0, 0, 0, 0, 0, -12, 0, 0, -21, 0, 0, -33, 0, 0, -42, 0, 0, -54};
        buffer.accept(frame(1, 1000, new Pose(1, 1, true, false, 200_000, 64, 0, anchors)), 0);
        var overview = buffer.cars(0, 199_900, -100, 200_100, 100, .5);
        assertEquals(1, overview.size()); assertEquals(2, overview.getFirst().index());
        assertEquals(3, overview.getFirst().count()); assertTrue(overview.getFirst().leading());
        assertEquals(3, buffer.cars(0, 199_900, -100, 200_100, 100, 1).size());
        assertEquals(1, buffer.trains());
    }

    @Test void definitionsAreMergedOnceAndFrameContainsNoMetadataText() throws Exception {
        byte[] one = TrainFrameCodec.definitions(List.of(new NamedDefinition(1, DEFINITION)));
        byte[] two = TrainFrameCodec.definitions(List.of(new NamedDefinition(2,
                new Definition(DEFINITION.cars(), "Other", "Remote", 5))));
        var decoded = TrainFrameCodec.definitions(TrainFrameCodec.mergeDefinitions(List.of(one, two)));
        assertEquals(2, decoded.size()); assertEquals(DEFINITION, decoded.getFirst().value());
        byte[] data = TrainFrameCodec.frame(frame(1, 1000, pose(1, 1, 0, false)));
        assertEquals(77, data.length); // 24 time bytes + counts/handles/flags + 24 origin + 24 coordinates
        assertFalse(new String(data, java.nio.charset.StandardCharsets.UTF_8).contains("model"));
    }

    @Test void invalidWireValuesAndImpossibleLengthsAreRejectedBeforePublication() throws Exception {
        assertThrows(IOException.class, () -> TrainFrameCodec.frame(new byte[]{-1, -1, -1}));
        byte[] bytes = TrainFrameCodec.frame(frame(1, 1000, pose(1, 1, 0, false)));
        byte[] invalid = bytes.clone();
        // Last float is NaN.
        invalid[invalid.length - 4] = 0x7F; invalid[invalid.length - 3] = (byte) 0xC0;
        invalid[invalid.length - 2] = invalid[invalid.length - 1] = 0;
        assertThrows(IOException.class, () -> TrainFrameCodec.frame(invalid));
        assertThrows(IOException.class, () -> TrainFrameCodec.frame(Arrays.copyOf(bytes, bytes.length + 1)));
        assertThrows(IllegalArgumentException.class, () -> frame(1, 1000, pose(1, 1, 0, false), pose(1, 1, 0, false)));
    }

    @Test void atomicAssemblyHandlesDuplicatesAndRejectsConflictsAndPartialFrames() {
        int size = CHUNK_BYTES + 3;
        var assembly = new TrainFrameAssembly(2, size, 0);
        byte[] first = new byte[CHUNK_BYTES];
        assertFalse(assembly.add(0, 2, size, first));
        assertFalse(assembly.add(0, 2, size, first));
        assertThrows(IllegalStateException.class, assembly::assemble);
        first[0] = 1;
        assertThrows(IllegalArgumentException.class, () -> assembly.add(0, 2, size, first));
        assertTrue(assembly.add(1, 2, size, new byte[]{1, 2, 3}));
        assertEquals(size, assembly.assemble().length);
        assertThrows(IllegalArgumentException.class, () -> new TrainFrameAssembly(Integer.MAX_VALUE, size, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrainFrameAssembly(100, MAX_BYTES + 1, 0));
    }

    @Test void thousandEightCarTrainsUseCompactTextFreeFrames() throws Exception {
        var poses = new ArrayList<Pose>();
        for (int id = 1; id <= 1000; id++) poses.add(new Pose(id, 1, false, false, id * 100.125, 64, -1000, new float[48]));
        byte[] bytes = TrainFrameCodec.frame(new Frame(1, 1000, 250, poses));
        assertEquals(220_899, bytes.length);
        assertEquals(8000, TrainFrameCodec.frame(bytes).trains().stream().mapToInt(Pose::cars).sum());
    }
}

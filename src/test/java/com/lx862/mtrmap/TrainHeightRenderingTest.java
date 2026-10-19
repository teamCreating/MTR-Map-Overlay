package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.*;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrainHeightRenderingTest {
    @Test
    void samplerRetainsNativeXYZOnSlopesAndBelowZero() {
        final Rail rail = Rail.newRail(new Position(0, -40, 0), Angle.E, new Position(100, 80, 0), Angle.W,
                Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
        assertNotNull(rail);
        final List<double[]> samples = TrackSampler.sample(rail);
        assertNotNull(samples);
        for (int i = 0; i < samples.size(); i++) {
            final var nativePoint = rail.railMath.getPosition(rail.railMath.getLength() * i / (samples.size() - 1), false);
            assertEquals(nativePoint.x(), samples.get(i)[0], 1.0E-8);
            assertEquals(nativePoint.z(), samples.get(i)[1], 1.0E-8);
            assertEquals(nativePoint.y(), samples.get(i)[2], 1.0E-8);
        }
        assertNotEquals(samples.getFirst()[2], samples.getLast()[2]);
    }

    @Test
    void upperCarWinsRegardlessOfVehicleInputOrder() {
        final MapCar lower = car(1, 60, 60);
        final MapCar upper = car(2, 80, 80);
        assertEquals(upper, scene(List.of(), List.of(upper, lower)).pick(0, 0));
        assertEquals(upper, scene(List.of(), List.of(lower, upper)).pick(0, 0));
    }

    @Test
    void upperRailCoversAndBlocksHoverOfAnUnderpassingCar() {
        final MapCar lower = car(1, 60, 60);
        final MapTrack bridge = new MapTrack("bridge", List.of(new double[] {-20, 0, 80}, new double[] {20, 0, 80}));
        final HeightMapScene scene = scene(List.of(bridge), List.of(lower));
        assertNull(scene.pick(0, 0));
        assertEquals(lower, scene.pick(0, 5));
        final var atCrossing = scene.quads().stream().filter(q -> q.contains(0, 0)).toList();
        assertNull(atCrossing.getLast().car());
        assertEquals(80, atCrossing.getLast().height());
        assertEquals(255, atCrossing.getLast().alpha());
    }

    @Test
    void upperCarRemainsVisibleOverLowerRail() {
        final MapTrack rail = new MapTrack("lower", List.of(new double[] {-20, 0, 40}, new double[] {20, 0, 40}));
        final MapCar upper = car(1, 60, 60);
        assertEquals(upper, scene(List.of(rail), List.of(upper)).pick(0, 0));
    }

    @Test
    void slopeCrossingUsesLocalHeightInsteadOfAWholeTrainOrRailAverage() {
        final MapCar inclined = car(1, 40, 80);
        final MapTrack crossing = new MapTrack("bridge", List.of(new double[] {-20, -7, 55}, new double[] {20, -7, 55}));
        // Rear half is high: the car is above this bridge even though its front is lower.
        assertEquals(inclined, scene(List.of(crossing), List.of(inclined)).pick(0, -7));
        final MapTrack crossing2 = new MapTrack("bridge", List.of(new double[] {-20, 7, 55}, new double[] {20, 7, 55}));
        assertNull(scene(List.of(crossing2), List.of(inclined)).pick(0, 7));
    }

    @Test
    void coincidentXZEndpointsAtDifferentHeightsAreNotConnected() {
        final MapTrack lower = new MapTrack("a", List.of(new double[] {0, 0, 40}, new double[] {10, 0, 40}));
        final MapTrack upper = new MapTrack("b", List.of(new double[] {10, 0, 80}, new double[] {20, 0, 80}));
        assertEquals(0, TrackPatternLayout.build(List.of(lower, upper)).get("b").distance());
    }

    @Test
    void whiteBlocksAndRouteBackingShareLocalHeightOnSlopes() {
        final MapTrack track = new MapTrack("slope", List.of(new double[] {0, 0, -20}, new double[] {64, 0, 44}));
        final var high = List.of(new TrackRoutePalette.Entry("h", "High speed", 0x008844, RouteLineStyle.HIGH_SPEED));
        final HeightMapScene scene = HeightMapScene.build(List.of(track), Map.of(track.id, high),
                TrackPatternLayout.build(List.of(track)), List.of(), true, true, 3, -100, -100, 100, 100);
        final var atWhiteBlock = scene.quads().stream().filter(q -> q.contains(24, 0)).toList();
        assertFalse(atWhiteBlock.isEmpty());
        assertEquals(0xFFFFFF, atWhiteBlock.getLast().color());
        assertTrue(scene.quads().stream().allMatch(q -> Double.isFinite(q.a().y())));
    }

    @Test
    void unknownYDoesNotBecomeMeasuredZeroAndDegenerateCarsCannotCaptureTheWholeMap() {
        final MapTrack legacy = new MapTrack("legacy", List.of(new double[] {0, 0}, new double[] {10, 0}));
        assertTrue(Double.isNaN(legacy.height(0)));
        final var p = new MapVertex(0, 64, 0);
        final MapCar invalid = new MapCar(1, 0, 1, true, "model", "route", "dest", 0xFF0000, p, p, p, p);
        assertNull(scene(List.of(), List.of(invalid)).pick(50, 50));
        assertTrue(scene(List.of(), List.of(invalid)).quads().isEmpty());
    }

    private static MapCar car(long id, double frontY, double backY) {
        return new MapCar(id, 0, 1, true, "model", "route", "destination", 0xFF0000,
                new MapVertex(1.5, frontY, 10), new MapVertex(1.5, backY, -10),
                new MapVertex(-1.5, backY, -10), new MapVertex(-1.5, frontY, 10));
    }

    private static HeightMapScene scene(List<MapTrack> tracks, List<MapCar> cars) {
        return HeightMapScene.build(tracks, Map.of(), TrackPatternLayout.build(tracks), cars,
                true, true, 3, -100, -100, 100, 100);
    }
}

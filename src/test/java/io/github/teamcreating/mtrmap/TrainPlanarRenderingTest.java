package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.mapdata.*;
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

class TrainPlanarRenderingTest {
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
    void lastPaintedCarWinsRegardlessOfHeight() {
        final MapCar lower = car(1, 60, 60);
        final MapCar upper = car(2, 80, 80);
        assertEquals(lower, scene(List.of(), List.of(upper, lower)).pick(0, 0));
        assertEquals(upper, scene(List.of(), List.of(lower, upper)).pick(0, 0));
    }

    @Test
    void higherRailDoesNotCoverOrBlockHoverOfACar() {
        final MapCar lower = car(1, 60, 60);
        final MapTrack bridge = new MapTrack("bridge", List.of(new double[] {-20, 0, 80}, new double[] {20, 0, 80}));
        final PlanarMapScene scene = scene(List.of(bridge), List.of(lower));
        assertEquals(lower, scene.pick(0, 0));
        assertEquals(lower, scene.pick(0, 5));
        final var atCrossing = scene.quads().stream().filter(q -> q.contains(0, 0)).toList();
        assertEquals(lower, atCrossing.getLast().car());
        assertEquals(255, atCrossing.getLast().alpha());
    }

    @Test
    void upperCarRemainsVisibleOverLowerRail() {
        final MapTrack rail = new MapTrack("lower", List.of(new double[] {-20, 0, 40}, new double[] {20, 0, 40}));
        final MapCar upper = car(1, 60, 60);
        assertEquals(upper, scene(List.of(rail), List.of(upper)).pick(0, 0));
    }

    @Test
    void slopesDoNotSplitCarsOrHideThemAtEitherCrossing() {
        final MapCar inclined = car(1, 40, 80);
        final MapTrack crossing = new MapTrack("bridge", List.of(new double[] {-20, -7, 55}, new double[] {20, -7, 55}));
        assertEquals(inclined, scene(List.of(crossing), List.of(inclined)).pick(0, -7));
        final MapTrack crossing2 = new MapTrack("bridge", List.of(new double[] {-20, 7, 55}, new double[] {20, 7, 55}));
        final PlanarMapScene result = scene(List.of(crossing2), List.of(inclined));
        assertEquals(inclined, result.pick(0, 7));
        assertEquals(scene(List.of(), List.of(car(1, 60, 60))).carQuadCount(), result.carQuadCount());
    }

    @Test
    void coincidentXZEndpointsAtDifferentHeightsAreNotConnected() {
        final MapTrack lower = new MapTrack("a", List.of(new double[] {0, 0, 40}, new double[] {10, 0, 40}));
        final MapTrack upper = new MapTrack("b", List.of(new double[] {10, 0, 80}, new double[] {20, 0, 80}));
        assertEquals(0, TrackPatternLayout.build(List.of(lower, upper)).get("b").distance());
    }

    @Test
    void routeBlocksAndGeometryAreIndependentOfHeight() {
        final MapTrack track = new MapTrack("slope", List.of(new double[] {0, 0, -20}, new double[] {64, 0, 44}));
        final var high = List.of(new TrackRoutePalette.Entry("h", "High speed", 0x008844, RouteLineStyle.HIGH_SPEED));
        final PlanarMapScene scene = PlanarMapScene.build(List.of(track), Map.of(track.id, high),
                TrackPatternLayout.build(List.of(track)), List.of(), true, true, 3, -100, -100, 100, 100);
        final var atWhiteBlock = scene.quads().stream().filter(q -> q.contains(24, 0)).toList();
        assertFalse(atWhiteBlock.isEmpty());
        assertEquals(0xFFFFFF, atWhiteBlock.getLast().color());
        final MapTrack flat = new MapTrack("slope", List.of(new double[] {0, 0, 64}, new double[] {64, 0, 64}));
        final PlanarMapScene flatScene = PlanarMapScene.build(List.of(flat), Map.of(flat.id, high),
                TrackPatternLayout.build(List.of(flat)), List.of(), true, true, 3, -100, -100, 100, 100);
        assertEquals(flatScene.quads(), scene.quads());
    }

    @Test
    void allBackingsPrecedeRoutesEvenIfAnUncolouredRailCrossesLater() {
        final MapTrack route = new MapTrack("route", List.of(new double[] {-20, 0, 10}, new double[] {20, 0, 10}));
        final MapTrack bridge = new MapTrack("bridge", List.of(new double[] {0, -20, 200}, new double[] {0, 20, 200}));
        final var band = List.of(new TrackRoutePalette.Entry("r", "route", 0xFF0000, RouteLineStyle.NORMAL));
        final PlanarMapScene result = PlanarMapScene.build(List.of(route, bridge), Map.of(route.id, band),
                Map.of(), List.of(), true, true, 3, -100, -100, 100, 100);
        assertEquals(0xFF0000, result.quads().stream().filter(q -> q.contains(0, 0)).toList().getLast().color());
    }

    @Test
    void zoomedOutCarMarkerCanBePickedBeyondItsPhysicalFootprint() {
        final MapCar car = car(1, 10, 200);
        final PlanarMapScene result = PlanarMapScene.build(List.of(), Map.of(), Map.of(), List.of(car),
                false, false, 0.1, -100, -100, 100, 100);
        assertEquals(0xFFFFFF, result.quads().getFirst().color());
        assertTrue(result.quads().stream().anyMatch(q -> q.color() == car.color()));
        assertTrue(result.quads().stream().anyMatch(q -> q.color() == 0xF4EEDC));
        assertEquals(car, result.pick(20, 0));
        assertNull(result.pick(40, 0));
    }

    @Test
    void narrowCarsHaveSevenPixelSilhouetteAndContrastingRoofWithoutChangingXYZ() {
        final MapCar car = car(1, -40, 200);
        final PlanarMapScene result = PlanarMapScene.withCars(List.of(), List.of(car), .5, -100, -100, 100, 100);
        assertEquals(car, result.pick(6.9, 0));
        assertNull(result.pick(7.1, 0));
        assertEquals(0xF4EEDC, result.quads().stream().filter(q -> q.contains(0, 0)).toList().getLast().color());
        assertEquals(1.5, car.frontRight().x());
        assertEquals(-40, car.frontRight().y());
        assertEquals(200, car.backRight().y());
    }

    @Test
    void reversingASingleCarMovesTheHeadArrowToTheOtherEnd() {
        final MapCar forward = car(1, 60, 60);
        final MapCar reverse = new MapCar(forward.vehicleId(), 0, 1, true, forward.modelId(),
                forward.routeName(), forward.destination(), forward.color(), forward.frontRight(),
                forward.backRight(), forward.backLeft(), forward.frontLeft(), true);
        final var first = scene(List.of(), List.of(forward)).quads().getLast();
        final var second = scene(List.of(), List.of(reverse)).quads().getLast();
        assertEquals(0xFFD166, first.color());
        assertTrue(first.z3() > 0);
        assertTrue(second.z3() < 0);
    }

    @Test
    void zoomedOutMarkerRetainsHeadingAndSuppressesTrailingCars() {
        final MapCar first = car(1, 60, 60);
        final MapCar trailing = new MapCar(1, 1, 2, false, "model", "route", "dest", 0xFF0000,
                first.frontRight(), first.backRight(), first.backLeft(), first.frontLeft());
        final var result = PlanarMapScene.withCars(List.of(), List.of(first, trailing), .1, -100, -100, 100, 100);
        assertEquals(first, result.pick(0, 55));
        assertNull(result.pick(55, 0));
        assertTrue(result.quads().stream().allMatch(q -> q.car() == first));
    }

    @Test
    void exaggeratedSilhouetteIsNotCulledAtViewportEdgeAndAdjacentCarsKeepAGap() {
        final MapCar first = car(1, 60, 60);
        final var edge = PlanarMapScene.withCars(List.of(), List.of(first), .5, 5, -5, 20, 5);
        assertEquals(first, edge.pick(6, 0));
        final var result = scene(List.of(), List.of(first));
        assertNull(result.pick(0, 9.9));
        assertEquals(first, result.pick(0, 9.7));
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

    @Test
    void smallSymbolsOmitUnreadableDetailsAndCarLengthDoesNotShrinkWhenZoomingIn() {
        final MapCar car = car(1, 60, 60);
        double previousLength = 0;
        for (double scale : new double[]{.1, .5, .6699, .6701, 1, 1.2999, 1.3001, 3}) {
            var scene = PlanarMapScene.withCars(List.of(), List.of(car), scale, -100, -100, 100, 100);
            double min = scene.quads().stream().mapToDouble(q -> Math.min(Math.min(q.z1(), q.z2()), Math.min(q.z3(), q.z4()))).min().orElseThrow();
            double max = scene.quads().stream().mapToDouble(q -> Math.max(Math.max(q.z1(), q.z2()), Math.max(q.z3(), q.z4()))).max().orElseThrow();
            assertTrue((max - min) * scale >= previousLength - 1.0E-7, "Zooming in shortened the symbol");
            previousLength = (max - min) * scale;
            assertEquals(scale < 1.3 ? 5 : 9, scene.carQuadCount());
            assertEquals(car, scene.pick(0, 0));
            assertEquals(car, scene.pick(0, (max - min) * .49));
            assertNull(scene.pick(0, (max - min) * .51));
        }
    }

    private static MapCar car(long id, double frontY, double backY) {
        return new MapCar(id, 0, 1, true, "model", "route", "destination", 0xFF0000,
                new MapVertex(1.5, frontY, 10), new MapVertex(1.5, backY, -10),
                new MapVertex(-1.5, backY, -10), new MapVertex(-1.5, frontY, 10));
    }

    private static PlanarMapScene scene(List<MapTrack> tracks, List<MapCar> cars) {
        return PlanarMapScene.build(tracks, Map.of(), TrackPatternLayout.build(tracks), cars,
                true, true, 3, -100, -100, 100, 100);
    }
}

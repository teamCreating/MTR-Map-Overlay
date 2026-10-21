package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanarMapCacheTest {
    private final List<MapTrack> rails = List.of(new MapTrack("r",
            List.of(new double[] {-500, 0, 10}, new double[] {500, 0, 90})));
    private final Map<String, List<TrackRoutePalette.Entry>> palette = Map.of("r",
            List.of(new TrackRoutePalette.Entry("route", "Route", 0x008844, RouteLineStyle.HIGH_SPEED)));
    private final Object source = new Object();

    @Test
    void steadyFramesAndSmallPansReuseTheSameScene() {
        final PlanarMapCache cache = new PlanarMapCache();
        final List<MapCar> cars = List.of(car(0));
        final PlanarMapScene first = get(cache, source, cars, true, true, 3, -100, 100);
        for (int frame = 0; frame < 120; frame++) {
            assertSame(first, get(cache, source, cars, true, true, 3, -90, 110));
        }
        assertEquals(1, cache.staticBuilds());
        assertEquals(1, cache.carBuilds());
    }

    @Test
    void newCarPosesReuseStaticQuadsButUpdateDrawingAndHover() {
        final PlanarMapCache cache = new PlanarMapCache();
        final MapCar old = car(0), moved = car(30);
        final PlanarMapScene first = get(cache, source, List.of(old), true, true, 3, -100, 100);
        final PlanarMapScene second = get(cache, source, List.of(moved), true, true, 3, -100, 100);
        assertSame(first.staticQuads(), second.staticQuads());
        assertNull(second.pick(0, 0));
        assertEquals(moved, second.pick(30, 0));
        assertEquals(old, first.pick(0, 0));
        assertEquals(1, cache.staticBuilds());
        assertEquals(2, cache.carBuilds());
    }

    @Test
    void sourceIdentityScaleSettingsAndGuardBoundaryInvalidate() {
        final PlanarMapCache cache = new PlanarMapCache();
        get(cache, source, List.of(), true, true, 3, -100, 100);
        final Object changed = new Object();
        get(cache, changed, List.of(), true, true, 3, -100, 100);
        get(cache, changed, List.of(), true, true, 6, -100, 100);
        final PlanarMapScene onlyTracks = get(cache, changed, List.of(), true, false, 6, -100, 100);
        assertTrue(onlyTracks.quads().stream().allMatch(q -> q.color() == RailRenderStyle.TRACK_COLOR));
        final PlanarMapScene hidden = get(cache, changed, List.of(), false, false, 6, -100, 100);
        assertTrue(hidden.quads().isEmpty());
        get(cache, changed, List.of(), false, false, 6, 200, 400);
        assertEquals(6, cache.staticBuilds());
    }

    @Test
    void paddedGeometryKeepsHighSpeedPhaseAfterPanning() {
        final PlanarMapCache cache = new PlanarMapCache();
        get(cache, source, List.of(), true, true, 3, -100, 100);
        final PlanarMapScene cached = get(cache, source, List.of(), true, true, 3, -80, 120);
        final PlanarMapScene direct = PlanarMapScene.build(rails, palette, Map.of(), List.of(),
                true, true, 3, -80, -100, 120, 100);
        for (double x : new double[] {-79, -48, -16, 0, 17, 49, 115, 119}) {
            assertEquals(topColor(direct, x), topColor(cached, x));
        }
        assertEquals(1, cache.staticBuilds());
    }

    @Test
    void overBudgetStaticGeometryRendersWithoutBeingRetained() {
        final PlanarMapCache cache = new PlanarMapCache(1);
        final MapCar car = car(0);
        assertEquals(car, get(cache, source, List.of(car), true, true, 3, -100, 100).pick(0, 0));
        assertEquals(0, cache.retainedQuads());
        assertFalse(get(cache, source, List.of(), true, true, 3, -100, 100).quads().isEmpty());
        assertEquals(2, cache.staticBuilds());
    }

    @Test
    void dynamicGeometryAlsoCountsAgainstRetentionBudget() {
        final PlanarMapCache cache = new PlanarMapCache(3);
        final PlanarMapScene scene = get(cache, source, List.of(car(0)), false, false, 3, -100, 100);
        assertEquals(4, scene.quads().size());
        assertNotNull(scene.pick(0, 0));
        assertEquals(0, cache.retainedQuads());
    }

    @Test
    void cleanupDropsGeometryAndSourcesAndTheCombinedViewIsReadOnly() {
        final PlanarMapCache cache = new PlanarMapCache();
        final PlanarMapScene first = get(cache, source, List.of(car(0)), true, true, 3, -100, 100);
        assertThrows(UnsupportedOperationException.class, () -> first.quads().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.staticQuads().clear());
        cache.clear();
        assertEquals(0, cache.retainedQuads());
        final PlanarMapScene next = get(cache, source, List.of(), true, true, 3, -100, 100);
        assertNotSame(first.staticQuads(), next.staticQuads());
        assertEquals(2, cache.staticBuilds());
    }

    @Test
    void invalidTransformCannotDisplayOrPickStaleCars() {
        final PlanarMapCache cache = new PlanarMapCache();
        final List<MapCar> cars = List.of(car(0));
        get(cache, source, cars, true, true, 3, -100, 100);
        final PlanarMapScene invalid = get(cache, source, cars, true, true, Double.NaN, -100, 100);
        assertTrue(invalid.quads().isEmpty());
        assertNull(invalid.pick(0, 0));
        assertEquals(1, cache.staticBuilds());
    }

    @Test
    void connectionCleanupClearsBothHostsWithoutOptionalMapClasses() {
        ClientPlanarScenes.clear();
        get(ClientPlanarScenes.XAERO, source, List.of(car(0)), true, true, 3, -100, 100);
        get(ClientPlanarScenes.JOURNEYMAP, source, List.of(car(0)), true, true, 3, -100, 100);
        assertTrue(ClientPlanarScenes.XAERO.retainedQuads() > 0);
        assertTrue(ClientPlanarScenes.JOURNEYMAP.retainedQuads() > 0);
        ClientPlanarScenes.clear();
        assertEquals(0, ClientPlanarScenes.XAERO.retainedQuads());
        assertEquals(0, ClientPlanarScenes.JOURNEYMAP.retainedQuads());
    }

    private PlanarMapScene get(PlanarMapCache cache, Object revision, List<MapCar> cars,
            boolean tracks, boolean routes, double scale, double minX, double maxX) {
        return cache.get(revision, rails, palette, Map.of(), cars, tracks, routes, scale,
                minX, -100, maxX, 100);
    }

    private static int topColor(PlanarMapScene scene, double x) {
        int result = -1;
        for (var q : scene.quads()) if (q.contains(x, 0)) result = q.color();
        return result;
    }

    private static MapCar car(double x) {
        return new MapCar(1, 0, 1, true, "model", "route", "destination", 0xFF0000,
                new MapVertex(x + 1.5, 64, 10), new MapVertex(x + 1.5, 64, -10),
                new MapVertex(x - 1.5, 64, -10), new MapVertex(x - 1.5, 64, 10));
    }
}

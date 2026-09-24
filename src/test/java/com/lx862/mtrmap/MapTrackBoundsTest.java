package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.MapTrack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTrackBoundsTest {

    @Test
    void crossingRailIsVisibleEvenWhenItsEndpointsAreOffscreen() {
        final MapTrack track = new MapTrack("rail", List.of(new double[]{-100, 5}, new double[]{100, 5}));

        assertTrue(track.intersects(0, 0, 10, 10));
        assertFalse(track.intersects(0, 20, 10, 30));
    }

    @Test
    void unusableGeometryNeverMatchesAViewport() {
        final MapTrack track = new MapTrack("invalid",
                List.of(new double[]{0, 0}, new double[]{Double.NaN, 0}, new double[]{10, 10}));

        assertFalse(track.intersects(-10, -10, 10, 10));
    }
}

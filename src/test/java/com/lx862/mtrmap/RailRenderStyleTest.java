package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.RailRenderStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailRenderStyleTest {

    @Test
    void journeyMapAndXaeroHaveTheSamePixelWidth() {
        for (double pixelsPerBlock : new double[] {0.25, 0.5, 1, 2, 5}) {
            final float worldHalfWidth = RailRenderStyle.worldHalfWidth(
                    RailRenderStyle.TRACK_HALF_WIDTH_PX, pixelsPerBlock);
            final float screenHalfWidth = RailRenderStyle.screenHalfWidth(
                    RailRenderStyle.TRACK_HALF_WIDTH_PX, pixelsPerBlock);
            assertEquals(worldHalfWidth * pixelsPerBlock, screenHalfWidth, 1.0E-5);
        }
    }

    @Test
    void sharedRoutesStayWithinTheSmallWidthMultiplier() {
        assertEquals(1.0f, RailRenderStyle.routeWidthMultiplier(1));
        assertEquals(1.0f, RailRenderStyle.routeWidthMultiplier(3));
        assertEquals(1.15f, RailRenderStyle.routeWidthMultiplier(4), 1.0E-5);
        assertTrue(RailRenderStyle.routeWidthMultiplier(20) <= 1.6f);
    }
}

package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.RailRenderStyle;
import com.lx862.mtrmap.mapdata.RouteRibbonGeometry;
import com.lx862.mtrmap.mapdata.RouteLineStyle;
import com.lx862.mtrmap.mapdata.TrackRoutePalette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailRenderStyleTest {

    @Test
    void journeyMapAndXaeroHaveTheSamePixelWidth() {
        for (double pixelsPerBlock : new double[] {0.25, 0.5, 1, 2, 5}) {
            final float worldHalfWidth = RailRenderStyle.worldHalfWidth(
                    RailRenderStyle.TRACK_HALF_WIDTH, pixelsPerBlock);
            final float screenHalfWidth = RailRenderStyle.screenHalfWidth(
                    RailRenderStyle.TRACK_HALF_WIDTH, pixelsPerBlock);
            assertEquals(worldHalfWidth * pixelsPerBlock, screenHalfWidth, 1.0E-5);
        }
    }

    @Test
    void sharedRoutesHaveLegibleLanesWithoutUnboundedBundleGrowth() {
        assertEquals(1.0f, RailRenderStyle.routeWidthMultiplier(1));
        assertEquals(3.0f, RailRenderStyle.routeWidthMultiplier(3));
        assertEquals(4.0f, RailRenderStyle.routeWidthMultiplier(4), 1.0E-5);
        assertTrue(RailRenderStyle.routeWidthMultiplier(20) <= 4.0f);
    }

    @Test void railsShrinkWithTheMapAndFarBundlesShareOneReadabilityFloor() {
        assertEquals(RailRenderStyle.screenHalfWidth(1.25f, 1) / 2,
                RailRenderStyle.screenHalfWidth(1.25f, .5), 1E-8);
        final var bands = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
                new TrackRoutePalette.Entry("" + i, "Route", 0x008844, RouteLineStyle.HIGH_SPEED)).toList();
        final double near = RouteRibbonGeometry.halfWidth(bands, 1);
        final double far = RouteRibbonGeometry.halfWidth(bands, .1) * .1;
        assertEquals(near * .1, far, 1E-7);
        final double veryFar = RouteRibbonGeometry.halfWidth(bands, .01) * .01;
        assertEquals(.3, veryFar, 1E-7);
        assertEquals(veryFar, RouteRibbonGeometry.halfWidth(bands.subList(0, 1), .01) * .01, 1E-7);
        assertTrue(RailRenderStyle.backingWidth(RouteRibbonGeometry.halfWidth(bands, .01), .01, true) * .02 < 1,
                "Entire far bundle backing must stay below one GUI pixel, not one floor per route");
    }
}

package com.lx862.mtrmap.mapdata;

/** Pixel-space rail style shared by the Xaero and JourneyMap fullscreen maps. */
public final class RailRenderStyle {

    public static final float TRACK_HALF_WIDTH_PX = 1.25f;
    public static final int TRACK_COLOR = 0x404040;
    public static final int TRACK_ALPHA = 175;
    /** A narrow rail shoulder remains visible when a route occupies the same track. */
    public static final float TRACK_SHOULDER_PX = 0.4f;

    private RailRenderStyle() {
    }

    public static float routeWidthMultiplier(int routeCount) {
        // Give the common two-to-four-route bundle legible individual lanes.
        // Beyond four routes, subdivide the same bounded ribbon instead of widening forever.
        return Math.max(1, Math.min(4, routeCount));
    }

    public static float worldHalfWidth(float pixels, double screenPixelsPerBlock) {
        return (float) Math.max(pixels / screenPixelsPerBlock, 0.33);
    }

    public static float screenHalfWidth(float pixels, double screenPixelsPerBlock) {
        return (float) Math.max(pixels, 0.33 * screenPixelsPerBlock);
    }
}

package io.github.teamcreating.mtrmap.mapdata;

/** World-space rail style shared by both maps, with a small whole-ribbon readability floor. */
public final class RailRenderStyle {

    public static final float TRACK_HALF_WIDTH = 1.25f;
    public static final int TRACK_COLOR = 0x404040;
    public static final int TRACK_ALPHA = 255;
    /** A narrow rail shoulder remains visible when a route occupies the same track. */
    public static final float TRACK_SHOULDER = 0.4f;

    private RailRenderStyle() {
    }

    public static float routeWidthMultiplier(int routeCount) {
        // Give the common two-to-four-route bundle legible individual lanes.
        // Beyond four routes, subdivide the same bounded ribbon instead of widening forever.
        return Math.max(1, Math.min(4, routeCount));
    }

    public static float worldHalfWidth(float blocks, double screenPixelsPerBlock) {
        return (float) Math.max(blocks, 0.3 / screenPixelsPerBlock);
    }

    public static float screenHalfWidth(float blocks, double screenPixelsPerBlock) {
        return (float) Math.max(blocks * screenPixelsPerBlock, 0.3);
    }

    public static double shoulder(double scale) { return Math.max(TRACK_SHOULDER, .1 / scale); }

    public static double backingWidth(double routeHalfWidth, double scale, boolean showTracks) {
        return Math.max(showTracks ? worldHalfWidth(TRACK_HALF_WIDTH, scale) : 0,
                routeHalfWidth > 0 ? routeHalfWidth + shoulder(scale) : 0);
    }
}

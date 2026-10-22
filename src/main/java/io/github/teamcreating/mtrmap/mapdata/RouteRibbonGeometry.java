package io.github.teamcreating.mtrmap.mapdata;

import java.util.List;

/** Shared world-space route quads for both maps; picking and rail geometry stay unchanged. */
public final class RouteRibbonGeometry {
    private RouteRibbonGeometry() {
    }

    @FunctionalInterface
    public interface QuadSink {
        void quad(double x1, double z1, double x2, double z2, double x3, double z3,
                double x4, double z4, int color, int alpha);
        default void feather(double x1, double z1, double x2, double z2, double x3, double z3,
                double x4, double z4, int color, int alpha) {}
    }

    public static double halfWidth(List<TrackRoutePalette.Entry> bands, double scale) {
        if (bands.isEmpty()) {
            return 0;
        }
        double weight = 0;
        for (TrackRoutePalette.Entry band : bands) {
            weight += band.style().width;
        }
        // Apply the readability floor once to the entire bundle, never to each lane.
        return RailRenderStyle.worldHalfWidth((float) (RailRenderStyle.TRACK_HALF_WIDTH
                * RailRenderStyle.routeWidthMultiplier(bands.size()) * weight / bands.size()), scale);
    }

    public static void draw(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale,
            double minX, double minZ, double maxX, double maxZ, QuadSink sink) {
        drawLayered(track, bands, placement, scale, minX, minZ, maxX, maxZ, sink, sink);
    }

    public static void drawLayered(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale,
            double minX, double minZ, double maxX, double maxZ, QuadSink base, QuadSink details) {
        drawLayered(track, bands, placement, null, scale, minX, minZ, maxX, maxZ, base, details);
    }

    public static void drawLayered(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, RouteRibbonProfile profile, double scale,
            double minX, double minZ, double maxX, double maxZ, QuadSink base, QuadSink details) {
        drawInternal(track, bands, placement, profile, scale, minX, minZ, maxX, maxZ, base, details, Double.NaN);
    }

    public static void drawStrip(MapTrack track, double halfWidth, double scale,
            double minX, double minZ, double maxX, double maxZ, QuadSink sink) {
        drawStrip(track, TrackPatternLayout.Placement.DEFAULT, halfWidth, scale, minX, minZ, maxX, maxZ, sink);
    }

    public static void drawStrip(MapTrack track, TrackPatternLayout.Placement placement, double halfWidth, double scale,
            double minX, double minZ, double maxX, double maxZ, QuadSink sink) {
        drawStrip(track, placement, null, halfWidth, scale, minX, minZ, maxX, maxZ, sink);
    }

    public static void drawStrip(MapTrack track, TrackPatternLayout.Placement placement, RouteRibbonProfile profile,
            double halfWidth, double scale, double minX, double minZ, double maxX, double maxZ, QuadSink sink) {
        drawInternal(track, List.of(new TrackRoutePalette.Entry("", "", RailRenderStyle.TRACK_COLOR,
                RouteLineStyle.NORMAL)), placement, profile, scale,
                minX, minZ, maxX, maxZ, sink, sink, halfWidth);
    }

    private static void drawInternal(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, RouteRibbonProfile profile, double scale,
            double minX, double minZ, double maxX, double maxZ,
            QuadSink base, QuadSink details, double overrideWidth) {
        if (bands.isEmpty() || !Double.isFinite(scale) || scale <= 0) {
            return;
        }
        final double halfWidth = Double.isFinite(overrideWidth) ? overrideWidth : halfWidth(bands, scale);
        final double margin = (profile == null ? halfWidth
                : Math.max(halfWidth, Math.max(profile.start().width(), profile.end().width()) + 1 / scale)) * 2;
        if (!track.intersects(minX - margin, minZ - margin, maxX + margin, maxZ + margin)) {
            return;
        }
        double weight = 0;
        for (TrackRoutePalette.Entry band : bands) {
            weight += band.style().width;
        }
        final double unit = halfWidth * 2 / weight;
        final double[] clipped = new double[2];
        final double sign = placement.reversed() ? -1 : 1;
        for (int i = 0; i + 1 < track.points.size(); i++) {
            final int a = placement.reversed() ? track.points.size() - 1 - i : i;
            final int b = placement.reversed() ? a - 1 : a + 1;
            final double[] start = track.points.get(a);
            final double[] end = track.points.get(b);
            final double length = Math.abs(track.distances[b] - track.distances[a]);
            if (length < 1.0E-8 || !clip(start, end, minX - margin, minZ - margin,
                    maxX + margin, maxZ + margin, clipped)) {
                continue;
            }
            final double distance = placement.distance() + (placement.reversed()
                    ? track.length() - track.distances[a] : track.distances[a]);
            double left = -halfWidth;
            for (int lane = 0; lane < bands.size(); lane++) {
                final TrackRoutePalette.Entry band = bands.get(lane);
                final double right = lane + 1 == bands.size() ? halfWidth : left + unit * band.style().width;
                section(track, placement, profile, band.id(), Double.isFinite(overrideWidth), 0,
                        a, b, sign, left, right, clipped[0], clipped[1],
                        band.color(), RailRenderStyle.TRACK_ALPHA, base, 0, scale);
                if (Double.isFinite(overrideWidth)) {
                    for (int side : new int[] {-1, 1}) section(track, placement, profile, band.id(), true, 0,
                            a, b, sign, left, right, clipped[0], clipped[1], band.color(), 255, base, side, scale);
                }
                final double lanePixels = (right - left) * scale;
                if (band.style() == RouteLineStyle.HIGH_SPEED && lanePixels >= 1.8
                        && placement.blockLength() * scale >= 3) {
                    final double border = Math.max(0.65 / scale, (right - left) * 0.2);
                    final double block = placement.blockLength();
                    final double period = block * 2;
                    // Clip before enumerating blocks: even a rail spanning the whole world
                    // must do work proportional to the visible region, not its total length.
                    final double visibleStart = distance + clipped[0] * length;
                    final double visibleEnd = distance + clipped[1] * length;
                    for (double whiteStart = Math.floor(visibleStart / period) * period + block;
                            whiteStart < visibleEnd; whiteStart += period) {
                        final double from = Math.max(visibleStart, whiteStart);
                        final double to = Math.min(visibleEnd, whiteStart + block);
                        if (to > from) {
                            section(track, placement, profile, band.id(), false, border,
                                    a, b, sign, left, right,
                                    (from - distance) / length, (to - distance) / length,
                                    0xFFFFFF, 255, details, 0, scale);
                        }
                    }
                }
                left = right;
            }
        }
    }

    private static void section(MapTrack track, TrackPatternLayout.Placement placement,
            RouteRibbonProfile profile, String laneId, boolean backing, double inset,
            int a, int b, double sign, double left, double right,
            double from, double to, int color, int alpha, QuadSink sink, int featherSide, double scale) {
        // Explicit taper samples also cover long two-point rails; width changes
        // must not stretch over the entire rail or be represented by one sharp wedge.
        final double da = track.distances[a], db = track.distances[b];
        if (profile != null && profile.taper() > 0) {
            for (double d : profile.cuts()) {
                final double t = (d - da) / (db - da);
                if (t > from + 1.0E-9 && t < to - 1.0E-9) {
                    section(track, placement, profile, laneId, backing, inset, a, b, sign, left, right,
                            from, t, color, alpha, sink, featherSide, scale);
                    section(track, placement, profile, laneId, backing, inset, a, b, sign, left, right,
                            t, to, color, alpha, sink, featherSide, scale);
                    return;
                }
            }
        }
        final double d1 = da + (db - da) * from, d2 = da + (db - da) * to;
        final RouteRibbonProfile.Band edge1 = edges(profile, laneId, backing, left, right, d1, track.length());
        final RouteRibbonProfile.Band edge2 = edges(profile, laneId, backing, left, right, d2, track.length());
        final double[] start = track.points.get(a);
        final double[] end = track.points.get(b);
        final double x1 = start[0] + (end[0] - start[0]) * from;
        final double z1 = start[1] + (end[1] - start[1]) * from;
        final double x2 = start[0] + (end[0] - start[0]) * to;
        final double z2 = start[1] + (end[1] - start[1]) * to;
        final double nx1 = sign * (placement.normalX(track, a) + (placement.normalX(track, b) - placement.normalX(track, a)) * from);
        final double nz1 = sign * (placement.normalZ(track, a) + (placement.normalZ(track, b) - placement.normalZ(track, a)) * from);
        final double nx2 = sign * (placement.normalX(track, a) + (placement.normalX(track, b) - placement.normalX(track, a)) * to);
        final double nz2 = sign * (placement.normalZ(track, a) + (placement.normalZ(track, b) - placement.normalZ(track, a)) * to);
        if (featherSide != 0) {
            final double inner1 = featherSide > 0 ? edge1.right() : edge1.left();
            final double inner2 = featherSide > 0 ? edge2.right() : edge2.left();
            final double outer1 = inner1 + featherSide * Math.min(0.35 / scale, (edge1.right() - edge1.left()) * .15);
            final double outer2 = inner2 + featherSide * Math.min(0.35 / scale, (edge2.right() - edge2.left()) * .15);
            if (featherSide > 0) sink.feather(x1 + nx1 * outer1, z1 + nz1 * outer1,
                    x2 + nx2 * outer2, z2 + nz2 * outer2, x2 + nx2 * inner2, z2 + nz2 * inner2,
                    x1 + nx1 * inner1, z1 + nz1 * inner1, color, alpha);
            else sink.feather(x2 + nx2 * outer2, z2 + nz2 * outer2,
                    x1 + nx1 * outer1, z1 + nz1 * outer1, x1 + nx1 * inner1, z1 + nz1 * inner1,
                    x2 + nx2 * inner2, z2 + nz2 * inner2, color, alpha);
            return;
        }
        // A white high-speed block must collapse with its terminating lane. A fixed
        // inset would invert the quad and spill white outside a narrow taper.
        final double inset1 = Math.min(inset, Math.max(0, (edge1.right() - edge1.left()) / 2));
        final double inset2 = Math.min(inset, Math.max(0, (edge2.right() - edge2.left()) / 2));
        sink.quad(x1 + nx1 * (edge1.right() - inset1), z1 + nz1 * (edge1.right() - inset1),
                x2 + nx2 * (edge2.right() - inset2), z2 + nz2 * (edge2.right() - inset2),
                x2 + nx2 * (edge2.left() + inset2), z2 + nz2 * (edge2.left() + inset2),
                x1 + nx1 * (edge1.left() + inset1), z1 + nz1 * (edge1.left() + inset1), color, alpha);
    }

    private static RouteRibbonProfile.Band edges(RouteRibbonProfile profile, String laneId, boolean backing,
            double left, double right, double distance, double length) {
        if (profile == null) return new RouteRibbonProfile.Band(left, right);
        if (!backing) return profile.at(laneId, left, right, distance, length);
        final double width = profile.widthAt(right, distance, length);
        return new RouteRibbonProfile.Band(-width, width);
    }

    /** Liang-Barsky clipping preserves the original rail distance and hence stripe phase. */
    private static boolean clip(double[] start, double[] end, double minX, double minZ,
            double maxX, double maxZ, double[] result) {
        result[0] = 0;
        result[1] = 1;
        final double dx = end[0] - start[0];
        final double dz = end[1] - start[1];
        return bound(-dx, start[0] - minX, result) && bound(dx, maxX - start[0], result)
                && bound(-dz, start[1] - minZ, result) && bound(dz, maxZ - start[1], result);
    }

    private static boolean bound(double p, double q, double[] result) {
        if (Math.abs(p) < 1.0E-12) {
            return q >= 0;
        }
        final double t = q / p;
        if (p < 0) {
            result[0] = Math.max(result[0], t);
        } else {
            result[1] = Math.min(result[1], t);
        }
        return result[0] <= result[1];
    }
}

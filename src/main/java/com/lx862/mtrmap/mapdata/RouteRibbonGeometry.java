package com.lx862.mtrmap.mapdata;

import java.util.List;

/** Shared world-space route quads for both maps; picking and rail geometry stay unchanged. */
public final class RouteRibbonGeometry {
    private RouteRibbonGeometry() {
    }

    @FunctionalInterface
    public interface QuadSink {
        void quad(double x1, double z1, double x2, double z2, double x3, double z3,
                double x4, double z4, int color, int alpha);
    }

    @FunctionalInterface
    public interface SpatialQuadSink {
        void quad(MapVertex a, MapVertex b, MapVertex c, MapVertex d,
                int color, int alpha, double sortHeight, int detail);
    }

    public static double halfWidth(List<TrackRoutePalette.Entry> bands, double scale) {
        if (bands.isEmpty()) {
            return 0;
        }
        double weight = 0;
        for (TrackRoutePalette.Entry band : bands) {
            weight += band.style().width;
        }
        return RailRenderStyle.worldHalfWidth(RailRenderStyle.TRACK_HALF_WIDTH_PX
                * RailRenderStyle.routeWidthMultiplier(bands.size()), scale) * weight / bands.size();
    }

    public static void draw(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale,
            double minX, double minZ, double maxX, double maxZ, QuadSink sink) {
        drawSpatial(track, bands, placement, scale, minX, minZ, maxX, maxZ,
                (a, b, c, d, color, alpha, height, detail) -> sink.quad(
                        a.x(), a.z(), b.x(), b.z(), c.x(), c.z(), d.x(), d.z(), color, alpha));
    }

    public static void drawSpatial(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale,
            double minX, double minZ, double maxX, double maxZ, SpatialQuadSink sink) {
        drawInternal(track, bands, placement, scale, minX, minZ, maxX, maxZ, sink, Double.NaN);
    }

    public static void drawStrip(MapTrack track, double halfWidth, double scale,
            double minX, double minZ, double maxX, double maxZ, SpatialQuadSink sink) {
        drawInternal(track, List.of(new TrackRoutePalette.Entry("", "", RailRenderStyle.TRACK_COLOR,
                RouteLineStyle.NORMAL)), TrackPatternLayout.Placement.DEFAULT, scale,
                minX, minZ, maxX, maxZ, sink, halfWidth);
    }

    private static void drawInternal(MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale,
            double minX, double minZ, double maxX, double maxZ, SpatialQuadSink sink, double overrideWidth) {
        if (bands.isEmpty() || !Double.isFinite(scale) || scale <= 0) {
            return;
        }
        final double halfWidth = Double.isFinite(overrideWidth) ? overrideWidth : halfWidth(bands, scale);
        final double margin = halfWidth * 2;
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
            final double heightA = track.height(a);
            final double heightB = track.height(b);
            final int pieces = Double.isFinite(heightA) && Double.isFinite(heightB)
                    ? Math.max(1, Math.min(256, (int) Math.ceil(Math.abs(heightB - heightA) / 0.5))) : 1;
            final double originalFrom = clipped[0];
            final double originalTo = clipped[1];
            for (int piece = 0; piece < pieces; piece++) {
                clipped[0] = Math.max(originalFrom, (double) piece / pieces);
                clipped[1] = Math.min(originalTo, (double) (piece + 1) / pieces);
                if (clipped[1] <= clipped[0]) continue;
                final double sortHeight = heightA + (heightB - heightA) * (clipped[0] + clipped[1]) / 2;
                double left = -halfWidth;
                for (int lane = 0; lane < bands.size(); lane++) {
                    final TrackRoutePalette.Entry band = bands.get(lane);
                    final double right = lane + 1 == bands.size() ? halfWidth : left + unit * band.style().width;
                    section(track, a, b, sign, left, right, clipped[0], clipped[1],
                            band.color(), RailRenderStyle.TRACK_ALPHA, sortHeight, 0, sink);
                    final double lanePixels = (right - left) * scale;
                    if (band.style() == RouteLineStyle.HIGH_SPEED && lanePixels >= 2.8
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
                                section(track, a, b, sign, left + border, right - border,
                                        (from - distance) / length, (to - distance) / length,
                                        0xFFFFFF, 255, sortHeight, 1, sink);
                            }
                        }
                    }
                    left = right;
                }
            }
        }
    }

    private static void section(MapTrack track, int a, int b, double sign, double left, double right,
            double from, double to, int color, int alpha, double sortHeight, int detail, SpatialQuadSink sink) {
        final double[] start = track.points.get(a);
        final double[] end = track.points.get(b);
        final double x1 = start[0] + (end[0] - start[0]) * from;
        final double z1 = start[1] + (end[1] - start[1]) * from;
        final double x2 = start[0] + (end[0] - start[0]) * to;
        final double z2 = start[1] + (end[1] - start[1]) * to;
        final double nx1 = sign * (track.normalX[a] + (track.normalX[b] - track.normalX[a]) * from);
        final double nz1 = sign * (track.normalZ[a] + (track.normalZ[b] - track.normalZ[a]) * from);
        final double nx2 = sign * (track.normalX[a] + (track.normalX[b] - track.normalX[a]) * to);
        final double nz2 = sign * (track.normalZ[a] + (track.normalZ[b] - track.normalZ[a]) * to);
        final double heightA = track.height(a);
        final double dy = track.height(b) - heightA;
        final double y1 = heightA + dy * from;
        final double y2 = heightA + dy * to;
        sink.quad(new MapVertex(x1 + nx1 * right, y1, z1 + nz1 * right),
                new MapVertex(x2 + nx2 * right, y2, z2 + nz2 * right),
                new MapVertex(x2 + nx2 * left, y2, z2 + nz2 * left),
                new MapVertex(x1 + nx1 * left, y1, z1 + nz1 * left), color, alpha, sortHeight, detail);
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

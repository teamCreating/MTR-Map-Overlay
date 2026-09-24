package com.lx862.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.List;

/** JourneyMap strip geometry around the physical rail sampled for Xaero. */
public final class TrackRibbonGeometry {

    private static final double SIMPLIFY_TOLERANCE = 0.35;

    private TrackRibbonGeometry() {
    }

    /**
     * JourneyMap triangulates PolygonOverlay outlines. A ribbon with multiple
     * bends can be concave or self-intersecting, including when split into
     * short multi-segment pieces. Match Xaero's per-segment normal instead:
     * every overlay is one convex four-corner strip. Small end caps overlap
     * adjacent strips and independently sampled rails after integer rounding.
     */
    public static List<List<double[]>> sections(List<double[]> sampled, double left, double right) {
        final List<double[]> simplified = simplify(sampled);
        if (simplified.size() < 2 || right <= left) {
            return List.of();
        }
        final List<List<double[]>> result = new ArrayList<>();
        for (int i = 0; i < simplified.size() - 1; i++) {
            final double[] start = simplified.get(i);
            final double[] end = simplified.get(i + 1);
            final double dx = end[0] - start[0];
            final double dz = end[1] - start[1];
            final double length = Math.hypot(dx, dz);
            if (length < 1.0E-4) {
                continue;
            }
            final double ux = dx / length;
            final double uz = dz / length;
            final double nx = -uz;
            final double nz = ux;
            final double cap = Math.min(0.75, length * 0.25);
            final double x1 = start[0] - ux * cap;
            final double z1 = start[1] - uz * cap;
            final double x2 = end[0] + ux * cap;
            final double z2 = end[1] + uz * cap;
            result.add(List.of(
                    new double[] {x1 + nx * right, z1 + nz * right},
                    new double[] {x2 + nx * right, z2 + nz * right},
                    new double[] {x2 + nx * left, z2 + nz * left},
                    new double[] {x1 + nx * left, z1 + nz * left}));
        }
        return result;
    }

    private static List<double[]> simplify(List<double[]> sampled) {
        final List<double[]> points = validPoints(sampled);
        if (points.size() < 3) {
            return points;
        }
        final boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[keep.length - 1] = true;
        simplifyRange(points, keep, 0, points.size() - 1);
        final List<double[]> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static List<double[]> validPoints(List<double[]> sampled) {
        final List<double[]> points = new ArrayList<>();
        if (sampled == null) {
            return points;
        }
        for (double[] point : sampled) {
            if (point == null || point.length < 2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
                continue;
            }
            if (points.isEmpty() || Math.hypot(point[0] - points.getLast()[0], point[1] - points.getLast()[1]) > 1.0E-4) {
                points.add(point);
            }
        }
        return points;
    }

    private static void simplifyRange(List<double[]> points, boolean[] keep, int start, int end) {
        if (end - start < 2) {
            return;
        }
        final double[] a = points.get(start);
        final double[] b = points.get(end);
        final double dx = b[0] - a[0];
        final double dz = b[1] - a[1];
        final double lengthSquared = dx * dx + dz * dz;
        double greatest = -1;
        int farthest = -1;
        for (int i = start + 1; i < end; i++) {
            final double[] p = points.get(i);
            final double t = lengthSquared < 1.0E-8 ? 0
                    : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / lengthSquared));
            final double x = a[0] + t * dx;
            final double z = a[1] + t * dz;
            final double distanceSquared = (p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z);
            if (distanceSquared > greatest) {
                greatest = distanceSquared;
                farthest = i;
            }
        }
        if (greatest > SIMPLIFY_TOLERANCE * SIMPLIFY_TOLERANCE) {
            keep[farthest] = true;
            simplifyRange(points, keep, start, farthest);
            simplifyRange(points, keep, farthest, end);
        }
    }
}

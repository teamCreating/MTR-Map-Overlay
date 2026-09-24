package com.lx862.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.List;

/** World-space ribbon around the same sampled physical rail used by Xaero. */
public final class TrackRibbonGeometry {

    private static final int MAX_SEGMENTS_PER_POLYGON = 6;
    private static final double SIMPLIFY_TOLERANCE = 0.35;

    private TrackRibbonGeometry() {
    }

    /**
     * Builds one continuous band, ordered along its right edge and back along
     * its left edge. Offsets are measured in world blocks from the rail center.
     */
    public static List<double[]> band(List<double[]> sampled, double left, double right) {
        if (sampled == null || sampled.size() < 2 || right <= left) {
            return List.of();
        }
        final List<double[]> points = validPoints(sampled);
        if (points.size() < 2) {
            return List.of();
        }

        // Adjacent MTR rails meet at their centerline endpoints. A short cap
        // makes independently rendered ribbons overlap instead of exposing a
        // one-pixel seam after JourneyMap rounds coordinates to whole blocks.
        final double cap = Math.min(1.0, (right - left) * 0.25);
        final double[] first = points.getFirst();
        final double[] second = points.get(1);
        final double firstLength = Math.hypot(second[0] - first[0], second[1] - first[1]);
        final double[] last = points.getLast();
        final double[] beforeLast = points.get(points.size() - 2);
        final double lastLength = Math.hypot(last[0] - beforeLast[0], last[1] - beforeLast[1]);
        points.set(0, new double[] {first[0] - (second[0] - first[0]) * cap / firstLength,
                first[1] - (second[1] - first[1]) * cap / firstLength});
        points.set(points.size() - 1, new double[] {last[0] + (last[0] - beforeLast[0]) * cap / lastLength,
                last[1] + (last[1] - beforeLast[1]) * cap / lastLength});

        final List<double[]> rightEdge = new ArrayList<>(points.size());
        final List<double[]> leftEdge = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            final double[] before = points.get(Math.max(0, i - 1));
            final double[] after = points.get(Math.min(points.size() - 1, i + 1));
            final double dx = after[0] - before[0];
            final double dz = after[1] - before[1];
            final double length = Math.hypot(dx, dz);
            if (length < 1.0E-4) {
                return List.of();
            }
            final double nx = -dz / length;
            final double nz = dx / length;
            rightEdge.add(new double[] {points.get(i)[0] + nx * right, points.get(i)[1] + nz * right});
            leftEdge.add(new double[] {points.get(i)[0] + nx * left, points.get(i)[1] + nz * left});
        }
        rightEdge.addAll(leftEdge.reversed());
        return rightEdge;
    }

    /**
     * JourneyMap triangulates each PolygonOverlay independently. Long, thin
     * outlines with many nearly collinear samples are unstable there, so use
     * short polygons whose adjoining edge vertices are exactly identical.
     */
    public static List<List<double[]>> sections(List<double[]> sampled, double left, double right) {
        final List<double[]> simplified = simplify(sampled);
        final List<double[]> outline = band(simplified, left, right);
        if (outline.isEmpty()) {
            return List.of();
        }
        final int count = outline.size() / 2;
        final List<List<double[]>> result = new ArrayList<>();
        for (int start = 0; start < count - 1;) {
            final int end = Math.min(count - 1, start + MAX_SEGMENTS_PER_POLYGON);
            final List<double[]> section = new ArrayList<>(2 * (end - start + 1));
            for (int i = start; i <= end; i++) {
                section.add(outline.get(i));
            }
            for (int i = end; i >= start; i--) {
                section.add(outline.get(2 * count - 1 - i));
            }
            result.add(section);
            if (end == count - 1) {
                break;
            }
            // An opaque, one-segment overlap hides rasterization seams at the
            // boundary between two independently triangulated overlays.
            start = end - 1;
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

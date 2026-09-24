package com.lx862.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.List;

/** World-space ribbon around the same sampled physical rail used by Xaero. */
public final class TrackRibbonGeometry {

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
        final List<double[]> points = new ArrayList<>();
        for (double[] point : sampled) {
            if (point == null || point.length < 2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
                continue;
            }
            if (points.isEmpty() || Math.hypot(point[0] - points.getLast()[0], point[1] - points.getLast()[1]) > 1.0E-4) {
                points.add(point);
            }
        }
        if (points.size() < 2) {
            return List.of();
        }

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
}

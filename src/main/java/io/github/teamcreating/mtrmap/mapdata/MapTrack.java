package io.github.teamcreating.mtrmap.mapdata;

import java.util.List;

/**
 * Render-ready representation of one stretch of physical rail for the map
 * track layer. Points are sampled along the rail's actual curve (including
 * arcs and slopes). Points store X/Z/Y so existing planar geometry keeps its indices.
 * The third component is the measured world Y; legacy two-component points have unknown height.
 */
public class MapTrack {

    /** Stable MTR rail hex id, shared by the track and route layers. */
    public final String id;
    public final List<double[]> points;
    public final double minX;
    public final double minZ;
    public final double maxX;
    public final double maxZ;
    private final boolean validGeometry;
    public final double[] distances;
    public final double[] normalX;
    public final double[] normalZ;
    public final NativeRailCurve curve;

    public MapTrack(String id, List<double[]> points) {
        this(id, points, null, null);
    }

    public MapTrack(String id, List<double[]> points, NativeRailCurve curve) {
        this(id, points, curve, null);
    }

    MapTrack(String id, List<double[]> points, NativeRailCurve curve, double[] nativeDistances) {
        this.id = id == null ? "" : id;
        this.points = points;
        this.curve = curve;
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean validGeometry = true;
        for (double[] point : points) {
            if (point == null || point.length < 2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])
                    || (point.length >= 3 && Double.isInfinite(point[2]))) {
                validGeometry = false;
                break;
            }
            minX = Math.min(minX, point[0]);
            minZ = Math.min(minZ, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxZ = Math.max(maxZ, point[1]);
        }
        this.minX = curve == null ? minX : Math.min(minX, curve.minX());
        this.minZ = curve == null ? minZ : Math.min(minZ, curve.minZ());
        this.maxX = curve == null ? maxX : Math.max(maxX, curve.maxX());
        this.maxZ = curve == null ? maxZ : Math.max(maxZ, curve.maxZ());
        this.validGeometry = validGeometry && points.size() >= 2;
        distances = new double[points.size()];
        normalX = new double[points.size()];
        normalZ = new double[points.size()];
        if (this.validGeometry) {
            if (curve != null) {
                for (int i = 0; i < points.size(); i++) {
                    distances[i] = nativeDistances == null ? curve.length() * i / (points.size() - 1) : nativeDistances[i];
                    final double[] normal = curve.normal(distances[i]);
                    normalX[i] = normal[0];
                    normalZ[i] = normal[1];
                }
                return;
            }
            for (int i = 1; i < points.size(); i++) {
                distances[i] = nativeDistances != null ? nativeDistances[i] : distances[i - 1] + Math.hypot(points.get(i)[0] - points.get(i - 1)[0],
                        points.get(i)[1] - points.get(i - 1)[1]);
            }
            for (int i = 0; i < points.size(); i++) {
                final double[] before = points.get(Math.max(0, i - 1));
                final double[] current = points.get(i);
                final double[] after = points.get(Math.min(points.size() - 1, i + 1));
                final double inLength = Math.hypot(current[0] - before[0], current[1] - before[1]);
                final double outLength = Math.hypot(after[0] - current[0], after[1] - current[1]);
                final double inX = inLength > 1.0E-8 ? -(current[1] - before[1]) / inLength : 0;
                final double inZ = inLength > 1.0E-8 ? (current[0] - before[0]) / inLength : 0;
                final double outX = outLength > 1.0E-8 ? -(after[1] - current[1]) / outLength : 0;
                final double outZ = outLength > 1.0E-8 ? (after[0] - current[0]) / outLength : 0;
                final double sumX = inX + outX;
                final double sumZ = inZ + outZ;
                final double sumLength = Math.hypot(sumX, sumZ);
                if (sumLength > 1.0E-8) {
                    final double dot = (sumX * (outLength > 1.0E-8 ? outX : inX)
                            + sumZ * (outLength > 1.0E-8 ? outZ : inZ)) / sumLength;
                    final double multiplier = Math.min(2, 1 / Math.max(0.5, dot)) / sumLength;
                    normalX[i] = sumX * multiplier;
                    normalZ[i] = sumZ * multiplier;
                } else {
                    normalX[i] = outX;
                    normalZ[i] = outZ;
                }
            }
        }
    }

    public MapTrack forScale(double scale) {
        return curve == null ? this : curve.tessellate(id, scale);
    }

    /** Unknown heights remain NaN; a missing Y must never be invented from X/Z. */
    public double height(int index) {
        final double[] point = points.get(index);
        return point.length >= 3 ? point[2] : Double.NaN;
    }

    public double length() {
        return distances.length == 0 ? 0 : distances[distances.length - 1];
    }

    /** Fast rejection before inspecting the rail's individual segments. */
    public boolean intersects(double viewMinX, double viewMinZ, double viewMaxX, double viewMaxZ) {
        return validGeometry && maxX >= viewMinX && minX <= viewMaxX
                && maxZ >= viewMinZ && minZ <= viewMaxZ;
    }
}

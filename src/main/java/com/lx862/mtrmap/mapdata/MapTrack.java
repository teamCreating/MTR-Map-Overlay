package com.lx862.mtrmap.mapdata;

import java.util.List;

/**
 * Render-ready representation of one stretch of physical rail for the map
 * track layer. Points are sampled along the rail's actual curve (including
 * arcs and slopes projected to X/Z).
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

    public MapTrack(String id, List<double[]> points) {
        this.id = id == null ? "" : id;
        this.points = points;
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean validGeometry = true;
        for (double[] point : points) {
            if (point == null || point.length < 2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])) {
                validGeometry = false;
                break;
            }
            minX = Math.min(minX, point[0]);
            minZ = Math.min(minZ, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxZ = Math.max(maxZ, point[1]);
        }
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.validGeometry = validGeometry && points.size() >= 2;
    }

    /** Fast rejection before inspecting the rail's individual segments. */
    public boolean intersects(double viewMinX, double viewMinZ, double viewMaxX, double viewMaxZ) {
        return validGeometry && maxX >= viewMinX && minX <= viewMaxX
                && maxZ >= viewMinZ && minZ <= viewMaxZ;
    }
}

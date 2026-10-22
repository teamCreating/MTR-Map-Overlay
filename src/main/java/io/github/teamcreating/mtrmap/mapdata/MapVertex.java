package io.github.teamcreating.mtrmap.mapdata;

/** A measured world-space point. Y is never a GUI depth or projected-map coordinate. */
public record MapVertex(double x, double y, double z) {
    public boolean finite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public MapVertex lerp(MapVertex other, double t) {
        return new MapVertex(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
    }

    public double distanceTo(MapVertex other) {
        return Math.hypot(Math.hypot(x - other.x, y - other.y), z - other.z);
    }
}

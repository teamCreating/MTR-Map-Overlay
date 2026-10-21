package com.lx862.mtrmap.mapdata;

import java.util.List;

/** One rigid car's actual Core/MTR body corners and service metadata, independent of the map API. */
public record MapCar(long vehicleId, int index, int count, boolean leading, String modelId,
        String routeName, String destination, int color,
        MapVertex frontRight, MapVertex backRight, MapVertex backLeft, MapVertex frontLeft) {
    public List<MapVertex> corners() {
        return List.of(frontRight, backRight, backLeft, frontLeft);
    }

    public MapVertex center() {
        return frontRight.lerp(backLeft, 0.5);
    }

    public boolean valid() {
        return frontRight.finite() && backRight.finite() && backLeft.finite() && frontLeft.finite()
                && frontRight.distanceTo(backRight) > 1.0E-6 && frontRight.distanceTo(frontLeft) > 1.0E-6;
    }

    public boolean intersects(double minX, double minZ, double maxX, double maxZ) {
        final double left = Math.min(Math.min(frontRight.x(), backRight.x()), Math.min(backLeft.x(), frontLeft.x()));
        final double right = Math.max(Math.max(frontRight.x(), backRight.x()), Math.max(backLeft.x(), frontLeft.x()));
        final double bottom = Math.min(Math.min(frontRight.z(), backRight.z()), Math.min(backLeft.z(), frontLeft.z()));
        final double top = Math.max(Math.max(frontRight.z(), backRight.z()), Math.max(backLeft.z(), frontLeft.z()));
        return right >= minX && left <= maxX && top >= minZ && bottom <= maxZ;
    }
}

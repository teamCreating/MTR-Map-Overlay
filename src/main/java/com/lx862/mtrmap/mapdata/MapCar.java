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
        double left = Double.POSITIVE_INFINITY, bottom = left;
        double right = Double.NEGATIVE_INFINITY, top = right;
        for (MapVertex point : corners()) {
            left = Math.min(left, point.x());
            right = Math.max(right, point.x());
            bottom = Math.min(bottom, point.z());
            top = Math.max(top, point.z());
        }
        return right >= minX && left <= maxX && top >= minZ && bottom <= maxZ;
    }
}

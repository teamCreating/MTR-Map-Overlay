package com.lx862.mtrmap.integration.journeymap;

import java.awt.geom.Point2D;

/** Affine world-to-GUI transform sampled from JourneyMap's current renderer. */
record JourneyMapScreenProjection(double originX, double originY,
        double xStepX, double xStepY, double zStepX, double zStepY) {

    static JourneyMapScreenProjection fromSamples(Point2D origin, Point2D xStep, Point2D zStep,
            double guiScale) {
        return new JourneyMapScreenProjection(origin.getX() / guiScale, origin.getY() / guiScale,
                (xStep.getX() - origin.getX()) / guiScale,
                (xStep.getY() - origin.getY()) / guiScale,
                (zStep.getX() - origin.getX()) / guiScale,
                (zStep.getY() - origin.getY()) / guiScale);
    }

    /** JourneyMap renders an active drag as a temporary map translation before recentering. */
    JourneyMapScreenProjection withDrag(double blockX, double blockZ) {
        return new JourneyMapScreenProjection(
                originX + blockX * xStepX + blockZ * zStepX,
                originY + blockX * xStepY + blockZ * zStepY,
                xStepX, xStepY, zStepX, zStepY);
    }

    double x(double blockX, double blockZ) {
        return originX + blockX * xStepX + blockZ * zStepX;
    }

    double y(double blockX, double blockZ) {
        return originY + blockX * xStepY + blockZ * zStepY;
    }

    double pixelsPerBlock() {
        return Math.hypot(xStepX, xStepY);
    }

    WorldBounds visibleWorldBounds(int width, int height, double margin) {
        final double determinant = xStepX * zStepY - zStepX * xStepY;
        if (!Double.isFinite(determinant) || Math.abs(determinant) < 1.0E-12) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double screenX : new double[] {-margin, width + margin}) {
            for (double screenY : new double[] {-margin, height + margin}) {
                final double dx = screenX - originX;
                final double dy = screenY - originY;
                final double blockX = (dx * zStepY - dy * zStepX) / determinant;
                final double blockZ = (dy * xStepX - dx * xStepY) / determinant;
                minX = Math.min(minX, blockX);
                maxX = Math.max(maxX, blockX);
                minZ = Math.min(minZ, blockZ);
                maxZ = Math.max(maxZ, blockZ);
            }
        }
        return new WorldBounds(minX, minZ, maxX, maxZ);
    }

    record WorldBounds(double minX, double minZ, double maxX, double maxZ) {
        boolean intersects(double otherMinX, double otherMinZ, double otherMaxX, double otherMaxZ) {
            return otherMaxX >= minX && otherMinX <= maxX && otherMaxZ >= minZ && otherMinZ <= maxZ;
        }
    }
}

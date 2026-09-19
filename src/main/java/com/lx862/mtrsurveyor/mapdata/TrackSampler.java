package com.lx862.mtrsurveyor.mapdata;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Samples a rail's real geometry (arcs, slopes) into an X/Z polyline.
 * Shared by the client-side collector and the server-side network collector
 * so both produce identical track data.
 */
public final class TrackSampler {

    /** World-block distance between samples along a rail curve. */
    public static final double SAMPLE_INTERVAL = 8.0;
    private static final int MAX_SAMPLES_PER_RAIL = 256;

    private TrackSampler() {
    }

    /**
     * Sample one stretch of a real driving path ({@link PathData}) into an X/Z
     * polyline, ordered along the traversal direction. Returns {@code null}
     * when the rail reference is missing or the stretch has no length.
     */
    public static List<double[]> samplePathData(PathData pathData) {
        try {
            final Rail rail = pathData.getRail();
            if (rail == null || !rail.isValid() || rail.railMath == null) {
                return null;
            }
            final double length = rail.railMath.getLength();
            if (length <= 0) {
                return null;
            }
            final double d1 = Math.max(0, Math.min(length, pathData.getStartDistance()));
            final double d2 = Math.max(0, Math.min(length, pathData.getEndDistance()));
            if (Math.abs(d2 - d1) < 1.0E-3) {
                return null;
            }
            final int sampleCount = (int) Math.min(MAX_SAMPLES_PER_RAIL,
                    Math.max(2, Math.ceil(Math.abs(d2 - d1) / SAMPLE_INTERVAL) + 1));
            final ArrayList<double[]> points = new ArrayList<>(sampleCount);
            for (int i = 0; i <= sampleCount; i++) {
                final double d = d1 + (d2 - d1) * i / sampleCount;
                final Vector pos = rail.railMath.getPosition(d, false);
                points.add(new double[]{pos.x(), pos.z()});
            }
            return points;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Sample one rail into a polyline. Returns {@code null} when the rail is
     * not a train rail or has no usable geometry.
     */
    public static List<double[]> sample(Rail rail) {
        try {
            if (rail.getTransportMode() != TransportMode.TRAIN || !rail.isValid()) {
                return null;
            }
            final RailMath railMath = rail.railMath;
            final double length = railMath.getLength();
            if (length <= 0 || Double.isNaN(length)) {
                return null;
            }

            final int sampleCount = (int) Math.min(MAX_SAMPLES_PER_RAIL,
                    Math.max(2, Math.ceil(length / SAMPLE_INTERVAL) + 1));
            final ArrayList<double[]> points = new ArrayList<>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                final double distance = Math.min(length, i * (length / (sampleCount - 1)));
                final Vector pos = railMath.getPosition(distance, false);
                points.add(new double[]{pos.x(), pos.z()});
            }
            return points;
        } catch (Throwable e) {
            return null;
        }
    }
}

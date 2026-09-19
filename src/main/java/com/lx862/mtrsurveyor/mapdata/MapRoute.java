package com.lx862.mtrsurveyor.mapdata;

import java.util.List;

/**
 * Render-ready representation of one MTR route for the map path layer.
 *
 * <p>Two geometries are carried:</p>
 * <ul>
 *   <li>{@code stops} - one point per platform stop, used for hover tooltips
 *       (station name + destination);</li>
 *   <li>{@code path} - the snapped track geometry (points sampled along the
 *       real rails, each carrying a lane offset index for parallel routes).
 *       When empty, the renderer falls back to straight stop-to-stop lines
 *       (client-only mode without server-synced data).</li>
 * </ul>
 */
public class MapRoute {

    public final String name;
    public final int color;
    /** True when the route is circular, in which case the polyline closes back to the first stop. */
    public final boolean circular;
    public final List<Stop> stops;
    public final List<PathPoint> path;

    public MapRoute(String name, int color, boolean circular, List<Stop> stops, List<PathPoint> path) {
        this.name = name;
        this.color = color;
        this.circular = circular;
        this.stops = stops;
        this.path = path == null ? List.of() : path;
    }

    /** One stop on a route: its world position plus display strings for tooltips. */
    public static class Stop {

        public final double x;
        public final double z;
        public final String stationName;
        public final String destination;

        public Stop(double x, double z, String stationName, String destination) {
            this.x = x;
            this.z = z;
            this.stationName = stationName;
            this.destination = destination;
        }
    }

    /** One render point of the snapped track geometry; lane is the parallel-route offset index. */
    public record PathPoint(double x, double z, int lane) {
    }
}

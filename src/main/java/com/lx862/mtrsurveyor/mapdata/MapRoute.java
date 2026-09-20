package com.lx862.mtrsurveyor.mapdata;

import java.util.List;

/**
 * Render-ready representation of one MTR route for the map path layer.
 *
 * <p>Two geometries are carried:</p>
 * <ul>
 *   <li>{@code stops} - one point per platform stop, used for hover tooltips
 *       (station name + destination);</li>
 *   <li>{@code tracks} - the physical rail strokes used by this route. These
 *       are {@link MapTrack} objects, identical to the gray TRACK layer data.
 *       When empty, the renderer omits the route rather than inventing a
 *       station-to-station chord that does not follow track.</li>
 * </ul>
 */
public class MapRoute {

    public final String name;
    public final int color;
    /** True when the route is circular, in which case the polyline closes back to the first stop. */
    public final boolean circular;
    public final List<Stop> stops;
    public final List<MapTrack> tracks;

    public MapRoute(String name, int color, boolean circular, List<Stop> stops, List<MapTrack> tracks) {
        this.name = name;
        this.color = color;
        this.circular = circular;
        this.stops = stops;
        this.tracks = tracks == null ? List.of() : tracks;
    }

    /** A route represented only by stop metadata; it is not rendered without physical tracks. */
    public static MapRoute ofStops(String name, int color, boolean circular, List<Stop> stops) {
        return new MapRoute(name, color, circular, stops, List.of());
    }

    /** A route represented by the same physical rail strokes as the TRACK layer. */
    public static MapRoute ofTracks(String name, int color, List<Stop> stops, List<MapTrack> tracks) {
        return new MapRoute(name, color, false, stops, tracks);
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

}

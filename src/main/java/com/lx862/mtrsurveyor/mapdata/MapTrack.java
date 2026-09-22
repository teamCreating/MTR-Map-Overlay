package com.lx862.mtrsurveyor.mapdata;

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

    public MapTrack(String id, List<double[]> points) {
        this.id = id == null ? "" : id;
        this.points = points;
    }
}

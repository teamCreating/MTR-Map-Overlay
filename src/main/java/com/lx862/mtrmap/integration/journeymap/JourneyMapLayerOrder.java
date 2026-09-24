package com.lx862.mtrmap.integration.journeymap;

/**
 * JourneyMap's fullscreen overlay stack puts a lower display order in front.
 * Keep this relationship in one place: landmarks > routes > tracks.
 */
final class JourneyMapLayerOrder {

    static final int LANDMARK = -30;
    static final int ROUTE = -20;
    static final int TRACK = -10;

    private JourneyMapLayerOrder() {
    }
}

package com.lx862.mtrmap.integration.journeymap;

/**
 * Native JourneyMap markers still use display order for hover selection;
 * paths and foreground symbols are drawn explicitly in one render event.
 */
final class JourneyMapLayerOrder {

    static final int LANDMARK = 0;

    private JourneyMapLayerOrder() {
    }
}

package com.lx862.mtrmap.integration.journeymap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyMapLayerOrderTest {

    @Test
    void landmarksStayAboveRoutesAndTracks() {
        // JourneyMap's lower display-order value is visually in front.
        assertTrue(JourneyMapLayerOrder.LANDMARK < JourneyMapLayerOrder.ROUTE);
        assertTrue(JourneyMapLayerOrder.ROUTE < JourneyMapLayerOrder.TRACK);
    }
}

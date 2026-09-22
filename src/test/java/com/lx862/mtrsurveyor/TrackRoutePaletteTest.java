package com.lx862.mtrsurveyor;

import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.TrackRoutePalette;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackRoutePaletteTest {

    @Test
    void sharedRailUsesStableRouteOrderAndDeduplicatesRouteFragments() {
        final MapRoute routeB = new MapRoute("b", "Blue", 0x0000FF, false, List.of(), List.of("rail-1"));
        final MapRoute routeA = new MapRoute("a", "Red", 0xFF0000, false, List.of(), List.of("rail-1"));
        final MapRoute routeAFragment = new MapRoute("a", "Red", 0xFF0000, false, List.of(),
                List.of("rail-1", "rail-2"));

        final var palette = TrackRoutePalette.build(List.of(routeB, routeAFragment, routeA));

        assertEquals(List.of("a", "b"), palette.get("rail-1").stream().map(TrackRoutePalette.Entry::id).toList());
        assertEquals(List.of("a"), palette.get("rail-2").stream().map(TrackRoutePalette.Entry::id).toList());
    }
}

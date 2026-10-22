package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.mapdata.MapLandmark;
import io.github.teamcreating.mtrmap.mapdata.MapRoute;
import io.github.teamcreating.mtrmap.mapdata.MapTrack;
import io.github.teamcreating.mtrmap.mapdata.RouteLineStyle;
import io.github.teamcreating.mtrmap.network.NetworkSnapshotCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkSyncChunkTest {

    @Test
    void protocolEightRoundTripsXYZRouteStylesRailReferencesAndLandmarks() throws Exception {
        final NetworkSnapshotCodec.PendingDimension source =
                new NetworkSnapshotCodec.PendingDimension("minecraft/overworld", 42L);
        source.tracks.add(new MapTrack("rail-a", List.of(new double[]{29_000_000.125, 2.25, -40.75},
                new double[]{29_000_003.875, 4.5, 80.125})));
        source.routes.add(new MapRoute("route-a", "Red", 0xFF0000, RouteLineStyle.HIGH_SPEED, false,
                List.of(new MapRoute.Stop(2, 3, "Central", "Terminus")), List.of("rail-a")));
        source.landmarks.add(new MapLandmark("platform:a", MapLandmark.Type.PLATFORM,
                2, 64, 3, "Central", "1", "Red→Terminus", true));

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NetworkSnapshotCodec.writeDimensionList(new DataOutputStream(bytes), List.of(source));
        final var decoded = NetworkSnapshotCodec.readDimensionList(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))).get(0);

        assertEquals("minecraft/overworld", decoded.dimensionId);
        assertEquals(List.of("rail-a"), decoded.routes.get(0).trackIds);
        assertEquals(RouteLineStyle.HIGH_SPEED, decoded.routes.get(0).style);
        assertEquals(RouteLineStyle.HIGH_SPEED, decoded.routePalette.get("rail-a").get(0).style());
        assertEquals("rail-a", decoded.tracks.get(0).id);
        assertEquals(29_000_000.125, decoded.tracks.get(0).points.get(0)[0]);
        assertEquals(2.25, decoded.tracks.get(0).points.get(0)[1]);
        assertEquals(-40.75, decoded.tracks.get(0).height(0));
        assertEquals(80.125, decoded.tracks.get(0).height(1));
        assertEquals(MapLandmark.Type.PLATFORM, decoded.landmarks.get(0).type());
        assertEquals("Red→Terminus", decoded.landmarks.get(0).description());
    }

    @Test
    void rejectsCountsThatCannotFitInThePayload() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(Integer.MAX_VALUE);

        assertThrows(IOException.class, () -> NetworkSnapshotCodec.readDimensionList(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}

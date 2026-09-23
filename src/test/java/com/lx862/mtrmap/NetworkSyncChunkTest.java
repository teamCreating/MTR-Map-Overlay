package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.MapLandmark;
import com.lx862.mtrmap.mapdata.MapRoute;
import com.lx862.mtrmap.mapdata.MapTrack;
import com.lx862.mtrmap.network.NetworkSnapshotCodec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkSyncChunkTest {

    @Test
    void protocolFiveRoundTripsRailReferencesAndLandmarks() throws Exception {
        final NetworkSnapshotCodec.PendingDimension source =
                new NetworkSnapshotCodec.PendingDimension("minecraft/overworld", 42L);
        source.tracks.add(new MapTrack("rail-a", List.of(new double[]{1, 2}, new double[]{3, 4})));
        source.routes.add(new MapRoute("route-a", "Red", 0xFF0000, false,
                List.of(new MapRoute.Stop(2, 3, "Central", "Terminus")), List.of("rail-a")));
        source.landmarks.add(new MapLandmark("platform:a", MapLandmark.Type.PLATFORM,
                2, 64, 3, "Central", "1", "Red→Terminus", true));

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NetworkSnapshotCodec.writeDimensionList(new DataOutputStream(bytes), List.of(source));
        final var decoded = NetworkSnapshotCodec.readDimensionList(
                new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))).getFirst();

        assertEquals("minecraft/overworld", decoded.dimensionId);
        assertEquals(List.of("rail-a"), decoded.routes.getFirst().trackIds);
        assertEquals("rail-a", decoded.tracks.getFirst().id);
        assertEquals(MapLandmark.Type.PLATFORM, decoded.landmarks.getFirst().type());
        assertEquals("Red→Terminus", decoded.landmarks.getFirst().description());
    }
}

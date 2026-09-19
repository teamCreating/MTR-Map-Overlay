package com.lx862.mtrsurveyor.network;

import com.lx862.mtrsurveyor.MTRSurveyor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge payload registration for the full-network map sync (Plan C).
 *
 * <p>When this mod is installed on the server, clients can request a snapshot
 * of the whole MTR network (routes + sampled track geometry) per dimension -
 * the same data model Create's train map uses. The snapshot is transferred in
 * chunks so arbitrarily large networks stay within packet size limits.</p>
 *
 * <p>Both payloads are registered as {@code optional()}, so connecting to a
 * NeoForge server without this mod never disconnects the client; presence is
 * probed empirically by {@link ClientNetworkSync}.</p>
 */
public class MTRNetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(RequestNetworkSync.TYPE, RequestNetworkSync.STREAM_CODEC, RequestNetworkSync::handle);
        registrar.playToClient(NetworkSyncChunk.TYPE, NetworkSyncChunk.STREAM_CODEC, NetworkSyncChunk::handle);
        MTRSurveyor.LOGGER.info("[MTRSurveyor] Full-network sync payloads registered (protocol {})", PROTOCOL_VERSION);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MTRSurveyor.MOD_ID, path);
    }
}

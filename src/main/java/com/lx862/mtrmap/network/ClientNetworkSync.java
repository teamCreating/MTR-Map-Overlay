package com.lx862.mtrmap.network;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrmap.mapdata.MapDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side half of the full-network map sync: requests snapshots from the
 * server, reassembles chunked transfers and feeds {@link MapDataCache}.
 *
 * <p>Fully optional - when the server does not run this mod the client falls
 * back to MTR's own radius-limited client data. Requests are only sent over
 * NeoForge connections; payloads are registered as optional so even a
 * NeoForge server without this mod cannot break the client.</p>
 */
public final class ClientNetworkSync {

    /** After the first successful sync, refresh at least this often while playing. */
    private static final long MIN_REFRESH_MILLIS = 60_000;

    private static final Map<Integer, NetworkChunkAssembler> transfers = new ConcurrentHashMap<>();
    private static final Map<String, Long> knownHashes = new ConcurrentHashMap<>();
    private static long lastProbeMillis = 0;
    private static int SCREEN_TRACE_TIMER = 0;
    /** Flips to true when a server answered at least once; flips back on world change. */
    private static boolean serverHasSupport = false;
    /** Set when the connected server clearly has no support, to back off requests. */
    private static long serverUnsupportedBackoffUntil = 0;

    private ClientNetworkSync() {
    }

    public static void requestSync(String trigger) {
        requestSync(trigger, null);
    }

    private static void requestSync(String trigger, String dimensionFilter) {
        if (!MTRMapConfig.INSTANCE.networkSyncEnabled.get()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        if (System.currentTimeMillis() < serverUnsupportedBackoffUntil) {
            return;
        }
        try {
            if (!MTRNetwork.canSendToServer()) {
                // The remote server has not registered this mod's payloads.
                markServerUnsupported();
                return;
            }
            MTRNetwork.sendToServer(new RequestNetworkSync(dimensionFilter));
            if (MTRMapConfig.INSTANCE.debugLog.get()) {
                MTRMap.LOGGER.info("[MTRMap] Requested full-network snapshot ({}, filter={})",
                        trigger, dimensionFilter);
            }
        } catch (Throwable e) {
            // Payload rejected - back off for a while.
            MTRMap.LOGGER.debug("[MTRMap] Snapshot request failed (server lacks the mod?): {}", e.getMessage());
            markServerUnsupported();
        }
    }

    /** Ask the server for per-dimension content hashes (cheap hot-update probe). */
    private static void requestProbe() {
        try {
            if (!MTRNetwork.canSendToServer()) {
                markServerUnsupported();
                return;
            }
            MTRNetwork.sendToServer(NetworkSyncProbe.INSTANCE);
            lastProbeMillis = System.currentTimeMillis();
            if (MTRMapConfig.INSTANCE.debugLog.get()) {
                MTRMap.LOGGER.info("[MTRMap] Sent network probe");
            }
        } catch (Throwable e) {
            markServerUnsupported();
        }
    }

    /** Hot-update: pull full snapshots only for dimensions whose hash changed. */
    static void onProbeReceived(List<NetworkProbeResponse.DimensionHash> hashes) {
        serverHasSupport = true;
        for (NetworkProbeResponse.DimensionHash entry : hashes) {
            final Long known = knownHashes.get(entry.dimensionId());
            if (known == null || known.longValue() != entry.hash()) {
                requestSync("probe: " + entry.dimensionId() + " changed", entry.dimensionId());
            }
        }
    }

    static void onChunkReceived(NetworkSyncChunk chunk) {
        // Ignore stale transfers.
        transfers.values().removeIf(t -> t.ageMillis() > 120_000);

        try {
            final NetworkChunkAssembler buffer = transfers.computeIfAbsent(chunk.transferId(),
                    id -> new NetworkChunkAssembler(chunk.totalChunks(), chunk.snapshotHash()));
            if (!buffer.add(chunk.chunkIndex(), chunk.totalChunks(), chunk.snapshotHash(), chunk.data())) {
                return;
            }

            // All chunks present - reassemble.
            transfers.remove(chunk.transferId(), buffer);
            final byte[] payload = buffer.assemble();
            final List<MapDataCache.DimensionData> dimensions =
                    NetworkSnapshotCodec.readDimensionList(new java.io.DataInputStream(
                            new java.io.ByteArrayInputStream(payload)));
            if (dimensions.size() != 1 || dimensions.getFirst().version != buffer.snapshotHash()) {
                throw new IOException("Snapshot dimension count or hash does not match its chunks");
            }

            boolean firstOnServer = !serverHasSupport;
            serverHasSupport = true;
            for (MapDataCache.DimensionData dimension : dimensions) {
                MapDataCache.putServerData(dimension.dimensionId, dimension);
                knownHashes.put(dimension.dimensionId, dimension.version);
                MTRMap.LOGGER.info(
                        "[MTRMap] Full-network snapshot applied for {}: {} routes, {} rails, {} landmarks",
                        dimension.dimensionId, dimension.routes.size(), dimension.tracks.size(),
                        dimension.landmarks.size());
            }
            JourneyMapIntegration.requestSync();
            if (firstOnServer) {
                showActionbar("Full-network map sync active");
            }
        } catch (IOException e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to decode network snapshot", e);
        } catch (IllegalArgumentException e) {
            transfers.remove(chunk.transferId());
            MTRMap.LOGGER.warn("[MTRMap] Rejected invalid snapshot chunk: {}", e.getMessage());
        }
    }

    private static void markServerUnsupported() {
        serverUnsupportedBackoffUntil = System.currentTimeMillis() + 10 * 60_000;
    }

    private static void showActionbar(String message) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[MTR] " + message), true);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Events & ticking
    // -----------------------------------------------------------------------------------------------------------------

    public static void onClientTick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }

        // Diagnostics: periodic screen-state trace (debugLog only), useful for
        // checking which GUI the map layer should render into.
        if (MTRMapConfig.INSTANCE.debugLog.get()
                && (SCREEN_TRACE_TIMER++ % 60) == 0) {
            MTRMap.LOGGER.info("[MTRMap] screen-trace: {}", mc.screen);
        }


        if (!MTRMapConfig.INSTANCE.networkSyncEnabled.get()) {
            return;
        }

        final long now = System.currentTimeMillis();
        // Probing is cheap (O(network) hash, no transfer), so run it on the
        // regular cadence; full snapshots are pulled only when a hash changes.
        final long interval = Math.max(MIN_REFRESH_MILLIS,
                MTRMapConfig.INSTANCE.networkSyncIntervalSeconds.get() * 1000L);
        if (now >= serverUnsupportedBackoffUntil && now - lastProbeMillis >= interval) {
            requestProbe();
        }
    }

    public static void onLoggingIn() {
        // Fresh world/connection: reset sync state and ask for a snapshot as
        // soon as the server is ready to answer.
        MapDataCache.clearServerData();
        MapDataCache.clearClientData();
        transfers.clear();
        knownHashes.clear();
        serverHasSupport = false;
        serverUnsupportedBackoffUntil = 0;
        lastProbeMillis = System.currentTimeMillis()
                - Math.max(MIN_REFRESH_MILLIS, MTRMapConfig.INSTANCE.networkSyncIntervalSeconds.get() * 1000L)
                + 3_000; // first probe ~3 seconds after login
    }

    public static void onLoggingOut() {
        MapDataCache.clearServerData();
        MapDataCache.clearClientData();
        transfers.clear();
        knownHashes.clear();
        serverHasSupport = false;
        serverUnsupportedBackoffUntil = 0;
    }

}

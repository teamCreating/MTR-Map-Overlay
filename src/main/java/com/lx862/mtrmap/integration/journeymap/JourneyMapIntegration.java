package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.MapDataCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.mtr.client.MinecraftClientData;

/**
 * Entry point for the JourneyMap landmark integration. This class contains NO
 * JourneyMap imports so it is safe to load when JourneyMap is absent; the
 * JourneyMap-touching code lives in {@link JourneyMapLandmarkManager} and is
 * only invoked after the presence check.
 */
public final class JourneyMapIntegration {

    private static volatile boolean needsSync = false;
    private static int tickCounter = 0;
    private static final int SYNC_INTERVAL_TICKS = 100; // 5 seconds
    private static Object lastDimension = null;
    private static boolean journeyMapMissingLogged = false;

    private JourneyMapIntegration() {
    }

    /**
     * Check if JourneyMap is loaded (safe to call anywhere).
     */
    public static boolean isJourneyMapLoaded() {
        try {
            return ModList.get().isLoaded("journeymap");
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Mark that a landmark sync is needed (called from mixins and commands).
     */
    public static void requestSync() {
        needsSync = true;
        tickCounter = SYNC_INTERVAL_TICKS - 1; // Try on next tick cycle
    }

    /**
     * Called every client tick (from MTRMap). Tracks dimension changes
     * and performs queued landmark syncs once MTR data and the JourneyMap API
     * are ready.
     */
    public static void onClientTick() {
        final Level world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }

        // Re-sync when the player changes dimension so markers for the new
        // dimension are created
        final Object dimension = world.dimension();
        if (!dimension.equals(lastDimension)) {
            lastDimension = dimension;
            requestSync();
        }

        if (!needsSync) {
            return;
        }
        if (!MTRMapConfig.INSTANCE.enabled.get()) {
            return;
        }
        if (!isJourneyMapLoaded()) {
            if (!journeyMapMissingLogged) {
                journeyMapMissingLogged = true;
                MTRMap.LOGGER.info("[MTRMap] JourneyMap not installed - landmark integration disabled");
            }
            needsSync = false;
            return;
        }

        tickCounter++;
        if (tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        try {
            final MinecraftClientData clientData = MinecraftClientData.getInstance();
            if (clientData == null) {
                MTRMap.LOGGER.debug("[MTRMap] MTR client data not available yet, will retry...");
                return;
            }

            JourneyMapLandmarkManager.syncLandmarks("scheduled sync", world, clientData);
            needsSync = false;
        } catch (NoClassDefFoundError e) {
            MTRMap.LOGGER.warn("[MTRMap] JourneyMap classes not available: {}", e.getMessage());
            needsSync = false;
        } catch (Throwable e) {
            MTRMap.LOGGER.error("[MTRMap] Error during landmark sync tick", e);
            needsSync = false;
        }
    }

    /**
     * Show a diagnostic marker at the player's position (command
     * {@code /mtrmap testMarker}) to verify the JourneyMap integration
     * works end to end.
     *
     * @return a status message for the command feedback
     */
    public static String placeTestMarker() {
        if (!isJourneyMapLoaded()) {
            return "JourneyMap is not installed - nothing to test";
        }
        try {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return "Not in a world";
            }
            JourneyMapLandmarkManager.placeTestMarker(mc.player.blockPosition(), mc.level.dimension());
            return "Test marker placed at your position - check the JourneyMap fullscreen map (J)";
        } catch (NoClassDefFoundError e) {
            return "JourneyMap API not available: " + e.getMessage();
        } catch (Throwable e) {
            MTRMap.LOGGER.error("[MTRMap] Failed to place test marker", e);
            return "Failed: " + e.getMessage();
        }
    }
}

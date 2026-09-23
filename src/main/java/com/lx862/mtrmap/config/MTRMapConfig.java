package com.lx862.mtrmap.config;

import com.lx862.mtrmap.MTRMap;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MTRMapConfig {

        public static final ModConfigSpec SPEC;
        public static final MTRMapConfig INSTANCE;

        // General
        public final ModConfigSpec.BooleanValue formalInitLog;
        public final ModConfigSpec.BooleanValue debugLog;
        public final ModConfigSpec.BooleanValue enabled;

        // Client-only fallback mode: "station", "platform", or "both"
        public final ModConfigSpec.ConfigValue<String> waypointMode;

        // World map path layers
        public final ModConfigSpec.BooleanValue routeLinesEnabled;
        public final ModConfigSpec.BooleanValue trackLinesEnabled;

        // Full-network sync (requires the mod on the server)
        public final ModConfigSpec.BooleanValue networkSyncEnabled;
        public final ModConfigSpec.IntValue networkSyncIntervalSeconds;

        // Visibility
        public final ModConfigSpec.BooleanValue showStationLandmarks;
        public final ModConfigSpec.BooleanValue showPlatformLandmarks;
        public final ModConfigSpec.BooleanValue showDepotLandmarks;
        public final ModConfigSpec.BooleanValue showEmptyStation;
        public final ModConfigSpec.BooleanValue showHiddenRoute;

        static {
                ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
                INSTANCE = new MTRMapConfig(builder);
                SPEC = builder.build();
        }

        private MTRMapConfig(ModConfigSpec.Builder builder) {
                builder.comment("MTR Map Overlay Configuration");

                formalInitLog = builder
                                .comment("Change the mod initialization log message to be something more formal")
                                .define("formalInitLog", false);

                debugLog = builder
                                .comment("Log all landmark sync events to the console")
                                .define("debugLog", false);

                enabled = builder
                                .comment("Whether MTR routes, tracks and map-only landmark icons should be displayed")
                                .define("enabled", true);

                waypointMode = builder
                                .comment("Client-only fallback marker mode: 'station', 'platform', or 'both'. Full-network snapshots use the independent visibility switches below")
                                .define("waypointMode", "both");

                routeLinesEnabled = builder
                                .comment("Whether MTR route lines should be drawn on the Xaero's World Map")
                                .define("routeLinesEnabled", true);

                trackLinesEnabled = builder
                                .comment("Whether the MTR track layer (actual rail geometry) should be drawn on the Xaero's World Map")
                                .define("trackLinesEnabled", true);

                networkSyncEnabled = builder
                                .comment("Request full-network snapshots from servers that also run this mod (Create-train-map-style whole-network view). Client-only servers fall back to radius-limited MTR data automatically")
                                .define("networkSync.enabled", true);

                networkSyncIntervalSeconds = builder
                                .comment("How often (in seconds) to refresh the full-network snapshot while playing")
                                .defineInRange("networkSync.refreshIntervalSeconds", 300, 30, 3600);

                builder.push("visibility");

                showStationLandmarks = builder
                                .comment("Whether station icons should be drawn on fullscreen maps")
                                .define("showStationLandmarks", true);

                showPlatformLandmarks = builder
                                .comment("Whether platform icons should be drawn on fullscreen maps when zoomed in")
                                .define("showPlatformLandmarks", true);

                showDepotLandmarks = builder
                                .comment("Whether depot icons should be drawn on fullscreen maps")
                                .define("showDepotLandmarks", false);

                showEmptyStation = builder
                                .comment("Whether empty stations (with no routes) should be added to the map")
                                .define("showEmptyStation", false);

                showHiddenRoute = builder
                                .comment("Whether MTR routes marked as hidden should be appended to the station description")
                                .define("showHiddenRoute", false);

                builder.pop();
        }

        public static void register(ModContainer modContainer) {
                migrateLegacyConfig();
                modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "mtrmap.toml");
        }

        private static void migrateLegacyConfig() {
                Path configDirectory = FMLPaths.CONFIGDIR.get();
                Path oldConfig = configDirectory.resolve("mtrsurveyor.toml");
                Path newConfig = configDirectory.resolve("mtrmap.toml");

                if (!Files.exists(newConfig) && Files.isRegularFile(oldConfig)) {
                        try {
                                Files.copy(oldConfig, newConfig, StandardCopyOption.COPY_ATTRIBUTES);
                        } catch (IOException e) {
                                MTRMap.LOGGER.warn("Could not migrate the previous config file", e);
                        }
                }
        }
}

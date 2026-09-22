package com.lx862.mtrsurveyor.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public class MTRSurveyorConfig {

        public static final ModConfigSpec SPEC;
        public static final MTRSurveyorConfig INSTANCE;

        // General
        public final ModConfigSpec.BooleanValue formalInitLog;
        public final ModConfigSpec.BooleanValue debugLog;
        public final ModConfigSpec.BooleanValue enabled;

        // Waypoint mode: "station" or "platform"
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
                INSTANCE = new MTRSurveyorConfig(builder);
                SPEC = builder.build();
        }

        private MTRSurveyorConfig(ModConfigSpec.Builder builder) {
                builder.comment("MTR Surveyor Configuration");

                formalInitLog = builder
                                .comment("Change the mod initialization log message to be something more formal")
                                .define("formalInitLog", false);

                debugLog = builder
                                .comment("Log all landmark sync events to the console")
                                .define("debugLog", false);

                enabled = builder
                                .comment("Whether waypoints should be automatically created & synced when an MTR-related change occurs")
                                .define("enabled", true);

                waypointMode = builder
                                .comment("Waypoint display mode: 'station' shows one waypoint per station, 'platform' shows one waypoint per platform with route info")
                                .define("waypointMode", "station");

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
                                .comment("Whether station waypoints should be added to the map")
                                .define("showStationLandmarks", true);

                showPlatformLandmarks = builder
                                .comment("Whether platform waypoints should be added alongside station waypoints")
                                .define("showPlatformLandmarks", true);

                showDepotLandmarks = builder
                                .comment("Whether depot waypoints should be added to the map")
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
                modContainer.registerConfig(ModConfig.Type.COMMON, SPEC, "mtrsurveyor.toml");
        }
}

package io.github.teamcreating.mtrmap.config;

import io.github.teamcreating.mtrmap.MTRMap;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.ForgeConfigSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class MTRMapConfig {

        public static final ForgeConfigSpec SPEC;
        public static final MTRMapConfig INSTANCE;

        // General
        public final ForgeConfigSpec.BooleanValue formalInitLog;
        public final ForgeConfigSpec.BooleanValue debugLog;
        public final ForgeConfigSpec.BooleanValue enabled;

        // Client-only fallback mode: "station", "platform", or "both"
        public final ForgeConfigSpec.ConfigValue<String> waypointMode;

        // World map path layers
        public final ForgeConfigSpec.BooleanValue routeLinesEnabled;
        public final ForgeConfigSpec.BooleanValue trackLinesEnabled;
        public final ForgeConfigSpec.BooleanValue trainCarsEnabled;
        public final ForgeConfigSpec.ConfigValue<String> pidsStyle;

        // Full-network sync (requires the mod on the server)
        public final ForgeConfigSpec.BooleanValue networkSyncEnabled;
        public final ForgeConfigSpec.IntValue networkSyncIntervalSeconds;

        // Visibility
        public final ForgeConfigSpec.BooleanValue showStationLandmarks;
        public final ForgeConfigSpec.BooleanValue showPlatformLandmarks;
        public final ForgeConfigSpec.BooleanValue showDepotLandmarks;
        public final ForgeConfigSpec.BooleanValue showEmptyStation;
        public final ForgeConfigSpec.BooleanValue showHiddenRoute;

        static {
                ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
                INSTANCE = new MTRMapConfig(builder);
                SPEC = builder.build();
        }

        private MTRMapConfig(ForgeConfigSpec.Builder builder) {
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

                trainCarsEnabled = builder.comment("Draw TSC global train symbols on the map").define("trainCarsEnabled", true);

                pidsStyle = builder.define("pidsStyle", "british", value -> value instanceof String str && (str.equals("british") || str.equals("european")));

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

        public static void setPidsStyle(String style) { INSTANCE.pidsStyle.set(PidsStyle.parse(style).key); SPEC.save(); }

        public static void register(ModContainer modContainer) {
                migrateLegacyConfig();
                ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "mtrmap.toml");
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

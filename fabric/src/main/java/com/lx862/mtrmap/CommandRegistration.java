package com.lx862.mtrmap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrmap.network.ClientNetworkSync;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Fabric client-side counterpart of the NeoForge /mtrmap command. */
public final class CommandRegistration {
    private CommandRegistration() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> mode = literal("mode")
                .then(literal("station").executes(ctx -> setMode(ctx.getSource(), "station", true, false)))
                .then(literal("platform").executes(ctx -> setMode(ctx.getSource(), "platform", false, true)))
                .then(literal("both").executes(ctx -> setMode(ctx.getSource(), "both", true, true)))
                .executes(ctx -> feedback(ctx.getSource(),
                        "Current map marker mode: " + MTRMapConfig.INSTANCE.waypointMode.get()));

        LiteralArgumentBuilder<FabricClientCommandSource> config = literal("config")
                .then(boolConfig("enabled", "MTR map overlays", MTRMapConfig.INSTANCE.enabled::get,
                        MTRMapConfig.INSTANCE.enabled::set))
                .then(boolConfig("showStations", "Station map icons",
                        MTRMapConfig.INSTANCE.showStationLandmarks::get,
                        MTRMapConfig.INSTANCE.showStationLandmarks::set))
                .then(boolConfig("showPlatforms", "Platform map icons",
                        MTRMapConfig.INSTANCE.showPlatformLandmarks::get,
                        MTRMapConfig.INSTANCE.showPlatformLandmarks::set))
                .then(boolConfig("showDepots", "Depot map icons",
                        MTRMapConfig.INSTANCE.showDepotLandmarks::get,
                        MTRMapConfig.INSTANCE.showDepotLandmarks::set))
                .then(boolConfig("routeLines", "Map route lines",
                        MTRMapConfig.INSTANCE.routeLinesEnabled::get,
                        MTRMapConfig.INSTANCE.routeLinesEnabled::set))
                .then(boolConfig("trackLines", "Map track layer",
                        MTRMapConfig.INSTANCE.trackLinesEnabled::get,
                        MTRMapConfig.INSTANCE.trackLinesEnabled::set));

        dispatcher.register(literal("mtrmap")
                .then(mode)
                .then(config)
                .then(literal("syncRoutes").executes(ctx -> {
                    ClientNetworkSync.requestSync("manual /mtrmap syncRoutes");
                    return feedback(ctx.getSource(), "Full-network snapshot requested");
                }))
                .then(literal("syncLandmarks").executes(ctx -> {
                    if (!JourneyMapIntegration.isJourneyMapLoaded()) {
                        ctx.getSource().sendError(Component.literal("JourneyMap is not installed"));
                        return 0;
                    }
                    JourneyMapIntegration.requestSync();
                    return feedback(ctx.getSource(), "Landmark sync requested");
                }))
                .then(literal("testMarker").executes(ctx -> {
                    String result = JourneyMapIntegration.placeTestMarker();
                    if (result.startsWith("Failed") || result.contains("not installed")
                            || result.contains("Not in a world")) {
                        ctx.getSource().sendError(Component.literal(result));
                        return 0;
                    }
                    return feedback(ctx.getSource(), result);
                })));
    }

    private static int setMode(FabricClientCommandSource source, String mode,
            boolean showStations, boolean showPlatforms) {
        MTRMapConfig.INSTANCE.waypointMode.set(mode);
        MTRMapConfig.INSTANCE.showStationLandmarks.set(showStations);
        MTRMapConfig.INSTANCE.showPlatformLandmarks.set(showPlatforms);
        JourneyMapIntegration.requestSync();
        return feedback(source, "Map marker mode set to: " + mode);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> boolConfig(String name,
            String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return literal(name)
                .then(argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                    setter.accept(enabled);
                    JourneyMapIntegration.requestSync();
                    return feedback(ctx.getSource(), label + " set to " + enabled);
                }))
                .executes(ctx -> feedback(ctx.getSource(), label + " is currently set to " + getter.get()));
    }

    private static int feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
        return 1;
    }
}

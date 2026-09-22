package com.lx862.mtrsurveyor;

import com.lx862.mtrsurveyor.config.MTRSurveyorConfig;
import com.lx862.mtrsurveyor.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrsurveyor.network.ClientNetworkSync;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommandRegistration {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                LiteralArgumentBuilder<CommandSourceStack> rootNode = Commands.literal("mtrsurveyor");

                // /mtrsurveyor mode station|platform
                LiteralArgumentBuilder<CommandSourceStack> modeNode = Commands.literal("mode");

                modeNode.then(Commands.literal("station").executes(ctx -> {
                        MTRSurveyorConfig.INSTANCE.waypointMode.set("station");
                        MTRSurveyorConfig.INSTANCE.showStationLandmarks.set(true);
                        MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.set(false);
                        JourneyMapIntegration.requestSync();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal(
                                                        "Map marker mode set to: station")
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                modeNode.then(Commands.literal("platform").executes(ctx -> {
                        MTRSurveyorConfig.INSTANCE.waypointMode.set("platform");
                        MTRSurveyorConfig.INSTANCE.showStationLandmarks.set(false);
                        MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.set(true);
                        JourneyMapIntegration.requestSync();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal(
                                                        "Map marker mode set to: platform")
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                modeNode.then(Commands.literal("both").executes(ctx -> {
                        MTRSurveyorConfig.INSTANCE.waypointMode.set("both");
                        MTRSurveyorConfig.INSTANCE.showStationLandmarks.set(true);
                        MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.set(true);
                        JourneyMapIntegration.requestSync();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("Map marker mode set to: both (stations and platforms)")
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                // /mtrsurveyor mode (query current mode)
                modeNode.executes(ctx -> {
                        String currentMode = MTRSurveyorConfig.INSTANCE.waypointMode.get();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("Current map marker mode: " + currentMode)
                                                        .withStyle(ChatFormatting.AQUA),
                                        false);
                        return 1;
                });

                // /mtrsurveyor syncRoutes - request a full-network snapshot from the server
                LiteralArgumentBuilder<CommandSourceStack> syncRoutesNode = Commands.literal("syncRoutes");
                syncRoutesNode.executes(ctx -> {
                        ClientNetworkSync.requestSync("manual /mtrsurveyor syncRoutes");
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal(
                                                        "Full-network snapshot requested! (If the server also runs this mod, the whole network will appear shortly.)")
                                                                        .withStyle(ChatFormatting.GREEN),
                                                        true);
                        return 1;
                });

                // /mtrsurveyor syncLandmarks - force a JourneyMap landmark refresh
                LiteralArgumentBuilder<CommandSourceStack> syncLandmarksNode = Commands.literal("syncLandmarks");
                syncLandmarksNode
                                .executes(ctx -> {
                                        if (!JourneyMapIntegration.isJourneyMapLoaded()) {
                                                ctx.getSource().sendFailure(
                                                                Component.literal("JourneyMap is not installed!")
                                                                                .withStyle(ChatFormatting.RED));
                                                return 1;
                                        }
                                        JourneyMapIntegration.requestSync();
                                        ctx.getSource().sendSuccess(
                                                        () -> Component.literal(
                                                                        "Landmark sync requested! Markers will update shortly.")
                                                                                        .withStyle(ChatFormatting.GREEN),
                                                        true);
                                        return 1;
                                });

                // /mtrsurveyor testMarker - place a diagnostic marker at the player position
                LiteralArgumentBuilder<CommandSourceStack> testMarkerNode = Commands.literal("testMarker");
                testMarkerNode
                                .executes(ctx -> {
                                        String result = JourneyMapIntegration.placeTestMarker();
                                        boolean ok = !result.startsWith("Failed") && !result.contains("not installed")
                                                        && !result.contains("Not in a world");
                                        if (ok) {
                                                ctx.getSource().sendSuccess(
                                                                () -> Component.literal(result)
                                                                                .withStyle(ChatFormatting.GREEN),
                                                                true);
                                        } else {
                                                ctx.getSource().sendFailure(
                                                                Component.literal(result).withStyle(ChatFormatting.RED));
                                        }
                                        return 1;
                                });

                // Config sub-commands
                LiteralArgumentBuilder<CommandSourceStack> configNode = Commands.literal("config");
                configNode.then(createBoolConfigNode("enabled", "MTR map overlays",
                                () -> MTRSurveyorConfig.INSTANCE.enabled.get(),
                                v -> MTRSurveyorConfig.INSTANCE.enabled.set(v)));
                configNode.then(createBoolConfigNode("showStations", "Station map icons",
                                () -> MTRSurveyorConfig.INSTANCE.showStationLandmarks.get(),
                                v -> MTRSurveyorConfig.INSTANCE.showStationLandmarks.set(v)));
                configNode.then(createBoolConfigNode("showPlatforms", "Platform map icons",
                                () -> MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.get(),
                                v -> MTRSurveyorConfig.INSTANCE.showPlatformLandmarks.set(v)));
                configNode.then(createBoolConfigNode("showDepots", "Depot map icons",
                                () -> MTRSurveyorConfig.INSTANCE.showDepotLandmarks.get(),
                                v -> MTRSurveyorConfig.INSTANCE.showDepotLandmarks.set(v)));
                configNode.then(createBoolConfigNode("routeLines", "Map route lines",
                                () -> MTRSurveyorConfig.INSTANCE.routeLinesEnabled.get(),
                                v -> MTRSurveyorConfig.INSTANCE.routeLinesEnabled.set(v)));
                configNode.then(createBoolConfigNode("trackLines", "Map track layer",
                                () -> MTRSurveyorConfig.INSTANCE.trackLinesEnabled.get(),
                                v -> MTRSurveyorConfig.INSTANCE.trackLinesEnabled.set(v)));

                rootNode.then(modeNode);
                rootNode.then(syncRoutesNode);
                rootNode.then(syncLandmarksNode);
                rootNode.then(testMarkerNode);
                rootNode.then(configNode);
                dispatcher.register(rootNode);
        }

        private static LiteralArgumentBuilder<CommandSourceStack> createBoolConfigNode(String configName,
                        String friendlyName, Supplier<Boolean> getValue, Consumer<Boolean> setValue) {
                LiteralArgumentBuilder<CommandSourceStack> cfgNode = Commands.literal(configName);
                cfgNode.then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                        setValue.accept(enabled);
                                        ctx.getSource().sendSuccess(
                                                        () -> Component.literal(friendlyName + " set to " + enabled)
                                                                        .withStyle(ChatFormatting.GREEN),
                                                        true);
                                        return 1;
                                }))
                                .executes(ctx -> {
                                        ctx.getSource().sendSuccess(
                                                        () -> Component.literal(friendlyName + " is currently set to "
                                                                        + getValue.get())
                                                                        .withStyle(ChatFormatting.AQUA),
                                                        false);
                                        return 1;
                                });
                return cfgNode;
        }
}

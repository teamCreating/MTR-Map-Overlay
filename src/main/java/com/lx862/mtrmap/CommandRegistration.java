package com.lx862.mtrmap;

import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrmap.network.ClientNetworkSync;
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
                LiteralArgumentBuilder<CommandSourceStack> rootNode = Commands.literal("mtrmap");

                // /mtrmap mode station|platform
                LiteralArgumentBuilder<CommandSourceStack> modeNode = Commands.literal("mode");

                modeNode.then(Commands.literal("station").executes(ctx -> {
                        MTRMapConfig.INSTANCE.waypointMode.set("station");
                        MTRMapConfig.INSTANCE.showStationLandmarks.set(true);
                        MTRMapConfig.INSTANCE.showPlatformLandmarks.set(false);
                        JourneyMapIntegration.requestSync();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal(
                                                        "Map marker mode set to: station")
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                modeNode.then(Commands.literal("platform").executes(ctx -> {
                        MTRMapConfig.INSTANCE.waypointMode.set("platform");
                        MTRMapConfig.INSTANCE.showStationLandmarks.set(false);
                        MTRMapConfig.INSTANCE.showPlatformLandmarks.set(true);
                        JourneyMapIntegration.requestSync();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal(
                                                        "Map marker mode set to: platform")
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                modeNode.then(Commands.literal("both").executes(ctx -> {
                        MTRMapConfig.INSTANCE.waypointMode.set("both");
                        MTRMapConfig.INSTANCE.showStationLandmarks.set(true);
                        MTRMapConfig.INSTANCE.showPlatformLandmarks.set(true);
                        JourneyMapIntegration.requestSync();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("Map marker mode set to: both (stations and platforms)")
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                // /mtrmap mode (query current mode)
                modeNode.executes(ctx -> {
                        String currentMode = MTRMapConfig.INSTANCE.waypointMode.get();
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal("Current map marker mode: " + currentMode)
                                                        .withStyle(ChatFormatting.AQUA),
                                        false);
                        return 1;
                });

                // /mtrmap syncRoutes - request a full-network snapshot from the server
                LiteralArgumentBuilder<CommandSourceStack> syncRoutesNode = Commands.literal("syncRoutes");
                syncRoutesNode.executes(ctx -> {
                        ClientNetworkSync.requestSync("manual /mtrmap syncRoutes");
                        ctx.getSource().sendSuccess(
                                        () -> Component.literal(
                                                        "Full-network snapshot requested! (If the server also runs this mod, the whole network will appear shortly.)")
                                                                        .withStyle(ChatFormatting.GREEN),
                                                        true);
                        return 1;
                });

                // /mtrmap syncLandmarks - force a JourneyMap landmark refresh
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

                // /mtrmap testMarker - place a diagnostic marker at the player position
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
                                () -> MTRMapConfig.INSTANCE.enabled.get(),
                                v -> MTRMapConfig.INSTANCE.enabled.set(v)));
                configNode.then(createBoolConfigNode("showStations", "Station map icons",
                                () -> MTRMapConfig.INSTANCE.showStationLandmarks.get(),
                                v -> MTRMapConfig.INSTANCE.showStationLandmarks.set(v)));
                configNode.then(createBoolConfigNode("showPlatforms", "Platform map icons",
                                () -> MTRMapConfig.INSTANCE.showPlatformLandmarks.get(),
                                v -> MTRMapConfig.INSTANCE.showPlatformLandmarks.set(v)));
                configNode.then(createBoolConfigNode("showDepots", "Depot map icons",
                                () -> MTRMapConfig.INSTANCE.showDepotLandmarks.get(),
                                v -> MTRMapConfig.INSTANCE.showDepotLandmarks.set(v)));
                configNode.then(createBoolConfigNode("routeLines", "Map route lines",
                                () -> MTRMapConfig.INSTANCE.routeLinesEnabled.get(),
                                v -> MTRMapConfig.INSTANCE.routeLinesEnabled.set(v)));
                configNode.then(createBoolConfigNode("trackLines", "Map track layer",
                                () -> MTRMapConfig.INSTANCE.trackLinesEnabled.get(),
                                v -> MTRMapConfig.INSTANCE.trackLinesEnabled.set(v)));

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

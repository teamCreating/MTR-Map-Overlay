package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.integration.journeymap.JourneyMapIntegration;
import io.github.teamcreating.mtrmap.network.ClientNetworkSync;
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
                                        () -> Component.translatable("mtrmap.command.mode_set", modeLabel("station"))
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
                                        () -> Component.translatable("mtrmap.command.mode_set", modeLabel("platform"))
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
                                        () -> Component.translatable("mtrmap.command.mode_set", modeLabel("both"))
                                                        .withStyle(ChatFormatting.GREEN),
                                        true);
                        return 1;
                }));

                // /mtrmap mode (query current mode)
                modeNode.executes(ctx -> {
                        String currentMode = MTRMapConfig.INSTANCE.waypointMode.get();
                        ctx.getSource().sendSuccess(
                                        () -> Component.translatable("mtrmap.command.mode_current", modeLabel(currentMode))
                                                        .withStyle(ChatFormatting.AQUA),
                                        false);
                        return 1;
                });

                // /mtrmap syncRoutes - request a full-network snapshot from the server
                LiteralArgumentBuilder<CommandSourceStack> syncRoutesNode = Commands.literal("syncRoutes");
                syncRoutesNode.executes(ctx -> {
                        ClientNetworkSync.requestSync("manual /mtrmap syncRoutes");
                        ctx.getSource().sendSuccess(
                                        () -> Component.translatable("mtrmap.command.sync_requested")
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
                                                                Component.translatable("mtrmap.command.journeymap_missing")
                                                                                .withStyle(ChatFormatting.RED));
                                                return 1;
                                        }
                                        JourneyMapIntegration.requestSync();
                                        ctx.getSource().sendSuccess(
                                                        () -> Component.translatable("mtrmap.command.landmarks_requested")
                                                                                        .withStyle(ChatFormatting.GREEN),
                                                        true);
                                        return 1;
                                });

                // /mtrmap testMarker - place a diagnostic marker at the player position
                LiteralArgumentBuilder<CommandSourceStack> testMarkerNode = Commands.literal("testMarker");
                testMarkerNode
                                .executes(ctx -> {
                                        var result = JourneyMapIntegration.placeTestMarker();
                                        if (result.success()) {
                                                ctx.getSource().sendSuccess(
                                                                () -> result.message().copy()
                                                                                .withStyle(ChatFormatting.GREEN),
                                                                true);
                                        } else {
                                                ctx.getSource().sendFailure(
                                                                result.message().copy().withStyle(ChatFormatting.RED));
                                        }
                                        return 1;
                                });

                // Config sub-commands
                LiteralArgumentBuilder<CommandSourceStack> configNode = Commands.literal("config");
                var pids = Commands.literal("pidsStyle").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.translatable("mtrmap.ui.pids_style", Component.translatable("mtrmap.pids_style."+MTRMapConfig.INSTANCE.pidsStyle.get())), false);
                        return 1;
                });
                for (var style : io.github.teamcreating.mtrmap.config.PidsStyle.values()) pids.then(Commands.literal(style.key).executes(ctx -> {
                        MTRMapConfig.setPidsStyle(style.key);
                        ctx.getSource().sendSuccess(() -> Component.translatable("mtrmap.ui.pids_style", Component.translatable("mtrmap.pids_style."+style.key)), false);
                        return 1;
                }));
                configNode.then(pids);
                configNode.then(createBoolConfigNode("enabled", "mtrmap.ui.enabled",
                                () -> MTRMapConfig.INSTANCE.enabled.get(),
                                v -> MTRMapConfig.INSTANCE.enabled.set(v)));
                configNode.then(createBoolConfigNode("showStations", "mtrmap.ui.stations",
                                () -> MTRMapConfig.INSTANCE.showStationLandmarks.get(),
                                v -> MTRMapConfig.INSTANCE.showStationLandmarks.set(v)));
                configNode.then(createBoolConfigNode("showPlatforms", "mtrmap.ui.platforms",
                                () -> MTRMapConfig.INSTANCE.showPlatformLandmarks.get(),
                                v -> MTRMapConfig.INSTANCE.showPlatformLandmarks.set(v)));
                configNode.then(createBoolConfigNode("showDepots", "mtrmap.ui.depots",
                                () -> MTRMapConfig.INSTANCE.showDepotLandmarks.get(),
                                v -> MTRMapConfig.INSTANCE.showDepotLandmarks.set(v)));
                configNode.then(createBoolConfigNode("routeLines", "mtrmap.ui.routes",
                                () -> MTRMapConfig.INSTANCE.routeLinesEnabled.get(),
                                v -> MTRMapConfig.INSTANCE.routeLinesEnabled.set(v)));
                configNode.then(createBoolConfigNode("trackLines", "mtrmap.ui.tracks",
                                () -> MTRMapConfig.INSTANCE.trackLinesEnabled.get(),
                                v -> MTRMapConfig.INSTANCE.trackLinesEnabled.set(v)));

                configNode.then(createBoolConfigNode("trainCars", "mtrmap.ui.trains",
                                () -> MTRMapConfig.INSTANCE.trainCarsEnabled.get(),
                                v -> MTRMapConfig.INSTANCE.trainCarsEnabled.set(v)));

                rootNode.then(modeNode);
                rootNode.then(syncRoutesNode);
                rootNode.then(syncLandmarksNode);
                rootNode.then(testMarkerNode);
                rootNode.then(configNode);
                dispatcher.register(rootNode);
        }

        private static Component modeLabel(String value) { return Component.translatableWithFallback("mtrmap.mode."+value,value); }
        private static Component boolLabel(boolean value) { return Component.translatable("mtrmap.value."+(value?"enabled":"disabled")); }

        private static LiteralArgumentBuilder<CommandSourceStack> createBoolConfigNode(String configName,
                        String friendlyName, Supplier<Boolean> getValue, Consumer<Boolean> setValue) {
                LiteralArgumentBuilder<CommandSourceStack> cfgNode = Commands.literal(configName);
                cfgNode.then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                        setValue.accept(enabled);
                                        ctx.getSource().sendSuccess(
                                                        () -> Component.translatable("mtrmap.command.config_set", Component.translatable(friendlyName), boolLabel(enabled))
                                                                        .withStyle(ChatFormatting.GREEN),
                                                        true);
                                        return 1;
                                }))
                                .executes(ctx -> {
                                        ctx.getSource().sendSuccess(
                                                        () -> Component.translatable("mtrmap.command.config_current", Component.translatable(friendlyName), boolLabel(getValue.get()))
                                                                        .withStyle(ChatFormatting.AQUA),
                                                        false);
                                        return 1;
                                });
                return cfgNode;
        }
}

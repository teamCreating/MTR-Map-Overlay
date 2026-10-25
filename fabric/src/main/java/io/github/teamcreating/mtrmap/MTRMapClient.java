package io.github.teamcreating.mtrmap;

import io.github.teamcreating.mtrmap.integration.journeymap.JourneyMapIntegration;
import io.github.teamcreating.mtrmap.integration.xaero.LegacyXaeroWaypointCleanup;
import io.github.teamcreating.mtrmap.network.ClientNetworkSync;
import io.github.teamcreating.mtrmap.network.MTRNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class MTRMapClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !io.github.teamcreating.mtrmap.extension.OverlayUi.down(x, y, button));
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !io.github.teamcreating.mtrmap.extension.OverlayUi.up(x, y, button));
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, horizontal, vertical) -> !io.github.teamcreating.mtrmap.extension.OverlayUi.wheel(x, y, vertical));
            net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scan, modifiers) -> !io.github.teamcreating.mtrmap.extension.OverlayUi.key(key, modifiers));
        });

        MTRNetwork.registerClient();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                CommandRegistration.register(dispatcher));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientNetworkSync.onLoggingIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientNetworkSync.onLoggingOut());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientNetworkSync.onClientTick();
            if (MTRMap.isModLoaded("xaerominimap")) {
                LegacyXaeroWaypointCleanup.onClientTick();
            }
            JourneyMapIntegration.onClientTick();
        });
    }
}

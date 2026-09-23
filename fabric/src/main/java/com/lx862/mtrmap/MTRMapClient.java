package com.lx862.mtrmap;

import com.lx862.mtrmap.integration.journeymap.JourneyMapIntegration;
import com.lx862.mtrmap.integration.xaero.LegacyXaeroWaypointCleanup;
import com.lx862.mtrmap.network.ClientNetworkSync;
import com.lx862.mtrmap.network.MTRNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class MTRMapClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
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

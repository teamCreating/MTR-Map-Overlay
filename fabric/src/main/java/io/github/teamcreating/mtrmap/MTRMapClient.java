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

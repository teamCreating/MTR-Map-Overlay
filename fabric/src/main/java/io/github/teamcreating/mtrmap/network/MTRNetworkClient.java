package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric counterpart for the shared client snapshot requester. */
public final class MTRNetworkClient {

    private MTRNetworkClient() {
    }

    public static boolean canSendToServer() {
        return MTRNetwork.canSendToServer();
    }

    public static boolean canSendTrains() {
        return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(TrainStreamRequest.TYPE);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        MTRNetwork.sendToServer(payload);
    }
}

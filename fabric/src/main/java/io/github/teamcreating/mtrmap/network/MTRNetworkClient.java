package io.github.teamcreating.mtrmap.network;



/** Fabric counterpart for the shared client snapshot requester. */
public final class MTRNetworkClient {

    private MTRNetworkClient() {
    }

    public static boolean canSendTrains() { return MTRNetwork.canSendTrainsToServer(); }
    public static boolean canSendToServer() {
        return MTRNetwork.canSendToServer();
    }

    public static void sendToServer(MapPacket payload) {
        MTRNetwork.sendToServer(payload);
    }
}

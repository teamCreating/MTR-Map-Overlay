package com.lx862.mtrmap.network;
import net.minecraft.client.Minecraft;
/** Physical-client callbacks. Never executed on a dedicated server. */
public final class MTRNetworkClient {
    static void onChunk(NetworkSyncChunk packet) { ClientNetworkSync.onChunkReceived(packet); }
    static void onProbe(NetworkProbeResponse packet) { ClientNetworkSync.onProbeReceived(packet.hashes()); }
    static void onTrainChunk(TrainStreamChunk packet) { com.lx862.mtrmap.train.ClientTrainStream.chunk(packet); }
    static void onTrainStatus(TrainStreamStatus packet) { com.lx862.mtrmap.train.ClientTrainStream.status(packet); }
    public static boolean canSendTrains() { var c= Minecraft.getInstance().getConnection(); return c != null && MTRNetwork.TRAINS.isRemotePresent(c.getConnection()); }
    public static boolean canSendToServer() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && MTRNetwork.CHANNEL.isRemotePresent(listener.getConnection());
    }
    public static void sendToServer(MapPacket packet) { if (packet instanceof TrainStreamRequest) MTRNetwork.TRAINS.sendToServer(packet); else MTRNetwork.CHANNEL.sendToServer(packet); }
}

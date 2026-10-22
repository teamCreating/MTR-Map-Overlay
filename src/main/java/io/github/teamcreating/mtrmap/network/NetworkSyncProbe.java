package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * C2S: lightweight change-detection probe. The server replies with a
 * {@link NetworkProbeResponse} listing a content hash per dimension; the
 * client pulls full snapshots only for dimensions whose hash changed. This
 * makes periodic hot-update polling cost O(network size) on the server with
 * no transfer when nothing changed.
 */
public record NetworkSyncProbe() implements MapPacket {

    public static final NetworkSyncProbe INSTANCE = new NetworkSyncProbe();



    public static void write(FriendlyByteBuf buf, NetworkSyncProbe msg) {}
    public static NetworkSyncProbe read(FriendlyByteBuf buf) { return INSTANCE; }
}

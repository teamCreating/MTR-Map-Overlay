package com.lx862.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

public record TrainStreamStatus(long generation, String dimension, UUID epoch, int code, String message)
        implements MapPacket {
    public static final int ACCEPTED = 0, UNAVAILABLE = 1, ERROR = 2;
    public static void write(FriendlyByteBuf b, TrainStreamStatus m) { b.writeVarLong(m.generation); b.writeUtf(m.dimension, 256); b.writeUUID(m.epoch); b.writeVarInt(m.code); b.writeUtf(m.message, 256); }
    public static TrainStreamStatus read(FriendlyByteBuf b) { return new TrainStreamStatus(b.readVarLong(), b.readUtf(256), b.readUUID(), b.readVarInt(), b.readUtf(256)); }
}

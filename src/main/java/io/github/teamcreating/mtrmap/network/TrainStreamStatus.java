package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record TrainStreamStatus(long generation, String dimension, UUID epoch, int code, String message)
        implements CustomPacketPayload {
    public static final int ACCEPTED = 0, UNAVAILABLE = 1, ERROR = 2;
    public static final Type<TrainStreamStatus> TYPE = new Type<>(MTRNetwork.id("train_status_v1"));
    public static final StreamCodec<FriendlyByteBuf, TrainStreamStatus> STREAM_CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarLong(m.generation); b.writeUtf(m.dimension, 256); b.writeUUID(m.epoch); b.writeVarInt(m.code); b.writeUtf(m.message, 256); },
            b -> new TrainStreamStatus(b.readVarLong(), b.readUtf(256), b.readUUID(), b.readVarInt(), b.readUtf(256)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

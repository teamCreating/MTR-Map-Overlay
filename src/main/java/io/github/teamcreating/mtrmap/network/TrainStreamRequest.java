package io.github.teamcreating.mtrmap.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Subscribe/renew/unsubscribe, independent of static network-v8 support. */
public record TrainStreamRequest(long generation, String dimension, boolean active) implements CustomPacketPayload {
    public static final Type<TrainStreamRequest> TYPE = new Type<>(MTRNetwork.id("train_request_v1"));
    public static final StreamCodec<FriendlyByteBuf, TrainStreamRequest> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeVarLong(msg.generation); buf.writeUtf(msg.dimension, 256); buf.writeBoolean(msg.active); },
            buf -> new TrainStreamRequest(buf.readVarLong(), buf.readUtf(256), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

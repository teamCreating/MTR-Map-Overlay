package io.github.teamcreating.mtrmap.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** One-shot catalog (empty station), or renewable station interest. */
public record OverlayRequest(long generation, String dimension, String station, boolean active) implements CustomPacketPayload {
    public static final Type<OverlayRequest> TYPE = new Type<>(MTRNetwork.id("overlay_request_v1"));
    public static final StreamCodec<FriendlyByteBuf, OverlayRequest> STREAM_CODEC = StreamCodec.of(
            (b, m) -> { b.writeVarLong(m.generation); b.writeUtf(m.dimension, 256); b.writeUtf(m.station, 32); b.writeBoolean(m.active); },
            b -> new OverlayRequest(b.readVarLong(), b.readUtf(256), b.readUtf(32), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

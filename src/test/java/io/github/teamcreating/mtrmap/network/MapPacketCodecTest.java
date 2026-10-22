package io.github.teamcreating.mtrmap.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual encoders used by both Forge and Fabric transports. */
class MapPacketCodecTest {
    @Test
    void preservesAllDimensionsAndUnicodeFilters() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RequestNetworkSync.write(buf, RequestNetworkSync.ALL);
            RequestNetworkSync.write(buf, new RequestNetworkSync("mtr/港岛"));
            assertEquals(RequestNetworkSync.ALL, RequestNetworkSync.read(buf));
            assertEquals("mtr/港岛", RequestNetworkSync.read(buf).dimensionFilter());
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test
    void preservesProbeHashesAndMaximumChunk() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var probe = new NetworkProbeResponse(List.of(
                    new NetworkProbeResponse.DimensionHash("minecraft/overworld", Long.MIN_VALUE),
                    new NetworkProbeResponse.DimensionHash("mtr/港岛", Long.MAX_VALUE)));
            NetworkProbeResponse.write(buf, probe);
            byte[] bytes = new byte[NetworkChunkAssembler.CHUNK_SIZE];
            bytes[0] = 42;
            bytes[bytes.length - 1] = -42;
            var chunk = new NetworkSyncChunk(31, (short) 1, (short) 2, 991, bytes);
            NetworkSyncChunk.write(buf, chunk);
            assertEquals(probe, NetworkProbeResponse.read(buf));
            var decoded = NetworkSyncChunk.read(buf);
            assertEquals(chunk.transferId(), decoded.transferId());
            assertEquals(chunk.chunkIndex(), decoded.chunkIndex());
            assertEquals(chunk.totalChunks(), decoded.totalChunks());
            assertEquals(chunk.snapshotHash(), decoded.snapshotHash());
            assertArrayEquals(bytes, decoded.data());
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test
    void rejectsOversizedChunksAndImpossibleProbeCountsBeforeAllocation() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NetworkSyncChunk.write(buf, new NetworkSyncChunk(0, (short) 0, (short) 1, 0,
                    new byte[NetworkChunkAssembler.CHUNK_SIZE + 1]));
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> NetworkSyncChunk.read(buf));
            buf.clear();
            buf.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> NetworkProbeResponse.read(buf));
        } finally { buf.release(); }
    }
}

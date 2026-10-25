package io.github.teamcreating.mtrmap.extension;
import io.github.teamcreating.mtrmap.network.OverlayChunk;
import java.util.Arrays;
public final class OverlayAssembly {
    public final long createdNanos = System.nanoTime();
    private final byte[][] parts; private final int total; private int received;
    public OverlayAssembly(int count, int total) {
        if (total <= 0 || total > OverlayCodec.MAX_BYTES || count != (total + OverlayChunk.CHUNK - 1) / OverlayChunk.CHUNK)
            throw new IllegalArgumentException("Invalid overlay transfer size");
        this.total = total; parts = new byte[count][];
    }
    public boolean add(int index, int count, int total, byte[] bytes) {
        if (count != parts.length || total != this.total || index < 0 || index >= count || bytes.length != Math.min(OverlayChunk.CHUNK, total - index * OverlayChunk.CHUNK))
            throw new IllegalArgumentException("Invalid overlay part");
        if (parts[index] == null) { parts[index] = bytes.clone(); received++; }
        else if (!Arrays.equals(parts[index], bytes)) throw new IllegalArgumentException("Conflicting overlay part");
        return received == parts.length;
    }
    public byte[] assemble() {
        if (received != parts.length) throw new IllegalStateException("Incomplete overlay transfer");
        byte[] result = new byte[total]; int offset = 0;
        for (var part : parts) { System.arraycopy(part, 0, result, offset, part.length); offset += part.length; }
        return result;
    }
}

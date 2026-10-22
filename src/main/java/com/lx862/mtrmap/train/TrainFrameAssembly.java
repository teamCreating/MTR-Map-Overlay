package com.lx862.mtrmap.train;

import java.util.Arrays;

/** One atomic logical message; malformed or incomplete frames never delete vehicles. */
public final class TrainFrameAssembly {
    private final byte[][] parts;
    private final int total;
    private int received, bytes;
    public final long createdNanos;

    public TrainFrameAssembly(int count, int total, long now) {
        if (total <= 0 || total > TrainSnapshot.MAX_BYTES
                || count != (total + TrainSnapshot.CHUNK_BYTES - 1) / TrainSnapshot.CHUNK_BYTES)
            throw new IllegalArgumentException("Invalid train transfer size");
        this.total = total; this.parts = new byte[count][]; this.createdNanos = now;
    }

    public boolean add(int index, int count, int total, byte[] data) {
        if (count != parts.length || total != this.total || index < 0 || index >= count
                || data.length != Math.min(TrainSnapshot.CHUNK_BYTES, total - index * TrainSnapshot.CHUNK_BYTES))
            throw new IllegalArgumentException("Inconsistent train part");
        if (parts[index] != null) {
            if (!Arrays.equals(parts[index], data)) throw new IllegalArgumentException("Conflicting train part");
        } else { parts[index] = data.clone(); bytes += data.length; received++; }
        return received == count;
    }

    public byte[] assemble() {
        if (received != parts.length || bytes != total) throw new IllegalStateException("Incomplete train frame");
        byte[] result = new byte[total]; int offset = 0;
        for (byte[] part : parts) { System.arraycopy(part, 0, result, offset, part.length); offset += part.length; }
        return result;
    }
}

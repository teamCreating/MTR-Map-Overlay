package com.lx862.mtrmap.network;

/** Reassembles one bounded snapshot transfer without depending on Minecraft classes. */
final class NetworkChunkAssembler {

    static final int CHUNK_SIZE = 200_000;
    private static final int MAX_CHUNKS = 32_767;
    static final int MAX_BYTES = 256 * 1024 * 1024;

    private final byte[][] chunks;
    private final long snapshotHash;
    private final long createdMillis = System.currentTimeMillis();
    private int receivedCount;
    private int receivedBytes;

    NetworkChunkAssembler(int totalChunks, long snapshotHash) {
        if (totalChunks <= 0 || totalChunks > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid snapshot chunk count: " + totalChunks);
        }
        this.chunks = new byte[totalChunks][];
        this.snapshotHash = snapshotHash;
    }

    boolean add(int index, int totalChunks, long hash, byte[] data) {
        if (totalChunks != chunks.length || hash != snapshotHash || index < 0 || index >= chunks.length
                || data == null || data.length == 0 || data.length > CHUNK_SIZE) {
            throw new IllegalArgumentException("Inconsistent snapshot chunk metadata");
        }
        final byte[] previous = chunks[index];
        if (previous != null) {
            if (!java.util.Arrays.equals(previous, data)) {
                throw new IllegalArgumentException("Conflicting duplicate snapshot chunk");
            }
            return receivedCount == chunks.length;
        }
        if (data.length > MAX_BYTES - receivedBytes) {
            throw new IllegalArgumentException("Snapshot exceeds transfer size limit");
        }
        chunks[index] = data;
        receivedBytes += data.length;
        receivedCount++;
        return receivedCount == chunks.length;
    }

    byte[] assemble() {
        if (receivedCount != chunks.length) {
            throw new IllegalStateException("Snapshot transfer is incomplete");
        }
        final byte[] result = new byte[receivedBytes];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    long ageMillis() {
        return System.currentTimeMillis() - createdMillis;
    }

    long snapshotHash() {
        return snapshotHash;
    }
}

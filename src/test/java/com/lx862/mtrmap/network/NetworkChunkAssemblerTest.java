package com.lx862.mtrmap.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkChunkAssemblerTest {

    @Test
    void reassemblesOutOfOrderChunksAndIgnoresIdenticalDuplicates() {
        final NetworkChunkAssembler assembler = new NetworkChunkAssembler(3, 42);

        assertFalse(assembler.add(2, 3, 42, new byte[]{3}));
        assertFalse(assembler.add(0, 3, 42, new byte[]{1}));
        assertFalse(assembler.add(0, 3, 42, new byte[]{1}));
        assertTrue(assembler.add(1, 3, 42, new byte[]{2}));
        assertArrayEquals(new byte[]{1, 2, 3}, assembler.assemble());
    }

    @Test
    void rejectsConflictingOrOutOfRangeChunks() {
        final NetworkChunkAssembler assembler = new NetworkChunkAssembler(2, 42);

        assertThrows(IllegalArgumentException.class, () -> assembler.add(-1, 2, 42, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.add(2, 2, 42, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.add(0, 3, 42, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.add(0, 2, 43, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.add(0, 2, 42, new byte[0]));
        assertFalse(assembler.add(0, 2, 42, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> assembler.add(0, 2, 42, new byte[]{9}));
    }
}

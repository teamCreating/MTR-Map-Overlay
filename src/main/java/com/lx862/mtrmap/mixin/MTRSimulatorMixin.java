package com.lx862.mtrmap.mixin;

import org.mtr.core.data.Data;
import org.mtr.core.simulation.Simulator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Simulator.class, remap = false)
public class MTRSimulatorMixin extends Data {

    @Override
    public void sync() {
        super.sync();
        // Reverted: MTRMap is a client-side mapper. We cannot inject code to
        // broadcast packets
        // from the server because players use this mod to connect to external
        // Multiplayer servers
        // that do not have MTRMap installed. Doing so causes the simulation to
        // crash or desync.
    }
}

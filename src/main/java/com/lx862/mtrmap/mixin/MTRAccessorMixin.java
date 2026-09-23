package com.lx862.mtrmap.mixin;

import org.mtr.core.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = org.mtr.MTR.class, remap = false)
public interface MTRAccessorMixin {
    @Accessor("main")
    static Main getMain() {
        throw new AssertionError();
    }
}

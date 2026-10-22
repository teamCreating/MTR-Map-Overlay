package io.github.teamcreating.mtrmap.mixin;

import org.mtr.core.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = org.mtr.mod.Init.class, remap = false)
public interface MTRAccessorMixin {
    @Accessor("main")
    static Main getMain() {
        throw new AssertionError();
    }
}

package com.lx862.mtrmap.mixin;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Core 4.0 exposes no public vehicle iterator. Read only on the Simulator thread. */
@Mixin(value = Siding.class, remap = false)
public interface SidingVehiclesAccessor {
 @Accessor("vehicles") ObjectArraySet<Vehicle> mtrmap$getVehicles();
}

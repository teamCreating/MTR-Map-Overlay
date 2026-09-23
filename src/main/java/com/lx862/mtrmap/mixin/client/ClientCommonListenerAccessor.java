package com.lx862.mtrmap.mixin.client;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the NeoForge connection type (vanilla / other / neoforge) from the
 * client's play listener; used to decide whether full-network sync requests
 * can be sent at all.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonListenerAccessor {

    @Accessor("connectionType")
    ConnectionType mtrmap$getConnectionType();
}

package io.github.teamcreating.mtrmap.mixin.client;

import io.github.teamcreating.mtrmap.network.ClientPacketDispatch;
import io.netty.channel.Channel;
import io.netty.channel.socket.SocketChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge47's sender toggles AUTO_READ before handing its write to Netty. Serialize that whole operation. */
@Mixin(Connection.class)
public abstract class ForgeClientConnectionMixin {
    @Shadow private Channel channel;
    @Shadow private void sendPacket(Packet<?> packet, PacketSendListener listener) { throw new AssertionError(); }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void mtrmap$sendOnOwningLoop(Packet<?> packet, PacketSendListener listener, CallbackInfo callback) {
        if (!(channel instanceof SocketChannel) || ((Connection) (Object) this).getDirection() != PacketFlow.CLIENTBOUND
                || channel.eventLoop().inEventLoop()) return;
        // Leave invalid-packet validation on its original caller/exception path.
        if (ConnectionProtocol.getProtocolForPacket(packet) == null) return;
        if (ClientPacketDispatch.enqueueIfNeeded(channel, () -> sendPacket(packet, listener))) callback.cancel();
    }
}

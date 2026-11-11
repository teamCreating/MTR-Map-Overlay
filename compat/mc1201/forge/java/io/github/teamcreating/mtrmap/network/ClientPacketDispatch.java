package io.github.teamcreating.mtrmap.network;

import io.netty.channel.Channel;

/** Keep the complete protocol/read/write operation on the channel's owning loop. */
public final class ClientPacketDispatch {
    private ClientPacketDispatch() { }

    public static boolean enqueueIfNeeded(Channel channel, Runnable operation) {
        if (channel.eventLoop().inEventLoop()) return false;
        channel.eventLoop().execute(operation);
        return true;
    }
}

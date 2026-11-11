package io.github.teamcreating.mtrmap.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DefaultSocketChannelConfig;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real NIO sockets: distinguish the configuration flag from actual read-interest registration. */
@Timeout(15)
class NativeReadInterestTest {
    @Test
    void offLoopClearCanRemoveAnAlreadyReenabledReadInterest() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var client = fixture.client;
            client.pauseOffLoopClear = true;
            var producer = Executors.newSingleThreadExecutor();
            try {
                Future<?> disable = producer.submit(() -> client.config().setAutoRead(false));
                assertTrue(client.disabled.await(3, TimeUnit.SECONDS));
                client.eventLoop().submit(() -> client.config().setAutoRead(true)).sync();
                client.release.countDown();
                disable.get(3, TimeUnit.SECONDS);
                client.eventLoop().submit(() -> {}).sync();
                assertTrue(client.config().isAutoRead());
                assertEquals(0, client.eventLoop().submit(client::readInterest).sync().getNow());
                fixture.peer.getOutputStream().write(42);
                fixture.peer.getOutputStream().flush();
                assertThrows(TimeoutException.class, () -> fixture.received.get(200, TimeUnit.MILLISECONDS));
            } finally {
                client.release.countDown();
                producer.shutdownNow();
            }
        }
    }

    @Test
    void serializedLoopTransitionsRetainReadInterestAndReceiveActualData() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var transitioned = new CompletableFuture<Void>();
            assertTrue(ClientPacketDispatch.enqueueIfNeeded(fixture.client, () -> {
                assertFalse(ClientPacketDispatch.enqueueIfNeeded(fixture.client, () -> fail("Must not enqueue recursively on the owner")));
                fixture.client.config().setAutoRead(false);
                fixture.client.config().setAutoRead(true);
                transitioned.complete(null);
            }));
            transitioned.get(3, TimeUnit.SECONDS);
            assertTrue(fixture.client.config().isAutoRead());
            assertEquals(SelectionKey.OP_READ, fixture.client.eventLoop().submit(fixture.client::readInterest).sync().getNow());
            fixture.peer.getOutputStream().write(42);
            fixture.peer.getOutputStream().flush();
            assertEquals(42, fixture.received.get(3, TimeUnit.SECONDS));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NioEventLoopGroup group = new NioEventLoopGroup(1);
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final CompletableFuture<Integer> received = new CompletableFuture<>();
        final ControlledChannel client = new ControlledChannel();
        final Socket peer;
        Fixture() throws Exception {
            new Bootstrap().group(group).channelFactory(() -> client).handler(new SimpleChannelInboundHandler<ByteBuf>() {
                @Override protected void channelRead0(ChannelHandlerContext context, ByteBuf bytes) { received.complete((int) bytes.readUnsignedByte()); }
                @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) { received.completeExceptionally(failure); }
            }).connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort())).sync();
            peer = server.accept();
            client.eventLoop().submit(() -> {}).sync();
        }
        @Override public void close() throws Exception {
            client.release.countDown();
            client.close().sync();
            peer.close(); server.close();
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).sync();
        }
    }

    private static final class ControlledChannel extends NioSocketChannel {
        final CountDownLatch disabled = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean pauseOffLoopClear;
        final SocketChannelConfig controlled = new DefaultSocketChannelConfig(this, javaChannel().socket()) {
            @Override protected void autoReadCleared() {
                if (pauseOffLoopClear && !eventLoop().inEventLoop()) {
                    disabled.countDown();
                    try {
                        if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Test clear barrier timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }
                clearReadPending();
            }
        };
        @Override public SocketChannelConfig config() { return controlled == null ? super.config() : controlled; }
        int readInterest() { return selectionKey().interestOps() & SelectionKey.OP_READ; }
    }
}

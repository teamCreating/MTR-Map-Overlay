package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.mixin.MainAccessorMixin;
import io.github.teamcreating.mtrmap.mixin.MTRAccessorMixin;
import io.github.teamcreating.mtrmap.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.simulation.Simulator;
import java.util.*;
import java.util.concurrent.*;

/** Shared station capture/encoding, renewable interests, bounded sources and paced outbound chunks. */
public final class ServerOverlayService {
    private record Key(String dimension, String station) {}
    private record Encoded(long sequence, byte[] bytes) {}
    private static final class Source {
        final Simulator simulator;
        boolean pending; long due;
        Encoded latest;
        Source(Simulator simulator) { this.simulator = simulator; }
    }
    private static final class Interest {
        final ServerPlayer player; final long generation; final Key key;
        long renewed, sent; int offset; Encoded active;
        Interest(ServerPlayer player, OverlayRequest request, Key key, long now) {
            this.player = player; generation = request.generation(); this.key = key; renewed = now;
        }
    }
    private static volatile MinecraftServer owner;
    private static UUID epoch;
    private static long sequence;
    private static int nextPlayer;
    private static final Map<UUID, Map<Key, Interest>> interests = new HashMap<>();
    private static final Map<UUID, Long> lastRequest = new HashMap<>();
    private static final Map<Key, Source> sources = new HashMap<>();
    private static ThreadPoolExecutor encoder;
    private ServerOverlayService() {}
    public static void request(ServerPlayer player, OverlayRequest request) {
        var server = player.getServer();
        if (server == null || !MTRNetwork.canSendOverlay(player) || request.generation() <= 0 || !request.dimension().matches("[a-z0-9_.-]+/[a-z0-9_./-]+")) return;
        if (!request.station().isEmpty()) try { Long.parseUnsignedLong(request.station(), 16); } catch (NumberFormatException e) { return; }
        if (owner != server) {
            stop(); owner = server; epoch = UUID.randomUUID();
            encoder = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), r -> {
                var t = new Thread(r, "MTRMap-overlay-encoder"); t.setDaemon(true); return t;
            });
        }
        var key = new Key(request.dimension(), request.station());
        var byPlayer = interests.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        var old = byPlayer.get(key);
        if (old != null && request.generation() < old.generation) return;
        if (!request.active()) { if (old != null && old.generation == request.generation()) byPlayer.remove(key); return; }
        long now = System.nanoTime();
        if (old != null && old.generation == request.generation()) { old.renewed = now; return; }
        if (byPlayer.size() >= 8 || sources.size() >= 128 && !sources.containsKey(key)) return;
        var previous = lastRequest.get(player.getUUID());
        if (previous != null && now - previous < 25_000_000L) return;
        lastRequest.put(player.getUUID(), now);
        byPlayer.put(key, new Interest(player, request, key, now));
        var source = sources.get(key);
        if (key.station.isEmpty() && source != null && !source.pending && now > source.due - 25_000_000_000L) {
            source.due = 0; source.latest = null;
        }
    }
    private static Simulator simulator(String dimension) {
        var main = MTRAccessorMixin.getMain();
        if (main != null) for (var sim : ((MainAccessorMixin) main).getSimulators()) if (sim.dimension.equals(dimension)) return sim;
        return null;
    }
    public static void tick(MinecraftServer server) {
        if (owner != server) return;
        long now = System.nanoTime();
        interests.values().forEach(map -> map.values().removeIf(i -> i.player.hasDisconnected() || now - i.renewed > 5_000_000_000L));
        interests.values().removeIf(Map::isEmpty);
        var keys = new HashSet<Key>(); interests.values().forEach(map -> keys.addAll(map.keySet()));
        sources.keySet().retainAll(keys);
        int queued = 0;
        for (var key : keys) {
            var sim = simulator(key.dimension);
            if (sim == null) continue;
            var source = sources.get(key);
            if (source == null || source.simulator != sim) { source = new Source(sim); sources.put(key, source); }
            if (!source.pending && now >= source.due && queued++ < 4 && encoder.getQueue().remainingCapacity() > 0) {
                source.pending = true; source.due = now + (key.station.isEmpty() ? 30_000_000_000L : 1_000_000_000L);
                Source capture = source; UUID captureEpoch = epoch; long serial = ++sequence;
                sim.run(() -> {
                    try {
                        var world = new WorldRef(captureEpoch, key.dimension.replaceFirst("/", ":"));
                        Record data = key.station.isEmpty() ? CoreOverlayAdapter.catalog(sim, world, ServerNetworkCollector.computeHash(sim)) : CoreOverlayAdapter.board(sim, world, key.station, 0);
                        var executor = encoder;
                        if (owner != server || executor == null) return;
                        executor.execute(() -> {
                            try {
                                var encoded = new Encoded(serial, OverlayCodec.encode(data));
                                server.execute(() -> { if (owner == server && sources.get(key) == capture) { capture.latest = encoded; capture.pending = false; } });
                            } catch (Exception e) { fail(server, key, capture); }
                        });
                    } catch (Exception e) { fail(server, key, capture); }
                });
            }
        }
        int budget = 256000;
        var players = new ArrayList<>(interests.values());
        int start = players.isEmpty() ? 0 : Math.floorMod(nextPlayer++, players.size());
        for (int p = 0; p < players.size(); p++) {
            var map = players.get((start + p) % players.size());
            int parts = 0;
            for (var interest : map.values()) {
                var source = sources.get(interest.key);
                if (source == null) continue;
                if (interest.active == null && source.latest != null && source.latest.sequence > interest.sent) interest.active = source.latest;
                while (interest.active != null && parts < 2 && budget >= OverlayChunk.CHUNK) {
                    var data = interest.active;
                    int length = Math.min(OverlayChunk.CHUNK, data.bytes.length - interest.offset);
                    MTRNetwork.sendToPlayer(interest.player, new OverlayChunk(interest.generation, interest.key.dimension, interest.key.station,
                            data.sequence, interest.offset / OverlayChunk.CHUNK, (data.bytes.length + OverlayChunk.CHUNK - 1) / OverlayChunk.CHUNK,
                            data.bytes.length, Arrays.copyOfRange(data.bytes, interest.offset, interest.offset + length)));
                    interest.offset += length; parts++; budget -= length;
                    if (interest.offset == data.bytes.length) { interest.sent = data.sequence; interest.active = null; interest.offset = 0; }
                }
            }
        }
    }
    private static void fail(MinecraftServer server, Key key, Source source) {
        server.execute(() -> { if (owner == server && sources.get(key) == source) { source.pending = false; source.due = System.nanoTime() + 5_000_000_000L; } });
    }
    public static void disconnect(UUID player) { interests.remove(player); lastRequest.remove(player); }
    public static void stop() {
        owner = null; epoch = null; sources.clear(); interests.clear(); lastRequest.clear();
        if (encoder != null) { encoder.shutdownNow(); encoder = null; }
    }
}

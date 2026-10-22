package io.github.teamcreating.mtrmap.train;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.mixin.MainAccessorMixin;
import io.github.teamcreating.mtrmap.mixin.MTRAccessorMixin;
import io.github.teamcreating.mtrmap.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.mtr.core.simulation.Simulator;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static io.github.teamcreating.mtrmap.train.TrainSnapshot.*;

/** Server-thread subscriptions -> one Core-thread capture -> bounded encoding -> paced transport. */
public final class ServerTrainStream {
    private static final long LEASE = 5_000_000_000L, PERIOD = 250_000_000L;
    private static final double BYTES_PER_SECOND = 2 * 1024 * 1024;
    private static volatile MinecraftServer owner;
    private static ThreadPoolExecutor encoder;
    private static final Map<UUID, Subscription> subscriptions = new HashMap<>();
    private static final Map<String, Source> sources = new HashMap<>();
    private static final Map<UUID, Long> requestTimes = new HashMap<>();
    private ServerTrainStream() { }

    private static final class Source {
        final Simulator simulator;
        final UUID epoch = UUID.randomUUID();
        final CoreTrainAdapter adapter = new CoreTrainAdapter();
        final Map<Integer, byte[]> encodedDefinitions = new HashMap<>(); // encoding thread only
        boolean pending;
        volatile boolean cancelled;
        long nextSample, lastWarning, lastSourceCheck;
        Source(Simulator simulator) { this.simulator = simulator; }
    }
    private record Encoded(long sequence, long captureMillis, byte[] frame, Map<Integer, byte[]> definitions) { }
    private record Message(byte kind, long sequence, byte[] bytes) { }
    private static final class Outbound {
        final Encoded snapshot;
        final List<Message> messages = new ArrayList<>();
        int message, part;
        Outbound(Encoded snapshot, Set<Integer> known) {
            this.snapshot = snapshot;
            var missing = new ArrayList<byte[]>();
            snapshot.definitions.forEach((id, data) -> { if (!known.contains(id)) missing.add(data); });
            if (!missing.isEmpty()) {
                try { messages.add(new Message(DEFINITIONS, snapshot.sequence, TrainFrameCodec.mergeDefinitions(missing))); }
                catch (IOException e) { throw new IllegalArgumentException("Cannot assemble train definitions", e); }
            }
            messages.add(new Message(FRAME, snapshot.sequence, snapshot.frame));
        }
    }
    private static final class Subscription {
        final ServerPlayer player;
        final long generation;
        final String dimension;
        final Source source;
        final Set<Integer> known = new HashSet<>();
        long renewed, lastRequest, tokenTime;
        double tokens = CHUNK_BYTES * 2.0;
        Outbound active;
        Encoded latest;
        Subscription(ServerPlayer p, TrainStreamRequest request, Source source, long now) {
            player = p; generation = request.generation(); dimension = request.dimension(); this.source = source;
            renewed = lastRequest = tokenTime = now;
        }
    }

    private static void initialize(MinecraftServer server) {
        if (owner == server) return;
        stop(); owner = server;
        encoder = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2), runnable -> {
            Thread thread = new Thread(runnable, "MTRMap-train-encoder"); thread.setDaemon(true); return thread;
        });
    }

    public static void request(ServerPlayer player, TrainStreamRequest request) {
        var server = player.getServer();
        if (server == null || !MTRNetwork.canSendTrains(player) || request.generation() <= 0) return;
        initialize(server);
        long now = System.nanoTime();
        Long requestedAt = requestTimes.put(player.getUUID(), now);
        if (requestedAt != null && now - requestedAt < 100_000_000L) return;
        var old = subscriptions.get(player.getUUID());
        if (old != null && request.generation() < old.generation) return;
        if (old != null && request.generation() == old.generation) {
            if (!old.dimension.equals(request.dimension())) return;
            if (!request.active()) { subscriptions.remove(player.getUUID()); return; }
            old.renewed = now; return;
        }
        if (!request.active()) return;
        if (old != null && now - old.lastRequest < 200_000_000L) return;
        Simulator simulator = findSimulator(request.dimension());
        if (simulator == null) {
            MTRNetwork.sendToPlayer(player, new TrainStreamStatus(request.generation(), request.dimension(), new UUID(0, 0),
                    TrainStreamStatus.UNAVAILABLE, "No simulator for this dimension"));
            return;
        }
        var source = sources.get(request.dimension());
        if (source == null || source.simulator != simulator) {
            source = new Source(simulator); sources.put(request.dimension(), source);
        }
        var sub = new Subscription(player, request, source, now);
        subscriptions.put(player.getUUID(), sub);
        status(sub, TrainStreamStatus.ACCEPTED, "Whole-dimension stream; target 4 Hz");
    }

    private static Simulator findSimulator(String dimension) {
        try {
            var main = MTRAccessorMixin.getMain();
            if (main != null) for (var simulator : ((MainAccessorMixin) main).getSimulators())
                if (simulator.dimension.equals(dimension)) return simulator;
        } catch (RuntimeException e) { MTRMap.LOGGER.debug("[MTRMap] Train source not ready", e); }
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (owner != server) return;
        long now = System.nanoTime();
        subscriptions.values().removeIf(sub -> now - sub.renewed > LEASE || sub.player.hasDisconnected());
        sources.entrySet().removeIf(entry -> {
            if (subscriptions.values().stream().anyMatch(s -> s.source == entry.getValue())) return false;
            entry.getValue().cancelled = true; return true;
        });
        for (var entry : List.copyOf(sources.entrySet())) {
            var source = entry.getValue();
            if (now - source.lastSourceCheck >= 1_000_000_000L) {
                source.lastSourceCheck = now;
                if (findSimulator(entry.getKey()) != source.simulator) {
                    for (var sub : subscriptions.values()) if (sub.source == source)
                        status(sub, TrainStreamStatus.UNAVAILABLE, "Simulator changed; resubscribe");
                    subscriptions.values().removeIf(sub -> sub.source == source);
                    source.cancelled = true;
                    sources.remove(entry.getKey()); continue;
                }
            }
            if (!source.pending && now >= source.nextSample) {
                source.pending = true; source.nextSample = now + PERIOD;
                String dimension = entry.getKey();
                source.simulator.run(() -> {
                    if (owner != server || source.cancelled) return;
                    try {
                        var capture = source.adapter.capture(source.simulator);
                        var executor = encoder;
                        if (owner != server || executor == null) return;
                        executor.execute(() -> {
                            if (owner != server || source.cancelled) return;
                            try {
                                byte[] frame = TrainFrameCodec.frame(capture.frame());
                                source.encodedDefinitions.keySet().retainAll(capture.definitions().keySet());
                                int total = 0;
                                for (var definition : capture.definitions().entrySet()) {
                                    if (!source.encodedDefinitions.containsKey(definition.getKey()))
                                        source.encodedDefinitions.put(definition.getKey(), TrainFrameCodec.definitions(
                                                List.of(new NamedDefinition(definition.getKey(), definition.getValue()))));
                                    if ((total += source.encodedDefinitions.get(definition.getKey()).length) > MAX_BYTES)
                                        throw new IOException("Global train definitions exceed capacity");
                                }
                                var encoded = new Encoded(capture.frame().sequence(), capture.frame().captureMillis(), frame, Map.copyOf(source.encodedDefinitions));
                                if (owner == server) server.execute(() -> {
                                    if (owner != server || sources.get(dimension) != source) return;
                                    source.pending = false;
                                    for (var sub : subscriptions.values()) if (sub.source == source) sub.latest = encoded;
                                });
                            } catch (Exception e) { failed(server, dimension, source, e); }
                        });
                    } catch (Exception e) { failed(server, dimension, source, e); }
                });
            }
        }
        int serverBudget = 4 * 1024 * 1024;
        for (var sub : subscriptions.values()) {
            sub.tokens = Math.min(CHUNK_BYTES * 2.0, sub.tokens + (now - sub.tokenTime) / 1.0E9 * BYTES_PER_SECOND);
            sub.tokenTime = now;
            if (sub.active == null && sub.latest != null) { sub.active = new Outbound(sub.latest, sub.known); sub.latest = null; }
            int sentParts = 0;
            while (sub.active != null && sentParts < 2) {
                var active = sub.active; var message = active.messages.get(active.message);
                int offset = active.part * CHUNK_BYTES, length = Math.min(CHUNK_BYTES, message.bytes.length - offset);
                if (length > sub.tokens || length > serverBudget) break;
                int count = (message.bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
                MTRNetwork.sendToPlayer(sub.player, new TrainStreamChunk(sub.generation, sub.dimension, sub.source.epoch,
                        message.sequence, Math.max(0, now / 1_000_000L - active.snapshot.captureMillis), message.kind, active.part, count, message.bytes.length,
                        Arrays.copyOfRange(message.bytes, offset, offset + length)));
                sub.tokens -= length; serverBudget -= length; sentParts++;
                if (++active.part == count) {
                    active.part = 0;
                    if (++active.message == active.messages.size()) {
                        sub.known.clear(); sub.known.addAll(active.snapshot.definitions.keySet()); sub.active = null;
                    }
                }
            }
        }
    }

    private static void failed(MinecraftServer server, String dimension, Source source, Exception error) {
        if (owner != server) return;
        server.execute(() -> {
            if (owner != server || sources.get(dimension) != source) return;
            source.pending = false;
            long now = System.nanoTime();
            if (now - source.lastWarning > LEASE) {
                source.lastWarning = now;
                MTRMap.LOGGER.warn("[MTRMap] Global train capture failed for {}", dimension, error);
                for (var sub : subscriptions.values()) if (sub.source == source) status(sub, TrainStreamStatus.ERROR, "Source/encoding failed; retaining last measured frame");
            }
        });
    }
    private static void status(Subscription sub, int code, String message) {
        MTRNetwork.sendToPlayer(sub.player, new TrainStreamStatus(sub.generation, sub.dimension, sub.source.epoch, code, message));
    }
    public static void disconnect(UUID player) { subscriptions.remove(player); requestTimes.remove(player); }
    public static void stop() {
        owner = null; subscriptions.clear(); sources.values().forEach(source -> source.cancelled = true);
        sources.clear(); requestTimes.clear();
        if (encoder != null) { encoder.shutdownNow(); encoder = null; }
    }
}

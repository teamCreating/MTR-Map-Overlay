package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.api.MtrMapApi;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.mapdata.MapDataCache;
import io.github.teamcreating.mtrmap.network.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Client-thread facade. Receiving snapshots never creates a local simulation. */
public final class ClientOverlayRuntime implements MtrMapApi.Client {
    public static final ClientOverlayRuntime INSTANCE = new ClientOverlayRuntime();
    private record Key(String dimension, String station) {}
    private static final class Query {
        final long generation; final long started = System.nanoTime();
        long requestAt, sequence, transferSequence;
        OverlayAssembly transfer;
        Query(long generation) { this.generation = generation; }
    }
    private final Map<Key, Query> queries = new LinkedHashMap<>();
    private final Map<String, Catalog> catalogs = new HashMap<>();
    private final Map<Key, List<Watch>> watches = new LinkedHashMap<>();
    private long generation;
    private final ViewRegistry views = new ViewRegistry();
    public record Navigation(String owner, String name, Consumer<NavigationTarget> handler) {}
    private final Map<String, Navigation> navigation = new LinkedHashMap<>();
    public List<Navigation> navigation() { thread(); return List.copyOf(navigation.values()); }
    public Registration registerNavigation(String owner, String displayName, Consumer<NavigationTarget> handler) {
        thread();
        if (owner == null || !owner.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || displayName == null || displayName.isBlank() || displayName.length() > 80 || navigation.size() >= 8 || navigation.containsKey(owner))
            throw new IllegalArgumentException("Invalid or duplicate navigation provider / capacity reached");
        var value = new Navigation(owner, displayName, Objects.requireNonNull(handler)); navigation.put(owner, value);
        // Providers live for the client/mod lifetime; world/session data is supplied only on an explicit user click.
        return () -> { thread(); navigation.remove(owner, value); };
    }
    public void navigate(Navigation provider, WorldRef world, String station) {
        thread(); if (navigation.get(provider.owner()) != provider || catalog(world).status() != Status.READY) return;
        try { provider.handler().accept(new NavigationTarget(world, station)); }
        catch (RuntimeException e) { MTRMap.LOGGER.warn("[MTRMap] Navigation provider failed: {}", provider.owner(), e); }
    }
    private boolean clearing;
    private final ThreadPoolExecutor decoder = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2), r -> { var t = new Thread(r, "MTRMap-overlay-decoder"); t.setDaemon(true); return t; });
    private ClientOverlayRuntime() { MtrMapApi.install(this); }
    private static void thread() { if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("MTR Map API requires client thread"); }
    public ViewRegistry views() { return views; }
    private static String key(String dimension) { return dimension.replaceFirst(":", "/"); }
    public Optional<WorldRef> world(String dimensionId) {
        thread(); if (Minecraft.getInstance().getConnection() == null) return Optional.empty();
        String dimension = key(dimensionId);
        if (!dimension.matches("[a-z0-9_.-]+/[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid dimension");
        Catalog catalog = catalogs.get(dimension);
        if (catalog == null && MTRNetworkClient.canSendOverlay() && queries.size() < 8) queries.computeIfAbsent(new Key(dimension, ""), k -> new Query(++generation));
        return catalog == null ? Optional.empty() : Optional.of(catalog.world());
    }
    public Catalog catalog(WorldRef world) {
        thread(); Catalog value = catalogs.get(key(world.dimensionId()));
        if (value == null || !value.world().equals(world)) return new Catalog(world, 0, 0, Status.STALE, List.of(), List.of(), List.of());
        if (MapDataCache.hasServerData(key(world.dimensionId())) && MapDataCache.get(key(world.dimensionId())).version != value.baseStaticRevision())
            return new Catalog(value.world(), value.revision(), value.baseStaticRevision(), Status.STALE, value.stations(), value.platforms(), value.routes());
        return value;
    }
    public List<Track> tracks(WorldRef world, Set<String> railIds) {
        thread(); if (railIds.size() > 4096) throw new IllegalArgumentException("Too many rails");
        if (catalog(world).status() != Status.READY) return List.of();
        return MapDataCache.get(key(world.dimensionId())).tracks.stream().filter(t -> railIds.contains(t.id))
                .map(t -> new Track(t.id, t.points.stream().map(p -> new Point(p[0], p.length >= 3 ? p[2] : Double.NaN, p[1])).toList(), t.curve == null ? Double.NaN : t.curve.length())).toList();
    }
    public CompletionStage<Resolution> resolve(WorldRef world, long revision, List<RouteSection> sections) {
        thread(); var catalog = catalog(world);
        return CompletableFuture.completedFuture(catalog.status() != Status.READY || catalog.revision() != revision
                ? new Resolution(Status.STALE, catalog.revision(), List.of(), List.of()) : SectionResolver.resolve(catalog, List.copyOf(sections)));
    }
    public ViewSession openView(String owner, String displayName, Purpose purpose, int priority) {
        thread(); var delegate = views.open(owner, displayName, purpose, priority, this::catalog);
        return new ViewSession() {
            public CompletionStage<Status> replace(ViewRequest request) {
                thread(); var tracks = new HashMap<String, io.github.teamcreating.mtrmap.mapdata.MapTrack>();
                MapDataCache.get(key(request.world().dimensionId())).tracks.forEach(t -> tracks.put(t.id, t));
                var perRail = new HashMap<String, Integer>();
                java.util.stream.Stream.concat(request.spans().stream(), request.spanStyles().stream().map(SpanStyle::span))
                        .forEach(s -> perRail.merge(s.rail().railId(), 1, Integer::sum));
                if (perRail.values().stream().anyMatch(n -> n > 256)) return CompletableFuture.completedFuture(Status.INVALID);
                if (java.util.stream.Stream.concat(request.spans().stream(), request.spanStyles().stream().map(SpanStyle::span))
                        .anyMatch(s -> !tracks.containsKey(s.rail().railId()) || tracks.get(s.rail().railId()).curve == null))
                    return CompletableFuture.completedFuture(Status.UNAVAILABLE);
                return delegate.replace(request);
            }
            public void clear() { thread(); delegate.clear(); }
            public void close() { thread(); delegate.close(); }
        };
    }
    public BoardSubscription watchStation(WorldRef world, String stationId, Consumer<Board> listener) {
        thread(); stationId = Long.toUnsignedString(Long.parseUnsignedLong(stationId, 16), 16); Objects.requireNonNull(listener);
        var key = new Key(key(world.dimensionId()), stationId);
        if (!watches.containsKey(key) && watches.size() >= 6) throw new IllegalStateException("Station interest capacity reached");
        var watch = new Watch(world, key, listener); watches.computeIfAbsent(key, ignored -> new ArrayList<>()).add(watch);
        if (catalog(world).status() == Status.STALE) watch.value = empty(world, key, 0, Status.STALE);
        else if (!MTRNetworkClient.canSendOverlay()) watch.value = empty(world, key, 0, Status.UNSUPPORTED);
        else queries.computeIfAbsent(key, ignored -> new Query(++generation));
        return watch;
    }
    private static Board empty(WorldRef world, Key key, long gen, Status status) { return new Board(world, key.station, gen, status, 0, 0, List.of(), List.of()); }
    private final class Watch implements BoardSubscription {
        final WorldRef world; final Key key; final Consumer<Board> listener; Board value; long received; boolean closed;
        Watch(WorldRef world, Key key, Consumer<Board> listener) { this.world = world; this.key = key; this.listener = listener; value = empty(world, key, 0, Status.LOADING); }
        public Board latest() { thread(); return value; }
        void accept(Board board) { if (closed) return; value = board; received = System.nanoTime(); try { listener.accept(board); } catch (RuntimeException e) { MTRMap.LOGGER.warn("[MTRMap] Station API listener failed", e); } }
        public void close() {
            thread(); if (closed) return; closed = true;
            var list = watches.get(key); if (list != null) { list.remove(this); if (list.isEmpty()) { watches.remove(key); cancel(key); } }
        }
    }
    private void cancel(Key key) {
        Query query = queries.remove(key);
        if (!clearing && query != null && MTRNetworkClient.canSendOverlay()) MTRNetworkClient.sendToServer(new OverlayRequest(query.generation, key.dimension, key.station, false));
    }
    public void tick() {
        thread(); OverlayUi.tick();
        if (Minecraft.getInstance().getConnection() == null) return;
        long now = System.nanoTime(); int sent = 0;
        for (var entry : List.copyOf(queries.entrySet())) {
            var query = entry.getValue(); var key = entry.getKey();
            if (query.transfer != null && now - query.transfer.createdNanos > 30_000_000_000L) query.transfer = null;
            if (!key.station.isEmpty()) for (var watch : List.copyOf(watches.getOrDefault(key, List.of()))) {
                if (watch.value.status() == Status.LOADING && now - query.started > 15_000_000_000L) watch.accept(empty(watch.world, key, query.generation, Status.ERROR));
                else if (watch.received > 0 && now - watch.received > 5_000_000_000L && watch.value.status() != Status.STALE)
                    watch.accept(new Board(watch.world, key.station, query.generation, Status.STALE, watch.value.sampledAt(), watch.value.serverNow(), watch.value.services(), watch.value.arrivals()));
            }
            if (queries.get(key) == query && sent < 2 && MTRNetworkClient.canSendOverlay() && now - query.requestAt > 1_000_000_000L) {
                sent++; query.requestAt = now; MTRNetworkClient.sendToServer(new OverlayRequest(query.generation, key.dimension, key.station, true));
            }
        }
        for (var c : List.copyOf(catalogs.values())) if (catalog(c.world()).status() == Status.STALE)
            queries.computeIfAbsent(new Key(key(c.world().dimensionId()), ""), ignored -> new Query(++generation));
        views.invalidate(catalogs.values().stream().map(c -> catalog(c.world())).toList());
    }
    public void chunk(OverlayChunk message) {
        thread(); var key = new Key(message.dimension(), message.station()); var query = queries.get(key);
        if (query == null || query.generation != message.generation() || message.sequence() <= query.sequence) return;
        try {
            if (query.transfer == null || query.transferSequence != message.sequence()) {
                if (message.sequence() < query.transferSequence) return;
                if (message.total() > OverlayCodec.MAX_BYTES) throw new IllegalArgumentException("Overlay response too large");
                query.transferSequence = message.sequence(); query.transfer = new OverlayAssembly(message.count(), message.total());
            }
            if (!query.transfer.add(message.index(), message.count(), message.total(), message.bytes())) return;
            byte[] bytes = query.transfer.assemble(); query.transfer = null;
            query.sequence = message.sequence();
            try { decoder.execute(() -> {
                try {
                    Record decoded = key.station.isEmpty() ? OverlayCodec.decode(bytes, Catalog.class) : OverlayCodec.decode(bytes, Board.class);
                    Minecraft.getInstance().execute(() -> install(key, query, message.sequence(), decoded));
                } catch (Exception e) { MTRMap.LOGGER.warn("[MTRMap] Invalid overlay response", e); }
            }); } catch (RejectedExecutionException busy) { queries.put(key, new Query(++generation)); }
        } catch (Exception e) { query.transfer = null; MTRMap.LOGGER.warn("[MTRMap] Invalid overlay response", e); }
    }
    private void install(Key key, Query query, long sequence, Record decoded) {
        thread();
        if (queries.get(key) != query || query.sequence != sequence) return;
            if (decoded instanceof Catalog catalog) {
                if (!key(catalog.world().dimensionId()).equals(key.dimension)) return;
                catalogs.put(key.dimension, catalog); queries.remove(key);
            } else {
                var board = (Board) decoded;
                if (!key(board.world().dimensionId()).equals(key.dimension) || !board.stationId().equals(key.station)) return;
                for (var watch : List.copyOf(watches.getOrDefault(key, List.of()))) if (watch.world.equals(board.world()))
                    watch.accept(new Board(board.world(), board.stationId(), query.generation, board.status(), board.sampledAt(), board.serverNow(), board.services(), board.arrivals()));
            }
    }
    public void clear() {
        thread(); clearing = true;
        try { OverlayUi.clear(); } finally { clearing = false; }
        queries.clear(); decoder.getQueue().clear(); catalogs.clear(); generation++; views.clear(); ViewRenderer.clear();
        watches.values().forEach(list -> list.forEach(w -> { w.closed = true; w.value = empty(w.world, w.key, 0, Status.CLOSED); })); watches.clear();
    }
}

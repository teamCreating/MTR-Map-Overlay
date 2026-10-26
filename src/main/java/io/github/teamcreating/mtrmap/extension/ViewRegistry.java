package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Owned, deterministic view transactions. A failed replacement leaves the previous valid view installed. */
public final class ViewRegistry {
    public record Active(String owner, String name, Purpose purpose, int priority, long order, ViewRequest request) {}
    private final List<Session> sessions = new ArrayList<>();
    private long order, revision;
    private boolean overview;
    public long revision() { return revision; }
    public boolean overview() { return overview; }
    public void toggleOverview() { overview = !overview; revision++; }
    public List<Active> active(String dimension) {
        if (overview) return List.of();
        return sessions.stream().filter(s -> !s.closed && s.request != null && s.request.world().dimensionId().equals(dimension))
                .map(s -> new Active(s.owner, s.name, s.purpose, s.priority, s.order, s.request))
                .sorted(Comparator.comparingInt(Active::priority).thenComparingLong(Active::order)).toList();
    }
    public Optional<Active> focus(String dimension) { return active(dimension).stream().filter(a -> a.purpose == Purpose.FOCUS).reduce((a, b) -> b); }
    public ViewSession open(String owner, String name, Purpose purpose, int priority, Function<WorldRef, Catalog> catalog) {
        if (sessions.size() >= 32 || owner == null || !owner.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || name == null || name.length() > 80 || priority < -1000 || priority > 1000)
            throw new IllegalArgumentException("Invalid view owner/priority or session capacity reached");
        var session = new Session(owner, name, Objects.requireNonNull(purpose), priority, ++order, catalog); sessions.add(session); return session;
    }
    private final class Session implements ViewSession {
        final String owner, name; final Purpose purpose; final int priority; final long order; final Function<WorldRef, Catalog> catalog;
        ViewRequest request; boolean closed;
        Session(String owner, String name, Purpose purpose, int priority, long order, Function<WorldRef, Catalog> catalog) { this.owner = owner; this.name = name; this.purpose = purpose; this.priority = priority; this.order = order; this.catalog = catalog; }
        public CompletionStage<Status> replace(ViewRequest next) {
            if (closed) return CompletableFuture.completedFuture(Status.CLOSED);
            var c = catalog.apply(next.world());
            if (c.status() != Status.READY || c.revision() != next.catalogRevision()) return CompletableFuture.completedFuture(Status.STALE);
            if (purpose == Purpose.DECORATION && (next.unmatched() != Unmatched.KEEP || !next.showTrains())) return CompletableFuture.completedFuture(Status.INVALID);
            Set<String> routeIds = new HashSet<>(); c.routes().forEach(r -> routeIds.add(r.id()));
            if (!routeIds.containsAll(next.wholeRoutes()) || !routeIds.containsAll(next.routeStyles().keySet())
                    || next.spans().stream().anyMatch(s -> !s.routeId().isEmpty() && !routeIds.contains(s.routeId()))
                    || next.spanStyles().stream().anyMatch(s -> !s.span().routeId().isEmpty() && !routeIds.contains(s.span().routeId()))) return CompletableFuture.completedFuture(Status.INVALID);
            var perRail = new HashMap<String, Integer>();
            var requests = new ArrayList<ViewRequest>(); requests.add(next);
            sessions.stream().filter(s -> s != this && s.request != null && s.request.world().equals(next.world())).forEach(s -> requests.add(s.request));
            for (var view : requests) java.util.stream.Stream.concat(view.spans().stream(), view.spanStyles().stream().map(SpanStyle::span))
                    .forEach(s -> perRail.merge(s.rail().railId(), 1, Integer::sum));
            if (perRail.values().stream().anyMatch(n -> n > 256)) return CompletableFuture.completedFuture(Status.INVALID);
            request = next; revision++; return CompletableFuture.completedFuture(Status.READY);
        }
        public void clear() { if (!closed && request != null) { request = null; revision++; } }
        public void close() { if (closed) return; clear(); closed = true; sessions.remove(this); }
    }
    public void invalidate(Collection<Catalog> catalogs) {
        for (var session : sessions) if (session.request != null && catalogs.stream().noneMatch(c -> c.status() == Status.READY && c.world().equals(session.request.world()) && c.revision() == session.request.catalogRevision())) session.clear();
    }
    public void clear() { sessions.forEach(s -> { s.closed = true; s.request = null; }); sessions.clear(); overview = false; revision++; }
}

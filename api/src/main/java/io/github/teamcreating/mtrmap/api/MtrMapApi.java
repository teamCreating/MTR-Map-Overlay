package io.github.teamcreating.mtrmap.api;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Optional, client-thread API. Compile against the API JAR; do not shade it. */
public final class MtrMapApi {
    private static Client provider;
    private MtrMapApi() {}
    public static Optional<Client> findClient() { return Optional.ofNullable(provider); }
    /** Runtime bootstrap; consumers must not replace the provider. */
    public static void install(Client client) {
        if (provider != null && provider != client) throw new IllegalStateException("Provider already installed");
        provider = Objects.requireNonNull(client);
    }
    public enum Status { LOADING, READY, LIMITED, STALE, UNSUPPORTED, NOT_FOUND, ERROR, INVALID, AMBIGUOUS, UNAVAILABLE, CLOSED }
    public record WorldRef(UUID serverEpoch, String dimensionId) {
        public WorldRef { Objects.requireNonNull(serverEpoch); Objects.requireNonNull(dimensionId); }
    }
    public record Point(double x, double y, double z) {}
    public record Station(String id, String name, Point position, List<String> platformIds) {
        public Station { platformIds = List.copyOf(platformIds); }
    }
    public record Platform(String id, String stationId, String name, Point position) {}
    public record Stop(int ordinal, String platformId, String stationId, String destination) {}
    /** Fractions use canonical rail direction and native Core distance, including slopes. */
    public record RailSpan(String railId, double from, double to) {
        public RailSpan {
            Objects.requireNonNull(railId);
            if (!Double.isFinite(from) || !Double.isFinite(to) || from < 0 || from > 1 || to < 0 || to > 1 || from == to)
                throw new IllegalArgumentException("Rail fractions must be distinct and within [0,1]");
        }
    }
    public record Leg(int fromStop, int toStop, List<RailSpan> rails) {
        public Leg { rails = List.copyOf(rails); }
    }
    public record PathVariant(String id, List<Leg> legs) {
        public PathVariant { legs = List.copyOf(legs); }
    }
    public record Route(String id, String name, String number, int color, String type, List<Stop> stops, List<PathVariant> paths) {
        public Route { stops = List.copyOf(stops); paths = List.copyOf(paths); }
    }
    public record Catalog(WorldRef world, long revision, long baseStaticRevision, Status status,
                          List<Station> stations, List<Platform> platforms, List<Route> routes) {
        public Catalog { stations = List.copyOf(stations); platforms = List.copyOf(platforms); routes = List.copyOf(routes); }
    }
    public record Track(String id, List<Point> points, double nativeLength) {
        public Track { points = List.copyOf(points); }
    }
    public record RouteSection(String routeId, int fromStop, int toStop, Optional<String> pathVariant) {
        public RouteSection { Objects.requireNonNull(routeId); pathVariant = Objects.requireNonNull(pathVariant); }
    }
    /** routeId can be empty for a physical-rail selection. */
    public record SelectedSpan(String routeId, RailSpan rail) {
        public SelectedSpan { Objects.requireNonNull(routeId); Objects.requireNonNull(rail); }
    }
    public record Resolution(Status status, long catalogRevision, List<SelectedSpan> spans, List<String> candidates) {
        public Resolution { spans = List.copyOf(spans); candidates = List.copyOf(candidates); }
    }
    public enum Purpose { FOCUS, DECORATION }
    public enum Unmatched { KEEP, DIM, HIDE }
    public enum Pattern { INHERIT, SOLID, COLOUR_WHITE }
    public record Style(Optional<Integer> color, double opacity, double widthScale, Pattern pattern) {
        public Style {
            Objects.requireNonNull(color); Objects.requireNonNull(pattern);
            if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1 || !Double.isFinite(widthScale) || widthScale < 0.25 || widthScale > 4)
                throw new IllegalArgumentException("Invalid style");
        }
        public static Style inherit() { return new Style(Optional.empty(), 1, 1, Pattern.INHERIT); }
        public static Style dimmed(double opacity) { return new Style(Optional.empty(), opacity, 1, Pattern.INHERIT); }
    }
    /** A local override in native rail distance; later overrides win at overlapping points. */
    public record SpanStyle(SelectedSpan span, Style style) {
        public SpanStyle { Objects.requireNonNull(span); Objects.requireNonNull(style); }
    }
    public record ViewRequest(WorldRef world, long catalogRevision, List<SelectedSpan> spans,
                              Set<String> wholeRoutes, Unmatched unmatched, Map<String, Style> routeStyles,
                              boolean showTrains, Set<String> stationIds, List<SpanStyle> spanStyles, double unmatchedOpacity) {
        public ViewRequest(WorldRef world, long catalogRevision, List<SelectedSpan> spans, Set<String> wholeRoutes,
                           Unmatched unmatched, Map<String, Style> routeStyles, boolean showTrains, Set<String> stationIds) {
            this(world, catalogRevision, spans, wholeRoutes, unmatched, routeStyles, showTrains, stationIds, List.of(), .15);
        }
        public ViewRequest {
            Objects.requireNonNull(world); Objects.requireNonNull(unmatched);
            spans = List.copyOf(spans); wholeRoutes = Set.copyOf(wholeRoutes); routeStyles = Map.copyOf(routeStyles); stationIds = Set.copyOf(stationIds);
            spanStyles = List.copyOf(spanStyles);
            if (spans.size() > 20000 || spanStyles.size() > 20000 || stationIds.size() > 20000 || wholeRoutes.size() > 4096 || routeStyles.size() > 4096) throw new IllegalArgumentException("View exceeds capacity");
            if (!Double.isFinite(unmatchedOpacity) || unmatchedOpacity < 0 || unmatchedOpacity > 1) throw new IllegalArgumentException("Invalid unmatched opacity");
        }
        /** The caller supplies navigation progress. Overlay never infers movement or computes a route. */
        public static ViewRequest navigation(WorldRef world, long revision, List<SelectedSpan> remaining,
                                             List<SelectedSpan> completed, double completedOpacity, double unusedOpacity) {
            var all = new ArrayList<SelectedSpan>(completed); all.addAll(remaining);
            var overrides = new ArrayList<SpanStyle>();
            for (var span : completed) overrides.add(new SpanStyle(span, Style.dimmed(completedOpacity)));
            // Remaining wins when a repeated/circular journey traverses the same physical interval again.
            for (var span : remaining) overrides.add(new SpanStyle(span, Style.inherit()));
            return new ViewRequest(world, revision, all, Set.of(), Unmatched.DIM, Map.of(), true, Set.of(), overrides, unusedOpacity);
        }
    }
    public interface ViewSession extends AutoCloseable {
        CompletionStage<Status> replace(ViewRequest request);
        void clear();
        @Override void close();
    }
    public record Arrival(String routeId, String routeName, int color, String platformId, String platformName,
                          String destination, long arrivalAt, long departureAt, long deviation, boolean realtime, boolean terminating) {}
    public record Service(String routeId, String platformId, String routeName, int color, String platformName) {}
    public record Board(WorldRef world, String stationId, long generation, Status status, long sampledAt,
                        long serverNow, List<Service> services, List<Arrival> arrivals) {
        public Board { services = List.copyOf(services); arrivals = List.copyOf(arrivals); }
    }
    public interface BoardSubscription extends AutoCloseable {
        Board latest();
        @Override void close();
    }
    public record NavigationTarget(WorldRef world, String stationId) {}
    public interface Registration extends AutoCloseable { @Override void close(); }
    public interface Client {
        default String apiVersion() { return "1.0.0"; }
        Optional<WorldRef> world(String dimensionId);
        Catalog catalog(WorldRef world);
        List<Track> tracks(WorldRef world, Set<String> railIds);
        CompletionStage<Resolution> resolve(WorldRef world, long catalogRevision, List<RouteSection> sections);
        ViewSession openView(String owner, String displayName, Purpose purpose, int priority);
        BoardSubscription watchStation(WorldRef world, String stationId, Consumer<Board> listener);
        Registration registerNavigation(String owner, String displayName, Consumer<NavigationTarget> handler);
    }
}

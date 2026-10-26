package io.github.teamcreating.mtrmap.extension;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import java.util.*;

public final class SectionResolver {
    private SectionResolver() {}
    public static Resolution resolve(Catalog catalog, List<RouteSection> sections) {
        if (sections.isEmpty() || sections.size() > 128) return result(catalog, Status.INVALID);
        var spans = new ArrayList<SelectedSpan>();
        for (var section : sections) {
            var route = catalog.routes().stream().filter(r -> r.id().equals(section.routeId())).findFirst().orElse(null);
            if (route == null || section.fromStop() < 0 || section.toStop() >= route.stops().size() || section.fromStop() >= section.toStop()) return result(catalog, Status.INVALID);
            var candidates = route.paths().stream().filter(p -> section.pathVariant().isEmpty() || section.pathVariant().get().equals(p.id())).toList();
            if (candidates.isEmpty()) return result(catalog, Status.UNAVAILABLE);
            if (candidates.size() > 1) return new Resolution(Status.AMBIGUOUS, catalog.revision(), List.of(), candidates.stream().map(PathVariant::id).toList());
            var legs = candidates.getFirst().legs().stream().filter(l -> l.fromStop() >= section.fromStop() && l.toStop() <= section.toStop()).toList();
            if (legs.size() != section.toStop() - section.fromStop()) return result(catalog, Status.UNAVAILABLE);
            for (var leg : legs) for (var rail : leg.rails()) spans.add(new SelectedSpan(route.id(), rail));
        }
        return new Resolution(Status.READY, catalog.revision(), spans, List.of());
    }
    private static Resolution result(Catalog c, Status status) { return new Resolution(status, c.revision(), List.of(), List.of()); }
}

package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import java.util.*;

/** Presentation of native snapshots only. No scheduling, prediction or deviation arithmetic. */
public final class PidsBoardModel {
    public enum Eta { MINUTES, SECONDS, ARRIVED, MISSING }
    public record Row(String routeId, String route, int color, String destination, String platform,
                      Eta eta, long value, boolean scheduled, boolean terminating) {}
    public record Columns(int destination, int destinationWidth, int line, int lineWidth, int platform, int platformWidth, int eta, int etaWidth) {}
    private PidsBoardModel() {}
    public static Columns columns(int width) {
        if (width < 150) throw new IllegalArgumentException("PIDS requires 150 content pixels");
        int time = 42, platform = 26, gutter = 6;
        int flexible = width - time - platform - 3 * gutter;
        int destination = (int) Math.round(flexible * .53), line = flexible - destination;
        return new Columns(0, destination, destination + gutter, line,
                destination + line + 2 * gutter, platform, width - time, time);
    }
    public static List<Row> rows(Board board, long now) {
        if (board == null) return List.of();
        var grouped = new HashMap<String, List<Arrival>>();
        for (var a : board.arrivals()) if (a.departureAt() >= now)
            grouped.computeIfAbsent(a.routeId() + ":" + a.platformId(), ignored -> new ArrayList<>()).add(a);
        var result = new ArrayList<Row>();
        for (var service : board.services().stream().sorted(Comparator.comparing(Service::routeName).thenComparing(Service::routeId).thenComparing(Service::platformId)).toList()) {
            var arrivals = grouped.getOrDefault(service.routeId() + ":" + service.platformId(), List.of());
            if (arrivals.isEmpty()) result.add(new Row(service.routeId(), service.routeName(), service.color(), "—", service.platformName(), Eta.MISSING, 0, false, false));
            else for (var a : arrivals) {
                long seconds = Math.max(0, (a.arrivalAt() - now + 999) / 1000);
                var eta = seconds == 0 ? Eta.ARRIVED : seconds < 60 ? Eta.SECONDS : Eta.MINUTES;
                result.add(new Row(a.routeId(), a.routeName(), a.color(), a.destination(), a.platformName(), eta,
                        eta == Eta.MINUTES ? seconds / 60 : seconds, !a.realtime(), a.terminating()));
            }
        }
        return List.copyOf(result);
    }
}

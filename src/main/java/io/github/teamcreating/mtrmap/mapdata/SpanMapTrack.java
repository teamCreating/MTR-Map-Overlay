package io.github.teamcreating.mtrmap.mapdata;
import java.util.*;

/** Native-distance interval, re-tessellated at the same screen error as its source rail. */
public final class SpanMapTrack extends MapTrack {
    private record Mesh(List<double[]> points, double[] distances) {}
    private final MapTrack original;
    private final double from, to;
    public SpanMapTrack(MapTrack original, double from, double to, String id) { this(original, from, to, id, slice(original, from, to)); }
    private SpanMapTrack(MapTrack original, double from, double to, String id, Mesh mesh) {
        super(id, mesh.points, null, mesh.distances); this.original = original; this.from = from; this.to = to;
        if (original.curve != null) for (int i = 0; i < points.size(); i++) {
            var n = original.curve.normal(from * original.length() + mesh.distances[i]); normalX[i] = n[0]; normalZ[i] = n[1];
        }
    }
    @Override public MapTrack forScale(double scale) {
        var mesh = slice(original.forScale(scale), from, to);
        return new SpanMapTrack(original, from, to, id, mesh);
    }
    private static Mesh slice(MapTrack source, double from, double to) {
        if (source.curve == null) throw new IllegalArgumentException("Native distance mapping unavailable");
        double a = from * source.length(), b = to * source.length();
        var points = new ArrayList<double[]>(); var ds = new ArrayList<Double>();
        add(source, points, ds, a, a);
        for (int i = 1; i + 1 < source.points.size(); i++) if (source.distances[i] > a && source.distances[i] < b) { points.add(source.points.get(i).clone()); ds.add(source.distances[i] - a); }
        add(source, points, ds, b, a);
        return new Mesh(points, ds.stream().mapToDouble(d -> d).toArray());
    }
    private static void add(MapTrack source, List<double[]> points, List<Double> ds, double d, double origin) {
        var p = source.curve.position(d); points.add(new double[]{p.x(), p.z(), p.y()}); ds.add(d - origin);
    }
}

package com.lx862.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;

/** Pure X/Z map layers: railway backing, route colours/details, then cars. */
public final class PlanarMapScene {
    public record Quad(double x1, double z1, double x2, double z2,
            double x3, double z3, double x4, double z4, int color, int alpha, MapCar car) {
        public boolean contains(double x, double z) {
            final double a = cross(x1, z1, x2, z2, x, z);
            final double b = cross(x2, z2, x3, z3, x, z);
            final double c = cross(x3, z3, x4, z4, x, z);
            final double d = cross(x4, z4, x1, z1, x, z);
            return !((a > 1.0E-8 || b > 1.0E-8 || c > 1.0E-8 || d > 1.0E-8)
                    && (a < -1.0E-8 || b < -1.0E-8 || c < -1.0E-8 || d < -1.0E-8));
        }

        private static double cross(double ax, double az, double bx, double bz, double x, double z) {
            return (bx - ax) * (z - az) - (bz - az) * (x - ax);
        }
    }

    private final List<Quad> staticQuads;
    private final List<Quad> carQuads = new ArrayList<>();
    private final List<Quad> carTargets = new ArrayList<>();
    private final List<Quad> quads;

    private PlanarMapScene(List<Quad> staticQuads) {
        this.staticQuads = staticQuads;
        this.quads = new Layers(staticQuads, carQuads);
    }

    public List<Quad> quads() {
        return quads;
    }

    /** Immutable geometry shared across frames; no copying into the dynamic scene. */
    public List<Quad> staticQuads() {
        return staticQuads;
    }

    public int carQuadCount() {
        return carQuads.size();
    }

    private static final class Layers extends AbstractList<Quad> implements RandomAccess {
        private final List<Quad> first;
        private final List<Quad> second;

        private Layers(List<Quad> first, List<Quad> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Quad get(int index) {
            return index < first.size() ? first.get(index) : second.get(index - first.size());
        }

        @Override
        public int size() {
            return first.size() + second.size();
        }
    }

    /** Only visible car bodies/markers participate, in reverse paint order. */
    public MapCar pick(double x, double z) {
        for (int i = carTargets.size() - 1; i >= 0; i--) {
            final Quad quad = carTargets.get(i);
            if (quad.contains(x, z)) return quad.car;
        }
        return null;
    }

    public static PlanarMapScene build(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, List<MapCar> cars,
            boolean showTracks, boolean showRoutes, double scale,
            double minX, double minZ, double maxX, double maxZ) {
        return withCars(buildStatic(tracks, palette, patterns, showTracks, showRoutes,
                scale, minX, minZ, maxX, maxZ), cars, scale, minX, minZ, maxX, maxZ);
    }

    public static List<Quad> buildStatic(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, boolean showTracks, boolean showRoutes,
            double scale, double minX, double minZ, double maxX, double maxZ) {
        if (!Double.isFinite(scale) || scale <= 0) return List.of();
        final List<Quad> result = new ArrayList<>();
        // Finish every backing before drawing any route, including at crossings.
        for (MapTrack track : tracks) {
            final List<TrackRoutePalette.Entry> bands = palette.getOrDefault(track.id, List.of());
            final double routeWidth = showRoutes ? RouteRibbonGeometry.halfWidth(bands, scale) : 0;
            if (showTracks || routeWidth > 0) {
                final double width = Math.max(showTracks ? RailRenderStyle.worldHalfWidth(
                        RailRenderStyle.TRACK_HALF_WIDTH_PX, scale) : 0,
                        routeWidth > 0 ? routeWidth + RailRenderStyle.TRACK_SHOULDER_PX / scale : 0);
                RouteRibbonGeometry.drawStrip(track, width, scale, minX, minZ, maxX, maxZ,
                        (a, b, c, d, e, f, g, h, color, alpha) ->
                                addTo(result, a, b, c, d, e, f, g, h, color, 255, null));
            }
        }
        if (showRoutes) {
            final List<Quad> details = new ArrayList<>();
            for (MapTrack track : tracks) {
                RouteRibbonGeometry.drawLayered(track, palette.getOrDefault(track.id, List.of()),
                        patterns.getOrDefault(track.id, TrackPatternLayout.Placement.DEFAULT), scale,
                        minX, minZ, maxX, maxZ,
                        (a, b, c, d, e, f, g, h, color, alpha) ->
                                addTo(result, a, b, c, d, e, f, g, h, color, alpha, null),
                        (a, b, c, d, e, f, g, h, color, alpha) ->
                                addTo(details, a, b, c, d, e, f, g, h, color, alpha, null));
            }
            result.addAll(details);
        }
        return List.copyOf(result);
    }

    /** The supplied static geometry is an immutable buildStatic result. */
    public static PlanarMapScene withCars(List<Quad> staticQuads, List<MapCar> cars, double scale,
            double minX, double minZ, double maxX, double maxZ) {
        final PlanarMapScene scene = new PlanarMapScene(staticQuads);
        if (!Double.isFinite(scale) || scale <= 0) return scene;
        for (MapCar car : cars) {
            if (!car.valid() || !car.intersects(minX, minZ, maxX, maxZ)) continue;
            final double lengthPixels = Math.hypot(car.frontRight().x() - car.backRight().x(),
                    car.frontRight().z() - car.backRight().z()) * scale;
            if (lengthPixels < 5) {
                if (car.leading()) scene.marker(car, scale);
            } else {
                scene.car(car, scale);
            }
        }
        return scene;
    }

    private void marker(MapCar car, double scale) {
        final MapVertex p = car.center();
        final double r = 2.5 / scale;
        final Quad target = add(p.x() + r, p.z() + r, p.x() + r, p.z() - r,
                p.x() - r, p.z() - r, p.x() - r, p.z() + r, car.color(), 255, car);
        if (target != null) carTargets.add(target);
    }

    private void car(MapCar car, double scale) {
        final MapVertex a = car.frontRight(), b = car.backRight(), c = car.backLeft(), d = car.frontLeft();
        final Quad target = add(a.x(), a.z(), b.x(), b.z(), c.x(), c.z(), d.x(), d.z(),
                0x16202A, 255, car);
        if (target == null) return;
        carTargets.add(target);
        final double width = Math.hypot(a.x() - d.x(), a.z() - d.z());
        final double inset = Math.min(0.3, Math.max(0.12, 0.65 / (scale * Math.max(0.01, width))));
        add(lerp(a.x(), d.x(), inset), lerp(a.z(), d.z(), inset),
                lerp(b.x(), c.x(), inset), lerp(b.z(), c.z(), inset),
                lerp(c.x(), b.x(), inset), lerp(c.z(), b.z(), inset),
                lerp(d.x(), a.x(), inset), lerp(d.z(), a.z(), inset), car.color(), 255, car);
        final double length = Math.hypot(a.x() - b.x(), a.z() - b.z());
        final double cap = Math.min(0.12, 0.7 / (scale * Math.max(0.01, length)));
        add(a.x(), a.z(), lerp(a.x(), b.x(), cap), lerp(a.z(), b.z(), cap),
                lerp(d.x(), c.x(), cap), lerp(d.z(), c.z(), cap), d.x(), d.z(),
                car.leading() ? 0xFFFFFF : 0x16202A, 255, car);
        add(lerp(b.x(), a.x(), cap), lerp(b.z(), a.z(), cap), b.x(), b.z(), c.x(), c.z(),
                lerp(c.x(), d.x(), cap), lerp(c.z(), d.z(), cap), 0x16202A, 255, car);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private Quad add(double a, double b, double c, double d, double e, double f, double g, double h,
            int color, int alpha, MapCar car) {
        return addTo(carQuads, a, b, c, d, e, f, g, h, color, alpha, car);
    }

    private static Quad addTo(List<Quad> target, double a, double b, double c, double d,
            double e, double f, double g, double h, int color, int alpha, MapCar car) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || !Double.isFinite(d)
                || !Double.isFinite(e) || !Double.isFinite(f) || !Double.isFinite(g) || !Double.isFinite(h)) return null;
        final double area = (c - a) * (f - b) - (d - b) * (e - a)
                + (e - a) * (h - b) - (f - b) * (g - a);
        if (Math.abs(area) < 1.0E-9) return null;
        final Quad quad = new Quad(a, b, c, d, e, f, g, h, color, alpha, car);
        target.add(quad);
        return quad;
    }
}

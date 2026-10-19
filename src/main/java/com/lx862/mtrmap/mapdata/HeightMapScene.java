package com.lx862.mtrmap.mapdata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Painter-ordered map overlay. Physical height precedes feature type, so bridges can cover cars below. */
public final class HeightMapScene {
    public record Quad(MapVertex a, MapVertex b, MapVertex c, MapVertex d,
            int color, int alpha, double height, int detail, MapCar car) {
        public boolean contains(double x, double z) {
            final MapVertex[] points = {a, b, c, d};
            boolean positive = false, negative = false;
            for (int i = 0; i < 4; i++) {
                final MapVertex p = points[i], q = points[(i + 1) % 4];
                final double cross = (q.x() - p.x()) * (z - p.z()) - (q.z() - p.z()) * (x - p.x());
                positive |= cross > 1.0E-8;
                negative |= cross < -1.0E-8;
            }
            return !(positive && negative);
        }
    }

    private final List<Quad> quads = new ArrayList<>();

    private HeightMapScene() {
    }

    public List<Quad> quads() {
        return quads;
    }

    /** Last visible primitive wins. An upper railway masks picking of a car underneath it. */
    public MapCar pick(double x, double z) {
        for (int i = quads.size() - 1; i >= 0; i--) {
            final Quad quad = quads.get(i);
            if (quad.contains(x, z)) return quad.car;
        }
        return null;
    }

    public static HeightMapScene build(List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, List<MapCar> cars,
            boolean showTracks, boolean showRoutes, double scale,
            double minX, double minZ, double maxX, double maxZ) {
        final HeightMapScene scene = new HeightMapScene();
        if (!Double.isFinite(scale) || scale <= 0) return scene;
        for (MapTrack track : tracks) {
            final List<TrackRoutePalette.Entry> bands = palette.getOrDefault(track.id, List.of());
            final double routeWidth = showRoutes ? RouteRibbonGeometry.halfWidth(bands, scale) : 0;
            if (showTracks || routeWidth > 0) {
                final double width = Math.max(showTracks ? RailRenderStyle.worldHalfWidth(
                        RailRenderStyle.TRACK_HALF_WIDTH_PX, scale) : 0,
                        routeWidth > 0 ? routeWidth + RailRenderStyle.TRACK_SHOULDER_PX / scale : 0);
                // Opaque backing masks lower overlay features, instead of blending two levels together.
                RouteRibbonGeometry.drawStrip(track, width, scale, minX, minZ, maxX, maxZ,
                        (a, b, c, d, color, alpha, y, detail) -> scene.add(a, b, c, d, color, 255, y, 0, null));
            }
            if (showRoutes && !bands.isEmpty()) {
                RouteRibbonGeometry.drawSpatial(track, bands,
                        patterns.getOrDefault(track.id, TrackPatternLayout.Placement.DEFAULT), scale,
                        minX, minZ, maxX, maxZ,
                        (a, b, c, d, color, alpha, y, detail) -> scene.add(a, b, c, d, color, alpha, y, 10 + detail, null));
            }
        }
        for (MapCar car : cars) {
            if (!car.valid() || !car.intersects(minX, minZ, maxX, maxZ)) continue;
            final double lengthPixels = Math.hypot(car.frontRight().x() - car.backRight().x(),
                    car.frontRight().z() - car.backRight().z()) * scale;
            if (lengthPixels < 5) {
                if (car.leading()) scene.marker(car, scale);
                continue;
            }
            scene.car(car, scale);
        }
        scene.quads.sort(Comparator.comparingDouble((Quad q) -> Double.isFinite(q.height)
                ? q.height : Double.NEGATIVE_INFINITY).thenComparingInt(Quad::detail));
        return scene;
    }

    private void marker(MapCar car, double scale) {
        final MapVertex p = car.center();
        final double r = 2.5 / scale;
        final MapVertex a = new MapVertex(p.x() + r, p.y(), p.z() + r);
        final MapVertex b = new MapVertex(p.x() + r, p.y(), p.z() - r);
        final MapVertex c = new MapVertex(p.x() - r, p.y(), p.z() - r);
        final MapVertex d = new MapVertex(p.x() - r, p.y(), p.z() + r);
        add(a, b, c, d, car.color(), 255, p.y(), 30, car);
    }

    private void car(MapCar car, double scale) {
        final double frontY = (car.frontRight().y() + car.frontLeft().y()) / 2;
        final double backY = (car.backRight().y() + car.backLeft().y()) / 2;
        final int pieces = Math.max(1, Math.min(256, (int) Math.ceil(Math.abs(backY - frontY) / 0.5)));
        final double width = Math.hypot(car.frontRight().x() - car.frontLeft().x(),
                car.frontRight().z() - car.frontLeft().z());
        final double inset = Math.min(0.3, Math.max(0.12, 0.65 / (scale * Math.max(0.01, width))));
        for (int i = 0; i < pieces; i++) {
            final double from = (double) i / pieces, to = (double) (i + 1) / pieces;
            final MapVertex a = car.frontRight().lerp(car.backRight(), from);
            final MapVertex b = car.frontRight().lerp(car.backRight(), to);
            final MapVertex c = car.frontLeft().lerp(car.backLeft(), to);
            final MapVertex d = car.frontLeft().lerp(car.backLeft(), from);
            final double y = (a.y() + b.y() + c.y() + d.y()) / 4;
            add(a, b, c, d, 0x16202A, 255, y, 30, car);
            // Side borders remain visible; colour comes from the actual service route.
            add(a.lerp(d, inset), b.lerp(c, inset), c.lerp(b, inset), d.lerp(a, inset),
                    car.color(), 255, y, 31, car);
        }
        // End caps separate adjacent cars without changing their real positions or lengths.
        final double length = Math.hypot(car.frontRight().x() - car.backRight().x(),
                car.frontRight().z() - car.backRight().z());
        final double cap = Math.min(0.12, 0.7 / (scale * Math.max(0.01, length)));
        add(car.frontRight(), car.frontRight().lerp(car.backRight(), cap),
                car.frontLeft().lerp(car.backLeft(), cap), car.frontLeft(),
                car.leading() ? 0xFFFFFF : 0x16202A, 255, frontY + (backY - frontY) / (2 * pieces), 32, car);
        add(car.backRight().lerp(car.frontRight(), cap), car.backRight(), car.backLeft(),
                car.backLeft().lerp(car.frontLeft(), cap), 0x16202A, 255, backY + (frontY - backY) / (2 * pieces), 32, car);
    }

    private void add(MapVertex a, MapVertex b, MapVertex c, MapVertex d,
            int color, int alpha, double height, int detail, MapCar car) {
        if (!Double.isFinite(a.x()) || !Double.isFinite(a.z()) || !Double.isFinite(b.x()) || !Double.isFinite(b.z())
                || !Double.isFinite(c.x()) || !Double.isFinite(c.z()) || !Double.isFinite(d.x()) || !Double.isFinite(d.z())) return;
        final double area = (b.x() - a.x()) * (c.z() - a.z()) - (b.z() - a.z()) * (c.x() - a.x())
                + (c.x() - a.x()) * (d.z() - a.z()) - (c.z() - a.z()) * (d.x() - a.x());
        if (Math.abs(area) < 1.0E-9) return;
        quads.add(new Quad(a, b, c, d, color, alpha, height, detail, car));
    }
}

package io.github.teamcreating.mtrmap.mapdata;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Bounded, render-thread viewport cache. Static geometry and car pose revisions are independent. */
public final class PlanarMapCache {
    public static final int DEFAULT_MAX_QUADS = 100_000;
    private final int maxQuads;
    private WeakReference<Object> source;
    private double scale;
    private boolean tracks;
    private boolean routes;
    private double minX, minZ, maxX, maxZ;
    private List<PlanarMapScene.Quad> geometry;
    private WeakReference<List<MapCar>> cars;
    private PlanarMapScene scene;
    private long staticBuilds;
    private long carBuilds;
    private long curveBuilds;
    private double curveScale;
    private Map<String, MapTrack> meshes = Map.of();

    public PlanarMapCache() {
        this(DEFAULT_MAX_QUADS);
    }

    public PlanarMapCache(int maxQuads) {
        if (maxQuads < 0) throw new IllegalArgumentException("Negative cache budget");
        this.maxQuads = maxQuads;
    }

    /** sourceRevision is a published immutable snapshot identity, not a numeric hash alone. */
    public PlanarMapScene get(Object sourceRevision, List<MapTrack> rails,
            Map<String, List<TrackRoutePalette.Entry>> palette,
            Map<String, TrackPatternLayout.Placement> patterns, List<MapCar> poses,
            boolean showTracks, boolean showRoutes, double pixelsPerBlock,
            double viewMinX, double viewMinZ, double viewMaxX, double viewMaxZ) {
        if (!valid(pixelsPerBlock, viewMinX, viewMinZ, viewMaxX, viewMaxZ)) {
            return PlanarMapScene.withCars(List.of(), List.of(), 1, 0, 0, 0, 0);
        }
        if (geometry == null || source == null || source.get() != sourceRevision || scale != pixelsPerBlock
                || tracks != showTracks || routes != showRoutes
                || viewMinX < minX || viewMinZ < minZ || viewMaxX > maxX || viewMaxZ > maxZ) {
            final double padX = Math.max((viewMaxX - viewMinX) * 0.15, 64 / pixelsPerBlock);
            final double padZ = Math.max((viewMaxZ - viewMinZ) * 0.15, 64 / pixelsPerBlock);
            final double left = viewMinX - padX, top = viewMinZ - padZ;
            final double right = viewMaxX + padX, bottom = viewMaxZ + padZ;
            // Extremely small scales must not turn a finite viewport into infinite cache bounds.
            final boolean padded = valid(pixelsPerBlock, left, top, right, bottom);
            minX = padded ? left : viewMinX;
            minZ = padded ? top : viewMinZ;
            maxX = padded ? right : viewMaxX;
            maxZ = padded ? bottom : viewMaxZ;
            double bucket = Math.scalb(1.0, Math.getExponent(pixelsPerBlock));
            if (bucket < pixelsPerBlock) bucket *= 2;
            final double lodScale = Double.isFinite(bucket) ? bucket : pixelsPerBlock;
            final Map<String, MapTrack> previous = source != null && source.get() == sourceRevision && curveScale == lodScale
                    ? meshes : Map.of();
            final Map<String, MapTrack> nextMeshes = new HashMap<>();
            final int[] retainedPoints = {0};
            final List<PlanarMapScene.Quad> built = PlanarMapScene.buildStatic(rails, palette, patterns,
                    showTracks, showRoutes, pixelsPerBlock, minX, minZ, maxX, maxZ, rail -> {
                        if (rail.curve == null) return rail;
                        MapTrack mesh = previous.get(rail.id);
                        if (mesh == null || mesh.curve != rail.curve) {
                            mesh = rail.forScale(lodScale);
                            curveBuilds++;
                        }
                        if ((long) retainedPoints[0] + mesh.points.size() <= maxQuads) {
                            nextMeshes.put(rail.id, mesh);
                            retainedPoints[0] += mesh.points.size();
                        }
                        return mesh;
                    });
            staticBuilds++;
            if (built.size() > maxQuads) {
                // Correct uncached rendering is retained when a dense view exceeds the retention budget.
                final PlanarMapScene uncached = PlanarMapScene.withCars(built, poses,
                        pixelsPerBlock, minX, minZ, maxX, maxZ);
                carBuilds++;
                clear();
                return uncached;
            }
            source = new WeakReference<>(sourceRevision);
            meshes = Map.copyOf(nextMeshes);
            curveScale = lodScale;
            scale = pixelsPerBlock;
            tracks = showTracks;
            routes = showRoutes;
            geometry = built;
            cars = null;
            scene = null;
        }
        if (scene == null || cars == null || cars.get() != poses) {
            final PlanarMapScene next = PlanarMapScene.withCars(geometry, poses, pixelsPerBlock,
                    minX, minZ, maxX, maxZ);
            carBuilds++;
            if ((long) geometry.size() + next.carQuadCount() > maxQuads) {
                clear();
                return next;
            }
            cars = new WeakReference<>(poses);
            scene = next;
        }
        return scene;
    }

    private static boolean valid(double scale, double minX, double minZ, double maxX, double maxZ) {
        return Double.isFinite(scale) && scale > 0 && Double.isFinite(minX) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxZ) && maxX >= minX && maxZ >= minZ;
    }

    public void clear() {
        source = null;
        geometry = null;
        cars = null;
        scene = null;
        meshes = Map.of();
    }

    public long staticBuilds() {
        return staticBuilds;
    }

    public long curveBuilds() { return curveBuilds; }

    public long carBuilds() {
        return carBuilds;
    }

    public int retainedQuads() {
        return scene != null ? scene.quads().size() : 0;
    }
}

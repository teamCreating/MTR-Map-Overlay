import com.lx862.mtrmap.mapdata.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic production-geometry counts, not a timing or allocation benchmark. */
public final class HeightComplexityProbe {
    public static void main(String[] args) {
        System.out.println("case,railQuads,carQuads,totalQuads");
        count("flat_4lanes", 0, 0);
        count("railRise1.6_4lanes", 1.6, 0);
        count("railRise1.6_carRise4_4lanes", 1.6, 4);
    }

    private static void count(String label, double segmentRise, double carRise) {
        final List<MapTrack> tracks = new ArrayList<>();
        final Map<String, List<TrackRoutePalette.Entry>> palettes = new HashMap<>();
        final List<TrackRoutePalette.Entry> lanes = List.of(
                new TrackRoutePalette.Entry("a", "a", 0xFF0000, RouteLineStyle.NORMAL),
                new TrackRoutePalette.Entry("b", "b", 0x00FF00, RouteLineStyle.NORMAL),
                new TrackRoutePalette.Entry("c", "c", 0x0000FF, RouteLineStyle.NORMAL),
                new TrackRoutePalette.Entry("d", "d", 0xFFFF00, RouteLineStyle.NORMAL));
        for (int rail = 0; rail < 100; rail++) {
            final List<double[]> points = new ArrayList<>();
            for (int segment = 0; segment <= 100; segment++) {
                points.add(new double[] {segment * 8.0, rail * 8.0, 60 + (segment % 2) * segmentRise});
            }
            final String id = "r" + rail;
            tracks.add(new MapTrack(id, points));
            palettes.put(id, lanes);
        }
        final List<MapCar> cars = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            final double x = (i % 25) * 30 + 10, z = (i / 25) * 30 + 10;
            cars.add(new MapCar(i, 0, 1, true, "model", "line", "destination", 0xFF0000,
                    new MapVertex(x + 10, 60 + carRise, z + 1.5),
                    new MapVertex(x - 10, 60, z + 1.5),
                    new MapVertex(x - 10, 60, z - 1.5),
                    new MapVertex(x + 10, 60 + carRise, z - 1.5)));
        }
        final PlanarMapScene scene = PlanarMapScene.build(tracks, palettes, Map.of(), cars,
                true, true, 1, -100, -100, 1000, 1000);
        final long carQuads = scene.quads().stream().filter(q -> q.car() != null).count();
        System.out.printf("%s,%d,%d,%d%n", label, scene.quads().size() - carQuads,
                carQuads, scene.quads().size());
    }
}

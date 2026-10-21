import com.lx862.mtrmap.mapdata.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual production geometry: one fixed world fixture at several zoom levels. */
public final class RouteJoinZoomPreview {
    public static void main(String[] args) throws Exception {
        final var image = new BufferedImage(1440, 850, BufferedImage.TYPE_INT_RGB);
        final var g = image.createGraphics();
        g.setColor(new Color(0xE9EDF0)); g.fillRect(0, 0, 1440, 850);
        g.setColor(new Color(0x172431)); g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.drawString("MTR Map Overlay / Continuous joins and world-space zoom", 30, 40);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("Production geometry / synthetic fixed world / 2x GUI pixels for inspection", 30, 70);
        final var green = new TrackRoutePalette.Entry("a", "Local", 0x008844, RouteLineStyle.LIGHT_RAIL);
        final var red = new TrackRoutePalette.Entry("b", "Express", 0xE62028, RouteLineStyle.HIGH_SPEED);
        final var blue = new TrackRoutePalette.Entry("c", "Rail", 0x3377DD, RouteLineStyle.NORMAL);
        final var yellow = new TrackRoutePalette.Entry("d", "Rail", 0xE9A924, RouteLineStyle.NORMAL);
        final var curve = new ArrayList<double[]>();
        for (int i = 0; i <= 32; i++) {
            final double t = i / 32.;
            curve.add(new double[]{180 + t * 160, 50 * t * t * (3 - 2 * t)});
        }
        final var a = new MapTrack("a", List.of(new double[]{0, 0}, new double[]{180, 0}));
        final var b = new MapTrack("b", curve);
        final var c = new MapTrack("c", List.of(new double[]{340, 50}, new double[]{440, 50}));
        final var d = new MapTrack("d", List.of(new double[]{440, 50}, new double[]{540, 50}));
        final var tracks = List.of(a, b, c, d);
        final var palette = Map.of("a", List.of(green, red), "b", List.of(green), "c", List.of(green));
        for (int row = 0; row < 3; row++) {
            final double scale = new double[]{1, .35, .08}[row];
            final int y = 95 + row * 245;
            final var panel = (Graphics2D) g.create(30, y, 1380, 225);
            panel.setColor(new Color(0x506B35)); panel.fillRect(0, 0, 1380, 225);
            panel.setColor(Color.WHITE); panel.drawString("GUI px/block = " + scale + " / same world", 20, 28);
            paint(panel, tracks, palette, scale, 40, 70);
            final var shared = new MapTrack("shared", List.of(new double[]{0, 0}, new double[]{540, 0}));
            paint(panel, List.of(shared), Map.of("shared", List.of(green, red, blue, yellow)), scale, 40, 205);
            panel.drawString("4 shared routes", 1160, 210); panel.dispose();
        }
        g.dispose(); ImageIO.write(image, "png", new File(args[0]));
    }

    private static void paint(Graphics2D g, List<MapTrack> tracks,
            Map<String, List<TrackRoutePalette.Entry>> palette, double scale, double x, double y) {
        final var scene = PlanarMapScene.build(tracks, palette, TrackPatternLayout.build(tracks, palette), List.of(),
                true, true, scale, -100, -100, 1000, 200);
        for (var q : scene.quads()) {
            if (q.feather()) continue; // Java2D has no matching per-vertex-alpha rasterizer.
            final var p = new Path2D.Double();
            p.moveTo(x + q.x1() * scale * 2, y + q.z1() * scale * 2);
            p.lineTo(x + q.x2() * scale * 2, y + q.z2() * scale * 2);
            p.lineTo(x + q.x3() * scale * 2, y + q.z3() * scale * 2);
            p.lineTo(x + q.x4() * scale * 2, y + q.z4() * scale * 2);
            p.closePath(); g.setColor(new Color(q.color())); g.fill(p);
        }
        g.setColor(Color.WHITE);
    }
}

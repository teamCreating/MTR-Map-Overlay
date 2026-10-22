import io.github.teamcreating.mtrmap.mapdata.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Production geometry on synthetic fixtures; not a game screenshot. */
public final class TrainStylePreview {
    public static void main(String[] args) throws Exception {
        final var image = new BufferedImage(1440, 780, BufferedImage.TYPE_INT_RGB);
        final var g = image.createGraphics();
        g.setColor(new Color(0xE9EDF0)); g.fillRect(0, 0, 1440, 780);
        g.setColor(new Color(0x172431)); g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString("MTR Map Overlay / Carriage visibility", 30, 42);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString("Production geometry / synthetic fixtures / enlarged 2x for inspection", 30, 72);
        for (int i = 0; i < 6; i++) {
            final double scale = new double[] {.2, .5, 1, .5, 1, 2}[i];
            final int x = 30 + i % 3 * 470, y = 105 + i / 3 * 325;
            final int color = i < 3 ? 0x118451 : 0xCF3342;
            final var copy = (Graphics2D) g.create(x, y, 440, 300);
            copy.setColor(new Color(i < 3 ? 0x4D6234 : 0xCFD5DC)); copy.fillRect(0, 0, 440, 300);
            copy.setColor(i < 3 ? Color.WHITE : new Color(0x172431));
            copy.drawString("Scale " + scale + (scale < .45 ? " / train marker" : " / individual cars"), 16, 28);
            final var cars = new ArrayList<MapCar>();
            final double angle = i == 1 || i == 4 ? .45 : 0;
            for (int n = 0; n < 3; n++) {
                final double center = (1 - n) * 21;
                cars.add(new MapCar(1, n, 3, n == 0, "fixture", "Route", "Terminus", color,
                        point(center + 10, 1.5, angle), point(center - 10, 1.5, angle),
                        point(center - 10, -1.5, angle), point(center + 10, -1.5, angle)));
            }
            final var track = new MapTrack("r", List.of(new double[] {-200 * Math.cos(angle), -200 * Math.sin(angle)},
                    new double[] {200 * Math.cos(angle), 200 * Math.sin(angle)}));
            final var palette = Map.of("r", List.of(new TrackRoutePalette.Entry("route", "Route", color,
                    i < 3 ? RouteLineStyle.LIGHT_RAIL : RouteLineStyle.HIGH_SPEED)));
            final var scene = PlanarMapScene.build(List.of(track), palette, Map.of(), cars,
                    true, true, scale, -220 / scale, -100 / scale, 220 / scale, 100 / scale);
            for (var q : scene.quads()) {
                final var path = new Path2D.Double();
                path.moveTo(220 + q.x1() * scale * 2, 165 + q.z1() * scale * 2);
                path.lineTo(220 + q.x2() * scale * 2, 165 + q.z2() * scale * 2);
                path.lineTo(220 + q.x3() * scale * 2, 165 + q.z3() * scale * 2);
                path.lineTo(220 + q.x4() * scale * 2, 165 + q.z4() * scale * 2);
                path.closePath(); copy.setColor(new Color(q.color())); copy.fill(path);
            }
            copy.dispose();
        }
        g.dispose(); ImageIO.write(image, "png", new File(args[0]));
    }
    private static MapVertex point(double x, double z, double angle) {
        return new MapVertex(x * Math.cos(angle) - z * Math.sin(angle), 64,
                x * Math.sin(angle) + z * Math.cos(angle));
    }
}

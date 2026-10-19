import com.lx862.mtrmap.mapdata.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

/** Headless proof of the actual shared painter order; synthetic measured-coordinate fixtures, not game screenshots. */
public final class TrainHeightPreview {
    public static void main(String[] args) throws Exception {
        final BufferedImage image = new BufferedImage(1440, 690, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0xF0F3F6));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(new Color(0x182633));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 27));
        graphics.drawString("MTR Map Overlay / XYZ and underpass rendering", 36, 48);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 16));
        graphics.drawString("Shared production geometry | synthetic fixtures | world Y determines overlay order", 36, 80);
        panel(graphics, 35, "Underpass", "Red car Y=60 / green railway Y=80",
                flatTrack("upper", 80, 0), car(60, 60));
        panel(graphics, 505, "Overpass", "Red car Y=80 / green railway Y=60",
                flatTrack("lower", 60, 0), car(80, 80));
        slopePanel(graphics, 975);
        graphics.setColor(new Color(0x182633));
        graphics.drawString("XYZ is preserved in world coordinates. Car bodies keep their real footprint, pitch and roll.", 36, 620);
        graphics.drawString("This verifies overlay-to-overlay masking; terrain/building occlusion and in-game integration remain separate checks.", 36, 652);
        graphics.dispose();
        ImageIO.write(image, "png", new File(args[0]));
    }

    private static MapTrack flatTrack(String id, double y, double z) {
        return new MapTrack(id, List.of(new double[] {-22, z, y}, new double[] {22, z, y}));
    }

    private static MapCar car(double frontY, double backY) {
        return new MapCar(1, 0, 1, true, "model", "Red line", "Terminus", 0xD34B4B,
                new MapVertex(1.6, frontY, 17), new MapVertex(1.6, backY, -17),
                new MapVertex(-1.6, backY, -17), new MapVertex(-1.6, frontY, 17));
    }

    private static void panel(Graphics2D graphics, int left, String title, String subtitle,
            MapTrack track, MapCar car) {
        final var route = List.of(new TrackRoutePalette.Entry("g", "Green", 0x118451, RouteLineStyle.NORMAL));
        final var scene = HeightMapScene.build(List.of(track), Map.of(track.id, route),
                TrackPatternLayout.build(List.of(track)), List.of(car), true, true, 8, -24, -24, 24, 24);
        paint(graphics, left, title, subtitle, scene);
    }

    private static void slopePanel(Graphics2D graphics, int left) {
        final var first = flatTrack("a", 55, -9);
        final var second = flatTrack("b", 55, 9);
        final var route = List.of(new TrackRoutePalette.Entry("g", "Green", 0x118451, RouteLineStyle.NORMAL));
        final var tracks = List.of(first, second);
        final var scene = HeightMapScene.build(tracks, Map.of("a", route, "b", route),
                TrackPatternLayout.build(tracks), List.of(car(40, 80)), true, true, 8, -24, -24, 24, 24);
        paint(graphics, left, "Inclined car", "Car Y=40..80 / both crossings Y=55", scene);
    }

    private static void paint(Graphics2D graphics, int left, String title, String subtitle, HeightMapScene scene) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(left, 110, 430, 460, 14, 14);
        graphics.setColor(new Color(0x182633));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 21));
        graphics.drawString(title, left + 18, 145);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 15));
        graphics.drawString(subtitle, left + 18, 175);
        final double originX = left + 215, originZ = 365, scale = 8;
        graphics.setColor(new Color(0xE7EBEF));
        for (int i = -20; i <= 20; i += 5) {
            graphics.drawLine((int) (originX - 20 * scale), (int) (originZ + i * scale),
                    (int) (originX + 20 * scale), (int) (originZ + i * scale));
            graphics.drawLine((int) (originX + i * scale), (int) (originZ - 20 * scale),
                    (int) (originX + i * scale), (int) (originZ + 20 * scale));
        }
        // Minecraft's GUI colour quads are rasterized without per-polygon Java2D antialiasing.
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (HeightMapScene.Quad quad : scene.quads()) {
            final Path2D.Double polygon = new Path2D.Double();
            polygon.moveTo(originX + quad.a().x() * scale, originZ + quad.a().z() * scale);
            for (var p : List.of(quad.b(), quad.c(), quad.d())) polygon.lineTo(originX + p.x() * scale, originZ + p.z() * scale);
            polygon.closePath();
            graphics.setColor(new Color((quad.alpha() << 24) | quad.color(), true));
            graphics.fill(polygon);
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
}

import io.github.teamcreating.mtrmap.mapdata.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Headless visual check using the exact route geometry consumed by both Minecraft maps. */
public final class RouteStylePreview {
    public static void main(String[] args) throws Exception {
        final BufferedImage image = new BufferedImage(1280, 780, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0xF1F3F5));
        graphics.fillRect(0, 0, 1280, 780);
        graphics.setColor(new Color(0x182633));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 26));
        graphics.drawString("MTR Map Overlay / route styles", 44, 52);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 16));
        graphics.drawString("Actual shared geometry | 70% light rail / 100% rail / 100% high speed", 44, 83);
        final MapTrack straight = track("straight", 0, 120);
        row(graphics, 145, "Light rail", straight, List.of(entry("a", 0x137C59, RouteLineStyle.LIGHT_RAIL)));
        row(graphics, 215, "Rail", straight, List.of(entry("a", 0x137C59, RouteLineStyle.NORMAL)));
        row(graphics, 285, "High speed / green", straight, List.of(entry("a", 0x137C59, RouteLineStyle.HIGH_SPEED)));
        row(graphics, 355, "High speed / red", straight, List.of(entry("a", 0xCE3939, RouteLineStyle.HIGH_SPEED)));
        final List<TrackRoutePalette.Entry> mixed = List.of(entry("a", 0x277DC5, RouteLineStyle.LIGHT_RAIL),
                entry("b", 0xE39925, RouteLineStyle.NORMAL), entry("c", 0xCE3939, RouteLineStyle.HIGH_SPEED));
        row(graphics, 425, "Shared rail / mixed", straight, mixed);
        graphics.setColor(new Color(0x182633));
        graphics.drawString("Connected rails (second rail stored backwards): continuous block spacing", 44, 495);
        final MapTrack first = track("a", 0, 22);
        final MapTrack second = new MapTrack("b", List.of(new double[] {120, 4}, new double[] {90, 4},
                new double[] {75, 12}, new double[] {60, 12}, new double[] {45, 0}, new double[] {22, 0}));
        final var placements = TrackPatternLayout.build(List.of(second, first));
        for (MapTrack track : List.of(first, second)) {
            paint(graphics, track, List.of(entry("a", 0x137C59, RouteLineStyle.HIGH_SPEED)),
                    placements.get(track.id), 5, 280, 540);
        }
        graphics.setColor(new Color(0x182633));
        graphics.drawString("Zoomed out: block pattern simplifies to solid; no broken or flickering white pixels", 44, 680);
        paint(graphics, track("zoom", 0, 5000), List.of(entry("a", 0xCE3939, RouteLineStyle.HIGH_SPEED)),
                TrackPatternLayout.Placement.DEFAULT, 0.1, 280, 716);
        graphics.dispose();
        ImageIO.write(image, "png", new File(args[0]));
    }

    private static MapTrack track(String id, double a, double b) {
        return new MapTrack(id, List.of(new double[] {a, 0}, new double[] {b, 0}));
    }

    private static TrackRoutePalette.Entry entry(String id, int color, RouteLineStyle style) {
        return new TrackRoutePalette.Entry(id, id, color, style);
    }

    private static void row(Graphics2D graphics, int y, String label, MapTrack track,
            List<TrackRoutePalette.Entry> bands) {
        graphics.setColor(new Color(0x182633));
        graphics.drawString(label, 44, y + 5);
        paint(graphics, track, bands, TrackPatternLayout.Placement.DEFAULT, 5, 280, y);
    }

    private static void paint(Graphics2D graphics, MapTrack track, List<TrackRoutePalette.Entry> bands,
            TrackPatternLayout.Placement placement, double scale, double originX, double originY) {
        RouteRibbonGeometry.draw(track, bands, placement, scale, -100, -100, 5100, 100,
                (x1, z1, x2, z2, x3, z3, x4, z4, color, alpha) -> {
                    final Path2D.Double polygon = new Path2D.Double();
                    polygon.moveTo(originX + x1 * scale, originY + z1 * scale);
                    polygon.lineTo(originX + x2 * scale, originY + z2 * scale);
                    polygon.lineTo(originX + x3 * scale, originY + z3 * scale);
                    polygon.lineTo(originX + x4 * scale, originY + z4 * scale);
                    polygon.closePath();
                    graphics.setColor(new Color((alpha << 24) | color, true));
                    graphics.fill(polygon);
                });
    }
}

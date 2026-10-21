package com.lx862.mtrmap.mapdata;

import java.util.function.Consumer;

/** Map symbols inspired by Create's carriage sprites, without copying its textures.
 * Screen-space embellishments never change the measured MapCar or its tooltip. */
final class TrainCarGeometry {
    private static final int OUTLINE = 0x172431;
    private static final int ROOF = 0xF4EEDC;
    private static final int HEAD = 0xFFD166;

    static PlanarMapScene.Quad draw(MapCar car, double scale, Consumer<PlanarMapScene.Quad> sink) {
        final MapVertex front = car.frontRight().lerp(car.frontLeft(), .5);
        final MapVertex back = car.backRight().lerp(car.backLeft(), .5);
        final double dx = front.x() - back.x(), dz = front.z() - back.z();
        final double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) return null;
        final boolean marker = length * scale < 9;
        if (marker && !car.leading()) return null;
        final double width = Math.hypot(car.frontRight().x() - car.frontLeft().x(),
                car.frontRight().z() - car.frontLeft().z());
        // A seven-GUI-pixel minimum silhouette remains readable on same-colour routes.
        // Full car lengths remain proportional; inset the ends to separate coupled cars.
        final double w = marker ? 3.5 : Math.max(3.5, width * scale / 2 + .8);
        final double l = marker ? 6 : length * scale / 2 - .7;
        final Symbol symbol = new Symbol(car, sink, (front.x() + back.x()) / 2,
                (front.z() + back.z()) / 2, dx / length / scale, dz / length / scale);
        final double bevel = 1;
        // Three convex pieces form a chamfered light halo.
        final var middle = symbol.rect(-w, -l + bevel, w, l - bevel, 0xFFFFFF);
        symbol.quad(-w + bevel, -l, w - bevel, -l, w, -l + bevel, -w, -l + bevel, 0xFFFFFF);
        symbol.quad(-w, l - bevel, w, l - bevel, w - bevel, l, -w + bevel, l, 0xFFFFFF);
        symbol.rect(-w + .65, -l + .65, w - .65, l - .65, OUTLINE);
        symbol.rect(-w + 1.3, -l + 1.3, w - 1.3, l - 1.3, car.color());
        symbol.rect(-w * .36, -l + 2.3, w * .36, l - 2.3, ROOF);
        symbol.rect(-w + 1.4, -l + 1.5, w - 1.4, -l + 2.15, OUTLINE);
        symbol.rect(-w + 1.4, l - 2.15, w - 1.4, l - 1.5, OUTLINE);
        if (car.leading()) {
            final double direction = car.reversed() ? -1 : 1;
            // Arrow sits on the actual leading end, including single-car reverse services.
            symbol.quad(-1.15, direction * (l - 2.7), 1.15, direction * (l - 2.7),
                    0, direction * (l - .9), 0, direction * (l - .9), HEAD);
        }
        // One enclosing hit rectangle per symbol, including its halo and both end caps.
        final double ex = symbol.ux * bevel, ez = symbol.uz * bevel;
        return new PlanarMapScene.Quad(middle.x1() + ex, middle.z1() + ez,
                middle.x2() - ex, middle.z2() - ez, middle.x3() - ex, middle.z3() - ez,
                middle.x4() + ex, middle.z4() + ez, OUTLINE, 255, car);
    }

    private record Symbol(MapCar car, Consumer<PlanarMapScene.Quad> sink, double x, double z,
            double ux, double uz) {
        PlanarMapScene.Quad rect(double left, double back, double right, double front, int color) {
            return quad(right, front, right, back, left, back, left, front, color);
        }

        PlanarMapScene.Quad quad(double a, double b, double c, double d, double e, double f,
                double g, double h, int color) {
            final var q = new PlanarMapScene.Quad(x + a * uz + b * ux, z - a * ux + b * uz,
                    x + c * uz + d * ux, z - c * ux + d * uz,
                    x + e * uz + f * ux, z - e * ux + f * uz,
                    x + g * uz + h * ux, z - g * ux + h * uz, color, 255, car);
            sink.accept(q);
            return q;
        }
    }
}

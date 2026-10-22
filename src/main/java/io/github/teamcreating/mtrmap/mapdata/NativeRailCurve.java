package io.github.teamcreating.mtrmap.mapdata;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.RailMath;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Immutable native definition. No fitting a different spline through coarse network samples. */
public final class NativeRailCurve {
    public static final double PIXEL_ERROR = 0.08; // GUI pixels; 0.24 physical pixels at GUI scale 3.
    public static final int MAX_SEGMENTS = 8192;
    private final Position start, end;
    private final Angle angle1, angle2;
    private final Rail.Shape shape;
    private final double verticalRadius;
    private final int tiltPoints;
    private final double[] tilt;
    private final RailMath math;
    private final long[] bounds;

    private NativeRailCurve(Position start, Angle angle1, Position end, Angle angle2,
            Rail.Shape shape, double verticalRadius, int tiltPoints, double[] tilt, long[] bounds, RailMath existing) {
        if (!Double.isFinite(verticalRadius)) throw new IllegalArgumentException("Invalid vertical radius");
        if (tiltPoints < 0 || tiltPoints > 7) throw new IllegalArgumentException("Invalid tilt point count");
        for (double value : tilt) if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid rail tilt");
        this.start = start;
        this.end = end;
        this.angle1 = angle1;
        this.angle2 = angle2;
        this.shape = shape;
        this.verticalRadius = verticalRadius;
        this.tiltPoints = tiltPoints;
        this.tilt = tilt.clone();
        this.bounds = bounds.clone();
        if (bounds[0] > bounds[2] || bounds[1] > bounds[3]) throw new IllegalArgumentException("Invalid curve bounds");
        math = existing != null ? existing : new MapRailMath(start, angle1, end, angle2, shape, verticalRadius, tiltPoints,
                tilt[0], tilt[1], tilt[2], tilt[3], tilt[4], tilt[5], tilt[6], tilt[7], tilt[8], tilt[9], tilt[10]);
        if (!(math.getLength() > 0) || !Double.isFinite(math.getLength())) {
            throw new IllegalArgumentException("Invalid native rail length");
        }
    }

    public static NativeRailCurve from(Rail rail) {
        final var positions = new ObjectArraySet<Position>();
        rail.writePositions(positions); // Core inserts original position1 then position2.
        if (positions.size() != 2) throw new IllegalArgumentException("Rail requires two endpoints");
        final Position[] endpoints = positions.toArray(new Position[0]);
        final boolean reverse = endpoints[0].compareTo(endpoints[1]) > 0;
        final Position start = endpoints[reverse ? 1 : 0], end = endpoints[reverse ? 0 : 1];
        final double[] tilt = new double[] {
                    rail.getTiltAngleDegrees1(), rail.getTiltAngleDistance1a(), rail.getTiltAngleDegrees1a(),
                    rail.getTiltAngleDegrees1b(), rail.getTiltAngleDistance1b(), rail.getTiltAngleDegreesMiddle(),
                    rail.getTiltAngleDistance2b(), rail.getTiltAngleDegrees2b(), rail.getTiltAngleDegrees2a(),
                    rail.getTiltAngleDistance2a(), rail.getTiltAngleDegrees2()};
        if (reverse) {
            for (int i = 0; i < tilt.length / 2; i++) {
                final double value = tilt[i]; tilt[i] = tilt[tilt.length - 1 - i]; tilt[tilt.length - 1 - i] = value;
            }
        }
        return new NativeRailCurve(start, rail.getStartAngle(start), end, rail.getStartAngle(end),
                rail.railMath.getShape(), rail.railMath.getVerticalRadius(), rail.getTiltPoints(), tilt,
                new long[]{rail.railMath.minX, rail.railMath.minZ, rail.railMath.maxX, rail.railMath.maxZ}, rail.railMath);
    }

    public double length() { return math.getLength(); }
    public Vector position(double distance) { return math.getPosition(distance, false); }
    public double minX() { return bounds[0]; }
    public double minZ() { return bounds[1]; }
    public double maxX() { return bounds[2]; }
    public double maxZ() { return bounds[3]; }

    public double[] normal(double distance) {
        final double h = Math.max(1E-5, Math.min(0.001, length() * 1E-6));
        final Vector a = position(Math.max(0, distance - h)), b = position(Math.min(length(), distance + h));
        final double dx = b.x() - a.x(), dz = b.z() - a.z(), length = Math.hypot(dx, dz);
        return length > 1E-10 ? new double[] {-dz / length, dx / length} : new double[] {0, 0};
    }

    public MapTrack tessellate(String id, double scale) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid curve scale");
        final List<double[]> points = new ArrayList<>();
        final List<Double> distances = new ArrayList<>();
        final Vector start = position(0), end = position(length());
        points.add(point(start));
        distances.add(0.0);
        split(0, start, position(length() / 2), length(), end, PIXEL_ERROR / scale, 0, points, distances);
        final double[] nativeDistances = new double[distances.size()];
        for (int i = 0; i < distances.size(); i++) nativeDistances[i] = distances.get(i);
        return new MapTrack(id, points, this, nativeDistances);
    }

    private void split(double d0, Vector p0, Vector mid, double d1, Vector p1, double tolerance, int depth,
            List<double[]> points, List<Double> distances) {
        final double dm = (d0 + d1) / 2;
        final Vector q1 = position((d0 + dm) / 2), q3 = position((dm + d1) / 2);
        // Quarter checks detect S bends whose midpoint lies exactly on the chord.
        final double error = Math.max(deviation(mid, p0, p1), Math.max(deviation(q1, p0, p1), deviation(q3, p0, p1)));
        if (error > tolerance && depth < 20 && points.size() < MAX_SEGMENTS - 20) {
            split(d0, p0, q1, dm, mid, tolerance, depth + 1, points, distances);
            split(dm, mid, q3, d1, p1, tolerance, depth + 1, points, distances);
        } else {
            points.add(point(p1));
            distances.add(d1);
        }
    }

    private static double deviation(Vector p, Vector a, Vector b) {
        final double dx = b.x() - a.x(), dz = b.z() - a.z(), squared = dx * dx + dz * dz;
        final double t = squared > 0 ? Math.max(0, Math.min(1, ((p.x() - a.x()) * dx + (p.z() - a.z()) * dz) / squared)) : 0;
        return Math.hypot(p.x() - a.x() - t * dx, p.z() - a.z() - t * dz);
    }

    private static double[] point(Vector p) { return new double[] {p.x(), p.z(), p.y()}; }

    public void write(DataOutputStream out) throws IOException {
        for (Position p : new Position[] {start, end}) {
            out.writeLong(p.getX()); out.writeLong(p.getY()); out.writeLong(p.getZ());
        }
        out.writeByte(angle1.ordinal()); out.writeByte(angle2.ordinal()); out.writeByte(shape.ordinal());
        out.writeDouble(verticalRadius); out.writeInt(tiltPoints);
        for (double value : tilt) out.writeDouble(value);
        for (long value : bounds) out.writeLong(value);
    }

    public static NativeRailCurve read(DataInputStream in) throws IOException {
        final Position start = new Position(in.readLong(), in.readLong(), in.readLong());
        final Position end = new Position(in.readLong(), in.readLong(), in.readLong());
        final int a = in.readUnsignedByte(), b = in.readUnsignedByte(), shape = in.readUnsignedByte();
        if (a >= Angle.values().length || b >= Angle.values().length || shape >= Rail.Shape.values().length) {
            throw new IOException("Invalid native rail enum");
        }
        final double radius = in.readDouble();
        final int count = in.readInt();
        final double[] tilt = new double[11];
        for (int i = 0; i < tilt.length; i++) tilt[i] = in.readDouble();
        final long[] bounds = new long[4];
        for (int i = 0; i < bounds.length; i++) bounds[i] = in.readLong();
        try {
            return new NativeRailCurve(start, Angle.values()[a], end, Angle.values()[b], Rail.Shape.values()[shape], radius, count, tilt, bounds, null);
        } catch (RuntimeException e) {
            throw new IOException("Invalid native rail geometry", e);
        }
    }

    /** Core's constructor calls render at 0.1-block intervals solely for bounds.
     * Reuse the authoritative exported bounds and bound that redundant scan;
     * getPosition/getLength retain Core's exact analytic implementation. */
    private static final class MapRailMath extends RailMath {
        MapRailMath(Position a, Angle aa, Position b, Angle ab, Rail.Shape shape, double radius, int count,
                double t0, double t1, double t2, double t3, double t4, double t5, double t6, double t7,
                double t8, double t9, double t10) {
            super(a, aa, b, ab, shape, radius, count, t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10);
        }

        @Override public void render(RenderRail callback, double interval, float offset1, float offset2) {
            super.render(callback, Math.max(interval, getLength() / 256), offset1, offset2);
        }
    }
}

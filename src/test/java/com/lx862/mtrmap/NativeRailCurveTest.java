package com.lx862.mtrmap;

import com.lx862.mtrmap.mapdata.*;
import com.lx862.mtrmap.network.NetworkSnapshotCodec;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NativeRailCurveTest {
    static Rail rail(long x, long z, long dx, long dz, long y1, long y2) {
        return Rail.newRail(new Position(x, y1, z), Angle.E, new Position(x + dx, y2, z + dz), Angle.W,
                Rail.Shape.QUADRATIC, 0,
                new ObjectArrayList<>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
    }

    @Test void nativeDefinitionRoundTripsTheActualCurveIncludingXYZAndEndpointOrder() throws Exception {
        for (Rail rail : List.of(rail(0, 0, 100, 80, -40, 50), rail(29_000_000, -23_000_000, -100, 80, 40, -20))) {
            assertTrue(rail.isValid());
            final var curve = NativeRailCurve.from(rail);
            final var bytes = new ByteArrayOutputStream();
            curve.write(new DataOutputStream(bytes));
            final var copy = NativeRailCurve.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            assertEquals(183, bytes.size());
            assertEquals(rail.railMath.getLength(), copy.length(), 1E-8);
            for (int i = 0; i <= 1000; i++) {
                final double d = copy.length() * i / 1000;
                final Vector nativePoint = rail.railMath.getPosition(d, false), p = copy.position(d);
                assertEquals(nativePoint.x(), p.x(), 1E-8);
                assertEquals(nativePoint.y(), p.y(), 1E-8);
                assertEquals(nativePoint.z(), p.z(), 1E-8);
            }
        }
    }

    @Test void allNativeShapesKeepTheirVerticalGeometryAfterDecode() throws Exception {
        for (var shape : Rail.Shape.values()) {
            final var rail = Rail.newRail(new Position(-200, -50, 20), Angle.E, new Position(100, 60, 150), Angle.W,
                    shape, 50,
                    new ObjectArrayList<>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
            final var bytes = new ByteArrayOutputStream();
            NativeRailCurve.from(rail).write(new DataOutputStream(bytes));
            final var copy = NativeRailCurve.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            for (int i = 0; i <= 1000; i++) {
                final double d = copy.length() * i / 1000;
                final Vector p = rail.railMath.getPosition(d, false), q = copy.position(d);
                assertEquals(p.x(), q.x(), 1E-8, shape.name());
                assertEquals(p.y(), q.y(), 1E-8, shape.name());
                assertEquals(p.z(), q.z(), 1E-8, shape.name());
            }
            assertEquals(rail.railMath.minX, copy.minX());
            assertEquals(rail.railMath.maxZ, copy.maxZ());
        }
    }

    @Test void sBendUsesNativeZoomAdaptiveGeometryWithinPixelTolerance() {
        final var rail = rail(0, 0, 100, 80, -40, 50);
        final MapTrack source = TrackSampler.track(rail);
        int previous = 0;
        for (double scale : new double[]{0.25, 1, 8, 32}) {
            final MapTrack mesh = source.forScale(scale);
            assertTrue(mesh.points.size() >= previous);
            previous = mesh.points.size();
            assertEquals(source.length(), mesh.length(), 1E-8); // stripe phase cannot drift with zoom.
            int segment = 0;
            int coarseSegment = 0;
            double worst = 0, coarseWorst = 0;
            for (int i = 0; i <= 10000; i++) {
                final double d = source.length() * i / 10000;
                while (segment + 2 < mesh.points.size() && d > mesh.distances[segment + 1]) segment++;
                final Vector p = rail.railMath.getPosition(d, false);
                worst = Math.max(worst, chordError(p, mesh.points.get(segment), mesh.points.get(segment + 1)) * scale);
                while (coarseSegment + 2 < source.points.size() && d > source.distances[coarseSegment + 1]) coarseSegment++;
                coarseWorst = Math.max(coarseWorst, chordError(p, source.points.get(coarseSegment), source.points.get(coarseSegment + 1)) * scale);
            }
            System.out.printf(java.util.Locale.ROOT, "[Curve] scale=%.2f coarse=%d adaptive=%d coarseError=%.6f newError=%.6f GUIpx%n",
                    scale, source.points.size(), mesh.points.size(), coarseWorst, worst);
            assertTrue(worst <= NativeRailCurve.PIXEL_ERROR * 1.01, "screen error=" + worst + " at scale=" + scale);
            for (int i = 0; i < mesh.points.size(); i++) {
                final Vector expected = rail.railMath.getPosition(mesh.distances[i], false);
                assertEquals(expected.x(), mesh.points.get(i)[0], 1E-8);
                assertEquals(expected.z(), mesh.points.get(i)[1], 1E-8);
                assertEquals(expected.y(), mesh.height(i), 1E-8);
            }
        }
        assertTrue(previous > source.points.size(), "Zoom must recover actual curve detail absent from coarse points");
    }

    @Test void arbitrarilyLongStraightRailNeedsOnlyTwoVertices() {
        final var straight = NativeRailCurve.from(rail(0, 0, 100_000, 0, -40, 80));
        assertEquals(2, straight.tessellate("long", 32).points.size());
    }

    @Test void protocolEightRetainsNativeCurvesForFullNetworkClients() throws Exception {
        final var dimension = new NetworkSnapshotCodec.PendingDimension("minecraft/overworld", 1);
        final var track = TrackSampler.track(rail(0, 0, 100, 80, -40, 50));
        dimension.tracks.add(track);
        final var bytes = new ByteArrayOutputStream();
        NetworkSnapshotCodec.writeDimensionList(new DataOutputStream(bytes), List.of(dimension));
        final var copy = NetworkSnapshotCodec.readDimensionList(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))
                .get(0).tracks.get(0);
        assertNotNull(copy.curve);
        assertEquals(track.length(), copy.length(), 1E-8);
        assertEquals(track.forScale(32).points.size(), copy.forScale(32).points.size());
    }

    @Test void nativeCurveDetailRemainsCachedWhenOnlyCarsChange() {
        final var track = TrackSampler.track(rail(0, 0, 100, 80, -40, 50));
        final var cache = new PlanarMapCache();
        final Object snapshot = new Object();
        final var first = cache.get(snapshot, List.of(track), Map.of(), Map.of(), List.of(), true, false, 8, -100, -100, 200, 200);
        final var second = cache.get(snapshot, List.of(track), Map.of(), Map.of(), new java.util.ArrayList<>(), true, false, 8, -100, -100, 200, 200);
        assertSame(first.staticQuads(), second.staticQuads());
        assertEquals(1, cache.staticBuilds());
        assertEquals(1, cache.curveBuilds());
        cache.get(snapshot, List.of(track), Map.of(), Map.of(), List.of(), true, false, 7, -100, -100, 200, 200);
        assertEquals(2, cache.staticBuilds());
        assertEquals(1, cache.curveBuilds(), "Smooth zoom within the same error-safe LOD reuses native detail");
        cache.get(snapshot, List.of(track), Map.of(), Map.of(), List.of(), true, false, 9, -100, -100, 200, 200);
        assertEquals(2, cache.curveBuilds());
    }

    @Test void thinRailFringesFadeOutwardsAndKeepConsistentWinding() {
        final var track = TrackSampler.track(rail(0, 0, 100, 80, -40, 50));
        final var scene = PlanarMapScene.build(List.of(track), Map.of(), Map.of(), List.of(),
                true, false, 8, -100, -100, 200, 200);
        final var edges = scene.quads().stream().filter(PlanarMapScene.Quad::feather).toList();
        assertFalse(edges.isEmpty());
        for (var edge : edges) {
            assertEquals(0, edge.vertexAlpha(0)); assertEquals(0, edge.vertexAlpha(1));
            assertEquals(255, edge.vertexAlpha(2)); assertEquals(255, edge.vertexAlpha(3));
            final double area = (edge.x2()-edge.x1())*(edge.z3()-edge.z1())
                    - (edge.z2()-edge.z1())*(edge.x3()-edge.x1());
            assertTrue(area < 0, "Both fringe sides must survive the GUI's face culling");
        }
    }

    @Test void invalidNativeDefinitionsAreRejected() throws Exception {
        final var bytes = new ByteArrayOutputStream();
        NativeRailCurve.from(rail(0, 0, 100, 80, -40, 50)).write(new DataOutputStream(bytes));
        final byte[] bad = bytes.toByteArray();
        bad[48] = (byte) 255; // angle enum after the two exact XYZ endpoints.
        assertThrows(IOException.class, () -> NativeRailCurve.read(new DataInputStream(new ByteArrayInputStream(bad))));
    }

    private static double chordError(Vector p, double[] a, double[] b) {
        final double dx = b[0] - a[0], dz = b[1] - a[1];
        final double t = Math.max(0, Math.min(1, ((p.x() - a[0]) * dx + (p.z() - a[1]) * dz) / (dx * dx + dz * dz)));
        return Math.hypot(p.x() - a[0] - t * dx, p.z() - a[1] - t * dz);
    }
}

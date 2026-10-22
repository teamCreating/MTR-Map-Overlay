package com.lx862.mtrmap.train;

import java.util.List;
import java.util.function.Consumer;
import static com.lx862.mtrmap.train.TrainSnapshot.*;

/** Conservative swept X/Z bounds, rebuilt at packet rate, never a simulation.
 * The hierarchy only filters drawing; complete world membership stays in the buffer. */
final class TrainViewportIndex {
    record Entry(Pose before, Pose current, Definition definition,
            double minX, double minZ, double maxX, double maxZ) {
        Entry(Pose before, Pose current, Definition definition) {
            this(before, current, definition, bound(before, current, definition, 0, false),
                    bound(before, current, definition, 2, false), bound(before, current, definition, 0, true),
                    bound(before, current, definition, 2, true));
        }

        private static double bound(Pose a, Pose b, Definition definition, int axis, boolean maximum) {
            double result = maximum ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            for (int i = 0; i < b.cars(); i++) {
                Car car = definition.cars().get(i);
                // Both native anchor endpoints plus any possible rigid-body orientation/offset.
                double radius = car.length() / 2 + car.width() / 2 + Math.abs(car.offset());
                for (int k = i * 6 + axis; k <= i * 6 + axis + 3; k += 3) {
                    double value = maximum ? Math.max(a.coordinate(k), b.coordinate(k)) + radius
                            : Math.min(a.coordinate(k), b.coordinate(k)) - radius;
                    result = maximum ? Math.max(result, value) : Math.min(result, value);
                }
            }
            return result;
        }
    }

    private final Entry[] entries;
    private final Node root;

    TrainViewportIndex(List<Entry> entries) {
        this.entries = entries.toArray(Entry[]::new);
        root = entries.isEmpty() ? null : build(0, entries.size());
    }

    void visit(double minX, double minZ, double maxX, double maxZ, Consumer<Entry> visitor) {
        if (root != null) visit(root, minX, minZ, maxX, maxZ, visitor);
    }

    private static boolean intersects(double minX, double minZ, double maxX, double maxZ,
            double left, double top, double right, double bottom) {
        return maxX >= left && minX <= right && maxZ >= top && minZ <= bottom;
    }

    private void visit(Node node, double minX, double minZ, double maxX, double maxZ, Consumer<Entry> visitor) {
        if (!intersects(node.minX, node.minZ, node.maxX, node.maxZ, minX, minZ, maxX, maxZ)) return;
        if (node.left == null) {
            for (int i = node.start; i < node.end; i++) {
                Entry e = entries[i];
                if (intersects(e.minX, e.minZ, e.maxX, e.maxZ, minX, minZ, maxX, maxZ)) visitor.accept(e);
            }
        } else {
            visit(node.left, minX, minZ, maxX, maxZ, visitor);
            visit(node.right, minX, minZ, maxX, maxZ, visitor);
        }
    }

    private Node build(int start, int end) {
        Node node = new Node(start, end);
        for (int i = start; i < end; i++) {
            Entry e = entries[i];
            node.minX = Math.min(node.minX, e.minX); node.minZ = Math.min(node.minZ, e.minZ);
            node.maxX = Math.max(node.maxX, e.maxX); node.maxZ = Math.max(node.maxZ, e.maxZ);
        }
        int count = end - start;
        if (count <= 8) return node;
        boolean x = node.maxX - node.minX >= node.maxZ - node.minZ;
        double split = x ? (node.minX + node.maxX) / 2 : (node.minZ + node.maxZ) / 2;
        int middle = start;
        for (int i = start; i < end; i++) {
            Entry e = entries[i];
            if ((x ? (e.minX + e.maxX) / 2 : (e.minZ + e.maxZ) / 2) < split) {
                Entry swap = entries[middle]; entries[middle++] = e; entries[i] = swap;
            }
        }
        // Bound depth and construction work even for identical/strongly clustered positions.
        if (middle - start < count / 4 || end - middle < count / 4) middle = start + count / 2;
        node.left = build(start, middle); node.right = build(middle, end);
        return node;
    }

    private static final class Node {
        final int start, end;
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        Node left, right;
        Node(int start, int end) { this.start = start; this.end = end; }
    }
}

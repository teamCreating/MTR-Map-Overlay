package io.github.teamcreating.mtrmap.train;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static io.github.teamcreating.mtrmap.train.TrainSnapshot.*;

/** Independent, bounded binary codec. High-frequency frames contain no strings. */
public final class TrainFrameCodec {
    private TrainFrameCodec() { }
    public static byte[] frame(Frame frame) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeLong(frame.sequence()); out.writeLong(frame.coreMillis()); out.writeLong(frame.captureMillis());
        number(out, frame.trains().size());
        for (Pose p : frame.trains()) {
            number(out, p.handle); number(out, p.definition);
            out.writeByte((p.reversed ? 1 : 0) | (p.manual ? 2 : 0));
            number(out, p.cars());
            if (p.cars() == 0) continue;
            out.writeDouble(p.x); out.writeDouble(p.y); out.writeDouble(p.z);
            for (int i = 0; i < p.cars() * 6; i++) out.writeFloat(p.relative(i));
        }
        return bounded(bytes);
    }

    public static Frame frame(byte[] bytes) throws IOException {
        var in = input(bytes);
        try {
            long seq = in.readLong(), core = in.readLong(), capture = in.readLong();
            int n = count(in, MAX_TRAINS, 4);
            var poses = new ArrayList<Pose>(n);
            int total = 0;
            for (int j = 0; j < n; j++) {
                int id = number(in), definition = number(in), flags = in.readUnsignedByte();
                if ((flags & ~3) != 0) throw new IOException("Unknown pose flags");
                int cars = count(in, MAX_CARS, 24);
                if ((total += cars) > MAX_TOTAL_CARS) throw new IOException("Too many cars");
                double x = 0, y = 0, z = 0;
                if (cars > 0) { x = in.readDouble(); y = in.readDouble(); z = in.readDouble(); }
                float[] anchors = new float[cars * 6];
                for (int k = 0; k < anchors.length; k++) anchors[k] = in.readFloat();
                poses.add(new Pose(id, definition, (flags & 1) != 0, (flags & 2) != 0, x, y, z, anchors));
            }
            end(in);
            return new Frame(seq, core, capture, poses);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid train data", e); }
    }

    public static byte[] definitions(List<NamedDefinition> definitions) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        number(out, definitions.size());
        for (var named : definitions) {
            number(out, named.id()); var d = named.value();
            text(out, d.route()); text(out, d.destination()); out.writeInt(d.color()); number(out, d.cars().size());
            for (Car car : d.cars()) {
                text(out, car.model()); out.writeFloat(car.length()); out.writeFloat(car.width()); out.writeFloat(car.offset());
            }
        }
        return bounded(bytes);
    }

    public static List<NamedDefinition> definitions(byte[] bytes) throws IOException {
        var in = input(bytes);
        try {
            int n = count(in, MAX_TRAINS, 10), total = 0;
            var result = new ArrayList<NamedDefinition>(n); var ids = new HashSet<Integer>();
            for (int j = 0; j < n; j++) {
                int id = number(in); if (!ids.add(id)) throw new IOException("Duplicate definition");
                String route = text(in), destination = text(in); int color = in.readInt();
                int cars = count(in, MAX_CARS, 13);
                if ((total += cars) > MAX_TOTAL_CARS) throw new IOException("Too many definition cars");
                var list = new ArrayList<Car>(cars);
                for (int k = 0; k < cars; k++) list.add(new Car(text(in), in.readFloat(), in.readFloat(), in.readFloat()));
                result.add(new NamedDefinition(id, new Definition(list, route, destination, color)));
            }
            end(in); return List.copyOf(result);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid definition", e); }
    }

    /** Merge pre-encoded singleton definitions without serializing strings on the server tick. */
    public static byte[] mergeDefinitions(List<byte[]> singletons) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        number(out, singletons.size());
        for (byte[] singleton : singletons) {
            if (singleton.length < 2 || singleton[0] != 1) throw new IOException("Not a singleton definition");
            if (bytes.size() + singleton.length - 1 > MAX_BYTES) throw new IOException("Definition batch too large");
            out.write(singleton, 1, singleton.length - 1);
        }
        return bounded(bytes);
    }

    private static byte[] bounded(ByteArrayOutputStream bytes) throws IOException {
        if (bytes.size() > MAX_BYTES) throw new IOException("Train transfer exceeds limit");
        return bytes.toByteArray();
    }
    private static DataInputStream input(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Train transfer exceeds limit");
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    private static int count(DataInputStream in, int max, int minBytes) throws IOException {
        int n = number(in);
        if (n > max || n > in.available() / minBytes) throw new IOException("Impossible train count");
        return n;
    }
    private static void end(DataInputStream in) throws IOException { if (in.available() != 0) throw new IOException("Trailing train data"); }
    private static void text(DataOutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 16_384) throw new IOException("Oversize train text");
        number(out, data.length); out.write(data);
    }
    private static String text(DataInputStream in) throws IOException {
        int n = count(in, 16_384, 1); return new String(in.readNBytes(n), StandardCharsets.UTF_8);
    }
    private static void number(DataOutputStream out, int value) throws IOException {
        if (value < 0) throw new IOException("Negative integer");
        while ((value & ~127) != 0) { out.writeByte((value & 127) | 128); value >>>= 7; }
        out.writeByte(value);
    }
    private static int number(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            if (shift == 28 && (b & 248) != 0) throw new IOException("Integer overflow");
            value |= (b & 127) << shift;
            if ((b & 128) == 0) return value;
        }
        throw new IOException("Invalid integer");
    }
}

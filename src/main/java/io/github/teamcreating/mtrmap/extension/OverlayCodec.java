package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Schema-directed record codec: wire data never chooses a Java class. Bounded at every level. */
public final class OverlayCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private OverlayCodec() {}
    public static byte[] encode(Record value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new OutputStream() {
            private void capacity(int n) throws IOException { if (n > MAX_BYTES - bytes.size()) throw new IOException("Overlay response too large"); }
            @Override public void write(int value) throws IOException { capacity(1); bytes.write(value); }
            @Override public void write(byte[] data, int offset, int length) throws IOException { capacity(length); bytes.write(data, offset, length); }
        })) { write(out, value, value.getClass(), 0); }
        if (bytes.size() > MAX_BYTES) throw new IOException("Overlay response too large");
        return bytes.toByteArray();
    }
    public static <T> T decode(byte[] bytes, Class<T> type) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Overlay response too large");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Object value = read(in, type, 0, new int[]{200000});
            if (in.available() != 0) throw new IOException("Trailing overlay bytes");
            return type.cast(value);
        }
    }
    private static void check(int depth) throws IOException { if (depth > 20) throw new IOException("Overlay nesting too deep"); }
    private static void write(DataOutputStream out, Object value, Type type, int depth) throws IOException {
        check(depth);
        if (type instanceof ParameterizedType p) {
            Type[] args = p.getActualTypeArguments();
            if (p.getRawType() == Optional.class) { var v = (Optional<?>) value; out.writeBoolean(v.isPresent()); if (v.isPresent()) write(out, v.get(), args[0], depth + 1); }
            else if (p.getRawType() == Map.class) {
                var map = (Map<?, ?>) value; count(out, map.size());
                for (var entry : map.entrySet()) { write(out, entry.getKey(), args[0], depth + 1); write(out, entry.getValue(), args[1], depth + 1); }
            } else {
                var list = (Collection<?>) value; count(out, list.size()); for (Object item : list) write(out, item, args[0], depth + 1);
            }
            return;
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) { if (((String) value).length() > 8192) throw new IOException("Text exceeds capacity"); out.writeUTF((String) value); }
        else if (c == int.class || c == Integer.class) out.writeInt((Integer) value);
        else if (c == long.class) out.writeLong((Long) value);
        else if (c == double.class) out.writeDouble((Double) value);
        else if (c == boolean.class) out.writeBoolean((Boolean) value);
        else if (c == UUID.class) { out.writeLong(((UUID) value).getMostSignificantBits()); out.writeLong(((UUID) value).getLeastSignificantBits()); }
        else if (c.isEnum()) out.writeUTF(((Enum<?>) value).name());
        else if (c.isRecord() && c.getEnclosingClass() == MtrMapApi.class) {
            try { for (var component : c.getRecordComponents()) write(out, component.getAccessor().invoke(value), component.getGenericType(), depth + 1); }
            catch (ReflectiveOperationException e) { throw new IOException(e); }
        } else throw new IOException("Unsupported schema " + c);
    }
    private static void count(DataOutputStream out, int count) throws IOException { if (count > 20000) throw new IOException("Collection exceeds capacity"); out.writeInt(count); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object read(DataInputStream in, Type type, int depth, int[] remaining) throws IOException {
        check(depth); if (--remaining[0] < 0) throw new IOException("Overlay object budget exceeded");
        if (type instanceof ParameterizedType p) {
            Type[] args = p.getActualTypeArguments();
            if (p.getRawType() == Optional.class) return in.readBoolean() ? Optional.of(read(in, args[0], depth + 1, remaining)) : Optional.empty();
            int n = in.readInt(); if (n < 0 || n > 20000 || n > remaining[0]) throw new IOException("Invalid collection count");
            if (p.getRawType() == Map.class) {
                var map = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) { Object key = read(in, args[0], depth + 1, remaining); if (map.put(key, read(in, args[1], depth + 1, remaining)) != null) throw new IOException("Duplicate map key"); }
                return Map.copyOf(map);
            }
            var list = new ArrayList<>(); for (int i = 0; i < n; i++) list.add(read(in, args[0], depth + 1, remaining));
            return p.getRawType() == Set.class ? Set.copyOf(list) : List.copyOf(list);
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) { String text = in.readUTF(); if (text.length() > 8192) throw new IOException("Text exceeds capacity"); return text; }
        if (c == int.class || c == Integer.class) return in.readInt();
        if (c == long.class) return in.readLong();
        if (c == double.class) return in.readDouble();
        if (c == boolean.class) return in.readBoolean();
        if (c == UUID.class) return new UUID(in.readLong(), in.readLong());
        try {
            if (c.isEnum()) return Enum.valueOf((Class) c, in.readUTF());
            if (!c.isRecord() || c.getEnclosingClass() != MtrMapApi.class) throw new IOException("Unsupported schema");
            var components = c.getRecordComponents(); var values = new Object[components.length]; var types = new Class<?>[values.length];
            for (int i = 0; i < values.length; i++) { values[i] = read(in, components[i].getGenericType(), depth + 1, remaining); types[i] = components[i].getType(); }
            return c.getConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException | IllegalArgumentException e) { throw new IOException("Invalid overlay data", e); }
    }
}

package com.lx862.mtrsurveyor.network;

import com.lx862.mtrsurveyor.mapdata.MapDataCache;
import com.lx862.mtrsurveyor.mapdata.MapRoute;
import com.lx862.mtrsurveyor.mapdata.MapTrack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * S2C: one chunk of a full-network map snapshot. The payload is a byte slice
 * of a self-describing binary dump; the last chunk triggers reassembly.
 */
public record NetworkSyncChunk(int transferId, short chunkIndex, short totalChunks, long snapshotHash, byte[] data)
        implements CustomPacketPayload {

    public static final Type<NetworkSyncChunk> TYPE =
            new Type<>(MTRNetwork.id("network_sync_chunk"));

    public static final StreamCodec<FriendlyByteBuf, NetworkSyncChunk> STREAM_CODEC =
            StreamCodec.of(NetworkSyncChunk::write, NetworkSyncChunk::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void write(FriendlyByteBuf buf, NetworkSyncChunk msg) {
        buf.writeVarInt(msg.transferId());
        buf.writeShort(msg.chunkIndex());
        buf.writeShort(msg.totalChunks());
        buf.writeLong(msg.snapshotHash());
        buf.writeByteArray(msg.data());
    }

    public static NetworkSyncChunk read(FriendlyByteBuf buf) {
        final int transferId = buf.readVarInt();
        final short chunkIndex = buf.readShort();
        final short totalChunks = buf.readShort();
        final long snapshotHash = buf.readLong();
        final byte[] data = buf.readByteArray();
        return new NetworkSyncChunk(transferId, chunkIndex, totalChunks, snapshotHash, data);
    }

    public static void handle(NetworkSyncChunk msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientNetworkSync.onChunkReceived(msg));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Payload codec (shared shape between writer and reader)
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Payload layout:
     * <pre>
     * int dimensionCount
     * per dimension:
     *   UTF dimensionId, long snapshotHash
     *   int routeCount
     *   per route: UTF name, int color, boolean circular, int stopCount,
     *              per stop: float x, float z, UTF stationName, UTF destination,
     *              int routeTrackCount,
     *              per route track: int pointCount, per point: float x, float z
     *   int trackCount
     *   per track: int pointCount, per point: float x, float z
     * </pre>
     */
    public static void writeDimensionList(DataOutputStream out,
            List<PendingDimension> dimensions) throws IOException {
        out.writeInt(dimensions.size());
        for (PendingDimension dimension : dimensions) {
            out.writeUTF(dimension.dimensionId);
            out.writeLong(dimension.snapshotHash);
            out.writeInt(dimension.routes.size());
            for (MapRoute route : dimension.routes) {
                out.writeUTF(route.name == null ? "" : route.name);
                out.writeInt(route.color);
                out.writeBoolean(route.circular);
                out.writeInt(route.stops.size());
                for (MapRoute.Stop stop : route.stops) {
                    out.writeFloat((float) stop.x);
                    out.writeFloat((float) stop.z);
                    out.writeUTF(stop.stationName == null ? "" : stop.stationName);
                    out.writeUTF(stop.destination == null ? "" : stop.destination);
                }
                out.writeInt(route.tracks.size());
                for (MapTrack track : route.tracks) {
                    out.writeInt(track.points.size());
                    for (double[] point : track.points) {
                        out.writeFloat((float) point[0]);
                        out.writeFloat((float) point[1]);
                    }
                }
            }
            out.writeInt(dimension.tracks.size());
            for (MapTrack track : dimension.tracks) {
                out.writeInt(track.points.size());
                for (double[] point : track.points) {
                    out.writeFloat((float) point[0]);
                    out.writeFloat((float) point[1]);
                }
            }
        }
    }

    public static List<MapDataCache.DimensionData> readDimensionList(DataInputStream in) throws IOException {
        final int dimensionCount = in.readInt();
        final List<MapDataCache.DimensionData> result = new ArrayList<>(dimensionCount);
        for (int d = 0; d < dimensionCount; d++) {
            final String dimensionId = in.readUTF();
            final long snapshotHash = in.readLong();

            final int routeCount = in.readInt();
            final List<MapRoute> routes = new ArrayList<>(routeCount);
            for (int r = 0; r < routeCount; r++) {
                final String name = in.readUTF();
                final int color = in.readInt();
                final boolean circular = in.readBoolean();
                final int stopCount = in.readInt();
                final List<MapRoute.Stop> stops = new ArrayList<>(stopCount);
                for (int s = 0; s < stopCount; s++) {
                    final float x = in.readFloat();
                    final float z = in.readFloat();
                    final String stationName = in.readUTF();
                    final String destination = in.readUTF();
                    stops.add(new MapRoute.Stop(x, z, stationName, destination));
                }
                final int routeTrackCount = in.readInt();
                final List<MapTrack> routeTracks = new ArrayList<>(routeTrackCount);
                for (int t = 0; t < routeTrackCount; t++) {
                    final int pointCount = in.readInt();
                    final List<double[]> points = new ArrayList<>(pointCount);
                    for (int p = 0; p < pointCount; p++) {
                        points.add(new double[]{in.readFloat(), in.readFloat()});
                    }
                    routeTracks.add(new MapTrack(points));
                }
                routes.add(new MapRoute(name, color, circular, stops, routeTracks));
            }

            final int trackCount = in.readInt();
            final List<MapTrack> tracks = new ArrayList<>(trackCount);
            for (int t = 0; t < trackCount; t++) {
                final int pointCount = in.readInt();
                final List<double[]> points = new ArrayList<>(pointCount);
                for (int p = 0; p < pointCount; p++) {
                    points.add(new double[]{in.readFloat(), in.readFloat()});
                }
                tracks.add(new MapTrack(points));
            }

            result.add(new MapDataCache.DimensionData(dimensionId, routes, tracks, snapshotHash));
        }
        return result;
    }

    /** One dimension's worth of data pending serialization. */
    public static class PendingDimension {

        public final String dimensionId;
        public final long snapshotHash;
        public final List<MapRoute> routes = new ArrayList<>();
        public final List<MapTrack> tracks = new ArrayList<>();
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet realPathRouteIds =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

        public PendingDimension(String dimensionId, long snapshotHash) {
            this.dimensionId = dimensionId;
            this.snapshotHash = snapshotHash;
        }

        /** Mark a route as covered by a real depot driving path. */
        public void markRealPath(long routeId) {
            realPathRouteIds.add(routeId);
        }

        public boolean hasRealPath(long routeId) {
            return realPathRouteIds.contains(routeId);
        }
    }

    static DataInputStream wrapForRead(byte[] payload) {
        return new DataInputStream(new ByteArrayInputStream(payload));
    }
}

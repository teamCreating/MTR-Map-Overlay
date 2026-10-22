package com.lx862.mtrmap.train;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import com.lx862.mtrmap.mapdata.ClientTrainCollector;
import com.lx862.mtrmap.mapdata.MapCar;
import com.lx862.mtrmap.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.*;
import static com.lx862.mtrmap.train.TrainSnapshot.*;

/** A single shared full-map subscription. The render hook only records interest, never sends packets. */
public final class ClientTrainStream {
    private static final TrainAnchorBuffer buffer = new TrainAnchorBuffer();
    private record Transfer(byte kind, long sequence) { }
    private static final Map<Transfer, TrainFrameAssembly> transfers = new HashMap<>();
    private static final ThreadPoolExecutor decoder = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), runnable -> { var t = new Thread(runnable, "MTRMap-train-decoder"); t.setDaemon(true); return t; });
    private static long generation, lastSeen, lastRequest, retryAt;
    private static String viewed, subscribed;
    private static Object screen;
    private static UUID epoch;
    private static boolean unsupported, accepted, error;
    private static String errorMessage = "";
    private static long cachedTime, cachedSequence;
    private static double cachedMinX, cachedMinZ, cachedMaxX, cachedMaxZ, cachedScale;
    private static List<MapCar> cached = List.of();
    private static long lastDiagnostic;
    private ClientTrainStream() { }

    public static void touch(String dimension) {
        if (dimension == null) return;
        viewed = dimension; lastSeen = System.nanoTime(); screen = Minecraft.getInstance().screen;
    }

    public static void tick() {
        var mc = Minecraft.getInstance(); long now = System.nanoTime();
        boolean visible = mc.getConnection() != null && mc.player != null && mc.screen != null && mc.screen == screen
                && now - lastSeen < 2_000_000_000L && MTRMapConfig.INSTANCE.enabled.get()
                && MTRMapConfig.INSTANCE.trainCarsEnabled.get() && MTRMapConfig.INSTANCE.networkSyncEnabled.get();
        if (!visible) { if (subscribed != null) cancel(); return; }
        if (!MTRNetworkClient.canSendTrains()) { unsupported = true; return; }
        unsupported = false;
        if (!Objects.equals(viewed, subscribed)) {
            cancel(); subscribed = viewed; generation++; lastRequest = 0; retryAt = 0;
        }
        if (now < retryAt) return;
        if (lastRequest == 0 || now - lastRequest >= 1_000_000_000L) {
            lastRequest = now;
            MTRNetworkClient.sendToServer(new TrainStreamRequest(generation, subscribed, true));
        }
        if (!transfers.isEmpty() && transfers.values().stream().anyMatch(t -> now - t.createdNanos > 5_000_000_000L))
            resync("Incomplete train frame timed out");
    }

    public static void status(TrainStreamStatus message) {
        if (!current(message.generation(), message.dimension())) return;
        if (message.code() == TrainStreamStatus.ACCEPTED) {
            if (!Objects.equals(epoch, message.epoch())) { buffer.clear(); transfers.clear(); decoder.getQueue().clear(); }
            epoch = message.epoch(); accepted = true; error = false;
        } else {
            error = true; errorMessage = message.message();
            if (message.code() == TrainStreamStatus.UNAVAILABLE) {
                accepted = false; generation++; retryAt = System.nanoTime() + 2_000_000_000L; lastRequest = 0;
            }
        }
    }

    public static void chunk(TrainStreamChunk message) {
        if (!accepted || !current(message.generation(), message.dimension()) || !Objects.equals(epoch, message.epoch())) return;
        if (message.kind() == FRAME && message.sequence() <= buffer.sequence()) return;
        if ((message.kind() != FRAME && message.kind() != DEFINITIONS) || message.sequence() <= 0
                || message.sampleAgeMillis() < 0 || message.sampleAgeMillis() > 86_400_000) { resync("Invalid train frame type/age"); return; }
        try {
            var key = new Transfer(message.kind(), message.sequence());
            if (!transfers.containsKey(key) && transfers.size() >= 2) throw new IllegalArgumentException("Too many train transfers");
            var transfer = transfers.computeIfAbsent(key, ignored -> new TrainFrameAssembly(message.count(), message.totalBytes(), System.nanoTime()));
            if (!transfer.add(message.index(), message.count(), message.totalBytes(), message.data())) return;
            transfers.remove(key);
            byte[] payload = transfer.assemble();
            decoder.execute(() -> {
                try {
                    Object value = message.kind() == FRAME ? TrainFrameCodec.frame(payload) : TrainFrameCodec.definitions(payload);
                    Minecraft.getInstance().execute(() -> install(message, value));
                } catch (Exception e) { Minecraft.getInstance().execute(() -> { if (matches(message)) resync(e.getMessage()); }); }
            });
        } catch (RuntimeException e) { resync(e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private static void install(TrainStreamChunk message, Object value) {
        if (!matches(message)) return;
        try {
            if (message.kind() == DEFINITIONS) buffer.define((List<NamedDefinition>) value);
            else {
                Frame frame = (Frame) value;
                if (frame.sequence() != message.sequence()) throw new IllegalArgumentException("Mismatched train sequence");
                if (buffer.accept(frame, System.nanoTime(), message.sampleAgeMillis() * 1_000_000L)) {
                    error = false; cachedTime = 0;
                    long now = System.nanoTime();
                    if (MTRMapConfig.INSTANCE.debugLog.get() && now - lastDiagnostic > 5_000_000_000L) {
                        lastDiagnostic = now;
                        var first = frame.trains().stream().filter(p -> p.cars() > 0).findFirst();
                        var nativeClient = org.mtr.mod.client.MinecraftClientData.getInstance();
                        MTRMap.LOGGER.info("[MTRMap] Global trains: {} trains, {} cars, {} unlocatable, {} bytes/frame; native nearby={}{}",
                                buffer.trains(), frame.trains().stream().mapToInt(Pose::cars).sum(), buffer.unlocatable(), message.totalBytes(),
                                nativeClient == null ? 0 : nativeClient.vehicles.size(), first.isEmpty() ? "" : ", first XYZ="
                                        + first.get().x + "/" + first.get().y + "/" + first.get().z);
                    }
                }
            }
        } catch (RuntimeException e) { resync(e.getMessage()); }
    }
    private static boolean matches(TrainStreamChunk m) {
        return accepted && current(m.generation(), m.dimension()) && Objects.equals(epoch, m.epoch());
    }
    private static boolean current(long gen, String dimension) { return subscribed != null && generation == gen && subscribed.equals(dimension); }
    private static void resync(String reason) {
        error = true; errorMessage = reason == null ? "Train stream error" : reason;
        MTRMap.LOGGER.debug("[MTRMap] Train stream resync: {}", errorMessage);
        accepted = false; epoch = null; transfers.clear(); decoder.getQueue().clear();
        generation++; lastRequest = 0; retryAt = System.nanoTime() + 1_000_000_000L;
    }
    private static void cancel() {
        if (subscribed != null && MTRNetworkClient.canSendTrains())
            MTRNetworkClient.sendToServer(new TrainStreamRequest(generation, subscribed, false));
        subscribed = null; generation++; accepted = false; epoch = null; error = false;
        buffer.clear(); transfers.clear(); decoder.getQueue().clear(); cached = List.of(); cachedTime = 0;
    }
    public static void clear() {
        // Disconnect must not send over a connection that is being torn down.
        subscribed = viewed = null; screen = null; lastSeen = lastRequest = retryAt = 0;
        generation++; unsupported = accepted = error = false; epoch = null;
        buffer.clear(); transfers.clear(); decoder.getQueue().clear(); cached = List.of(); cachedTime = 0;
    }

    public static List<MapCar> cars(String dimension, double minX, double minZ, double maxX, double maxZ, double scale) {
        touch(dimension);
        if (!MTRMapConfig.INSTANCE.networkSyncEnabled.get() || unsupported) return ClientTrainCollector.get(dimension);
        if (!Objects.equals(dimension, subscribed)) return List.of();
        long now = System.nanoTime(), time = buffer.animating(now) ? now / 16_666_667L : -1;
        if (cachedTime != time || cachedSequence != buffer.sequence() || minX != cachedMinX || minZ != cachedMinZ
                || maxX != cachedMaxX || maxZ != cachedMaxZ || scale != cachedScale) {
            cached = buffer.cars(now, minX, minZ, maxX, maxZ, scale);
            cachedTime = time; cachedSequence = buffer.sequence(); cachedMinX = minX; cachedMinZ = minZ;
            cachedMaxX = maxX; cachedMaxZ = maxZ; cachedScale = scale;
        }
        return cached;
    }

    public static void drawStatus(GuiGraphics graphics, String dimension) {
        if (!MTRMapConfig.INSTANCE.trainCarsEnabled.get()) return;
        long age = buffer.ageNanos(System.nanoTime());
        String key = unsupported || !MTRMapConfig.INSTANCE.networkSyncEnabled.get() ? "nearby"
                : error ? "error" : !Objects.equals(dimension, subscribed) || buffer.sequence() == 0 ? "loading"
                : age > 3_000_000_000L ? "stale" : buffer.slow() ? "slow" : "global";
        var text = Component.translatable("mtrmap.train." + key, buffer.trains());
        if (buffer.unlocatable() > 0 && (key.equals("global") || key.equals("slow"))) text.append(Component.translatable("mtrmap.train.unlocatable", buffer.unlocatable()));
        graphics.drawString(Minecraft.getInstance().font, text, 8, graphics.guiHeight() - 14,
                key.equals("global") ? 0x92A9BD : 0xFFAD60, true);
    }
}

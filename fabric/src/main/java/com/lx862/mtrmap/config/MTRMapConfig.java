package com.lx862.mtrmap.config;

import com.lx862.mtrmap.MTRMap;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

/** Fabric configuration with the same keys and defaults as the NeoForge edition. */
public final class MTRMapConfig {
    public static final MTRMapConfig INSTANCE = new MTRMapConfig();
    private static final Properties values = new Properties();
    private static Path file;

    public final Value<Boolean> formalInitLog = bool("formalInitLog", false);
    public final Value<Boolean> debugLog = bool("debugLog", false);
    public final Value<Boolean> enabled = bool("enabled", true);
    public final Value<String> waypointMode = value("waypointMode", "both", s -> s);
    public final Value<Boolean> routeLinesEnabled = bool("routeLinesEnabled", true);
    public final Value<Boolean> trackLinesEnabled = bool("trackLinesEnabled", true);
    public final Value<Boolean> networkSyncEnabled = bool("networkSync.enabled", true);
    public final Value<Integer> networkSyncIntervalSeconds = value("networkSync.refreshIntervalSeconds", 300,
            Integer::parseInt);
    public final Value<Boolean> showStationLandmarks = bool("visibility.showStationLandmarks", true);
    public final Value<Boolean> showPlatformLandmarks = bool("visibility.showPlatformLandmarks", true);
    public final Value<Boolean> showDepotLandmarks = bool("visibility.showDepotLandmarks", false);
    public final Value<Boolean> showEmptyStation = bool("visibility.showEmptyStation", false);
    public final Value<Boolean> showHiddenRoute = bool("visibility.showHiddenRoute", false);

    private MTRMapConfig() {
    }

    private static Value<Boolean> bool(String key, boolean fallback) {
        return value(key, fallback, Boolean::parseBoolean);
    }

    private static <T> Value<T> value(String key, T fallback, Function<String, T> parser) {
        return new Value<>(key, fallback, parser);
    }

    public static synchronized void load() {
        file = FabricLoader.getInstance().getConfigDir().resolve("mtrmap.properties");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        } catch (IOException e) {
            MTRMap.LOGGER.warn("Could not load Fabric configuration", e);
        }
    }

    private static synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                values.store(output, "MTR Map Overlay Fabric configuration");
            }
        } catch (IOException e) {
            MTRMap.LOGGER.warn("Could not save Fabric configuration", e);
        }
    }

    public static final class Value<T> {
        private final String key;
        private final T fallback;
        private final Function<String, T> parser;

        private Value(String key, T fallback, Function<String, T> parser) {
            this.key = key;
            this.fallback = fallback;
            this.parser = parser;
        }

        public T get() {
            String raw = values.getProperty(key);
            if (raw == null) {
                return fallback;
            }
            try {
                return parser.apply(raw);
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        public void set(T newValue) {
            values.setProperty(key, String.valueOf(newValue));
            save();
        }
    }
}

package com.lx862.mtrmap.integration;

import com.lx862.mtrmap.mapdata.MapCar;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Both maps display the same captured service and actual XYZ, without inventing an ETA. */
public final class TrainMapTooltip {
    private TrainMapTooltip() {
    }

    public static List<Component> lines(MapCar car) {
        final var point = car.center();
        return List.of(Component.literal(clean(car.routeName()) + " → " + clean(car.destination())),
                Component.literal(car.modelId() + " · " + (car.index() + 1) + "/" + car.count()),
                Component.literal(String.format(Locale.ROOT, "XYZ %.2f / %.2f / %.2f", point.x(), point.y(), point.z())),
                Component.literal("Nearby MTR vehicle data"));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "—" : value.replace('|', ' ');
    }
}

package io.github.teamcreating.mtrmap.integration;

import io.github.teamcreating.mtrmap.mapdata.MapCar;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared Create-style train tooltip; display metadata only, no distance/ETA guesses. */
public final class TrainMapTooltip {
    private TrainMapTooltip() {
    }

    public static List<Component> lines(MapCar car) {
        final Component[] name = {Component.translatable("mtrmap.train.title")};
        try {
            org.mtr.client.CustomResourceLoader.getVehicleById(org.mtr.core.data.TransportMode.TRAIN, car.modelId(),
                    resource -> name[0] = resource.left().getName());
        } catch (RuntimeException ignored) { }
        return List.of(name[0].copy().withStyle(style -> style.withColor(0xFFEFEF)),
                Component.literal("› " + clean(car.routeName()) + " → " + clean(car.destination()))
                        .withStyle(style -> style.withColor(0x92A9BD)),
                Component.translatable("mtrmap.train.cars", car.count()).withStyle(style -> style.withColor(0x92A9BD)));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "—" : value.replace('|', ' ');
    }
}

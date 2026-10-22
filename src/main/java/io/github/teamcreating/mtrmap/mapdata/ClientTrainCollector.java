package io.github.teamcreating.mtrmap.mapdata;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import net.minecraft.client.Minecraft;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.render.PositionAndRotation;

import java.util.ArrayList;
import java.util.List;

/** Read-only, map-open capture of the nearby vehicles MTR already simulates on the client. */
public final class ClientTrainCollector {
    private static Object world;
    private static long lastCapture;
    private static long lastWarning;
    private static long lastDiagnostic;
    private static List<MapCar> cars = List.of();

    private ClientTrainCollector() {
    }

    public static List<MapCar> get(String viewedDimension) {
        final var level = Minecraft.getInstance().level;
        if (level == null || !(level.dimension().location().getNamespace() + "/"
                + level.dimension().location().getPath()).equals(viewedDimension)) {
            if (level == null) clear();
            return List.of();
        }
        final long now = System.nanoTime();
        if (world != level) {
            clear();
            world = level;
        }
        if (lastCapture != 0 && now - lastCapture < 50_000_000L) {
            return cars;
        }
        lastCapture = now;
        final List<MapCar> next = new ArrayList<>();
        final MinecraftClientData data = MinecraftClientData.getInstance();
        if (data == null) {
            cars = List.of();
            return cars;
        }
        int trains = 0, withoutPath = 0, failures = 0;
        for (var vehicle : data.vehicles) {
            // Core's isOnRoute only means railProgress > the siding's default position.
            // Parked/manual/returning trains still have real bogies and must remain visible.
            if (vehicle.getTransportMode() != TransportMode.TRAIN) continue;
            trains++;
            try {
                final var extra = vehicle.vehicleExtraData;
                if (extra.immutablePath.isEmpty()) {
                    withoutPath++;
                    continue;
                }
                final var positions = vehicle.getVehicleCarsAndPositions();
                for (int i = 0; i < positions.size(); i++) {
                    final var car = positions.get(i).left();
                    if (!(car.getWidth() > 0) || !(car.getLength() > 0)) continue;
                    final ObjectArrayList<PositionAndRotation> bogies = new ObjectArrayList<>();
                    for (var bogie : positions.get(i).right()) {
                        final var a = bogie.left();
                        final var b = bogie.right();
                        if (a == null || b == null) continue;
                        bogies.add(new PositionAndRotation(a, b, true));
                    }
                    if (bogies.isEmpty()) continue;
                    // Reuse MTR's exact body-centre/bogie-offset math, including pitch and roll.
                    final PositionAndRotation body = new PositionAndRotation(bogies, car, true);
                    final double halfWidth = car.getWidth() / 2;
                    final double halfLength = car.getLength() / 2;
                    final MapCar rendered = new MapCar(vehicle.getId(), i, positions.size(),
                            i == (vehicle.getReversed() ? positions.size() - 1 : 0), car.getVehicleId(),
                            extra.getThisRouteName(), extra.getThisRouteDestination(),
                            extra.getThisRouteName().isBlank() ? 0xA6B3C1 : extra.getThisRouteColor(),
                            corner(body, halfWidth, halfLength), corner(body, halfWidth, -halfLength),
                            corner(body, -halfWidth, -halfLength), corner(body, -halfWidth, halfLength), vehicle.getReversed());
                    if (rendered.valid()) next.add(rendered);
                }
            } catch (RuntimeException e) {
                failures++;
                if (now - lastWarning > 5_000_000_000L) {
                    lastWarning = now;
                    MTRMap.LOGGER.warn("[MTRMap] Cannot capture nearby train " + vehicle.getId(), e);
                }
            }
        }
        cars = List.copyOf(next);
        if (MTRMapConfig.INSTANCE.debugLog.get() && now - lastDiagnostic > 5_000_000_000L) {
            lastDiagnostic = now;
            MTRMap.LOGGER.info("[MTRMap] Train capture (nearby): {} vehicles, {} trains, {} without path, {} failures, {} cars{}",
                    data.vehicles.size(), trains, withoutPath, failures, cars.size(),
                    cars.isEmpty() ? "" : ", first=" + cars.get(0).center());
        }
        return cars;
    }

    private static MapVertex corner(PositionAndRotation body, double x, double z) {
        final Vector point = body.transformForwards(new Vector(x, 0, z),
                Vector::rotateX, Vector::rotateY, Vector::add);
        return new MapVertex(point.x, point.y, point.z);
    }

    public static void clear() {
        world = null;
        lastCapture = 0;
        lastDiagnostic = 0;
        cars = List.of();
    }
}

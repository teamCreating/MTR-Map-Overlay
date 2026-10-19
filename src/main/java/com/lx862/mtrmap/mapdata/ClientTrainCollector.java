package com.lx862.mtrmap.mapdata;

import com.lx862.mtrmap.MTRMap;
import net.minecraft.client.Minecraft;
import org.mtr.client.MinecraftClientData;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.render.PositionAndRotation;

import java.util.ArrayList;
import java.util.List;

/** Read-only, map-open capture of the nearby vehicles MTR already simulates on the client. */
public final class ClientTrainCollector {
    private static Object world;
    private static long lastCapture;
    private static long lastWarning;
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
        if (data == null) return List.of();
        for (var vehicle : data.vehicles) {
            if (vehicle.getTransportMode() != TransportMode.TRAIN || !vehicle.getIsOnRoute()) continue;
            try {
                final var extra = vehicle.vehicleExtraData;
                if (extra.immutablePath.isEmpty()) continue;
                final var positions = vehicle.getVehicleCarsAndPositions();
                for (int i = 0; i < positions.size(); i++) {
                    final var car = positions.get(i).left();
                    if (!(car.getWidth() > 0) || !(car.getLength() > 0)) continue;
                    final ObjectArrayList<PositionAndRotation> bogies = new ObjectArrayList<>();
                    for (Vehicle.BogiePosition bogie : positions.get(i).right()) {
                        final var a = bogie.positionAndTiltAngle1();
                        final var b = bogie.positionAndTiltAngle2();
                        if (a == null || b == null) continue;
                        bogies.add(new PositionAndRotation(a.position(), b.position(), a.tiltAngle(), b.tiltAngle(), true));
                    }
                    if (bogies.isEmpty()) continue;
                    // Reuse MTR's exact body-centre/bogie-offset math, including pitch and roll.
                    final PositionAndRotation body = new PositionAndRotation(bogies, car, true);
                    final double halfWidth = car.getWidth() / 2;
                    final double halfLength = car.getLength() / 2;
                    final MapCar rendered = new MapCar(vehicle.getId(), i, positions.size(),
                            i == (vehicle.getReversed() ? positions.size() - 1 : 0), car.getVehicleId(),
                            extra.getThisRouteName(), extra.getThisRouteDestination(), extra.getThisRouteColor(),
                            corner(body, halfWidth, halfLength), corner(body, halfWidth, -halfLength),
                            corner(body, -halfWidth, -halfLength), corner(body, -halfWidth, halfLength));
                    if (rendered.valid()) next.add(rendered);
                }
            } catch (RuntimeException e) {
                if (now - lastWarning > 5_000_000_000L) {
                    lastWarning = now;
                    MTRMap.LOGGER.debug("[MTRMap] Cannot capture one nearby train: {}", e.getMessage());
                }
            }
        }
        cars = List.copyOf(next);
        return cars;
    }

    private static MapVertex corner(PositionAndRotation body, double x, double z) {
        final Vector point = body.transformForwards(new Vector(x, 0, z),
                Vector::rotateX, Vector::rotateY, Vector::rotateZ, Vector::add);
        return new MapVertex(point.x(), point.y(), point.z());
    }

    public static void clear() {
        world = null;
        lastCapture = 0;
        cars = List.of();
    }
}

package com.lx862.mtrmap;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates a small MTR network into the TestWorld save so the unified mod
 * can be verified visually in a real game (route snapping + parallel lanes).
 *
 * <p>Runs as a normal JUnit test but writes into {@code run/saves/TestWorld}.
 * Re-run it whenever the test world needs to be (re)populated.</p>
 */
class TestWorldGeneratorTest {

    private static final Path WORLD_MTR = Path.of("run/saves/TestWorld/mtr");
    private static final String DIMENSION = "minecraft/overworld";

    private static void deleteRecursively(java.io.File file) {
        final java.io.File[] children = file.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static Position node(long x, long z) {
        return new Position(x, 64, z);
    }

    private static Station station(Simulator simulator, String name, int color, Position p1, Position p2) {
        final Station station = new Station(simulator);
        final JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("color", color);
        json.add("position1", positionJson(p1));
        json.add("position2", positionJson(p2));
        station.updateData(new JsonReader(json));
        if (!name.equals(station.getName())) {
            throw new IllegalStateException("station name not applied: " + station.getName());
        }
        return station;
    }

    private static Angle angleOf(Position from, Position to) {
        final double deg = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        final int idx = ((int) Math.round(deg / 22.5) + 16) % 16;
        return Angle.values()[idx];
    }

    /**
     * Tangent (travel direction) at polyline point i, smoothed so consecutive
     * rails join without kinks: interior points use the angle bisector of the
     * incoming and outgoing segments - this is what MTR's rail placement does,
     * and its route path finder requires tangent continuity at joints.
     */
    private static Angle tangentAt(List<Position> polyline, int i) {
        if (i == 0) {
            return angleOf(polyline.get(0), polyline.get(1));
        }
        if (i == polyline.size() - 1) {
            return angleOf(polyline.get(i - 1), polyline.get(i));
        }
        final int inAngle = angleIndex(angleOf(polyline.get(i - 1), polyline.get(i)));
        final int outAngle = angleIndex(angleOf(polyline.get(i), polyline.get(i + 1)));
        int diff = outAngle - inAngle;
        if (diff > 8) {
            diff -= 16;
        } else if (diff < -8) {
            diff += 16;
        }
        int bisector = inAngle + diff / 2;
        return Angle.values()[((bisector % 16) + 16) % 16];
    }

    private static int angleIndex(Angle angle) {
        return ((int) Math.round(Math.toDegrees(angle.angleDegrees) / 22.5)) % 16;
    }

    private static Rail railOnSegment(List<Position> polyline, int seg, boolean platform) {
        final Position a = polyline.get(seg);
        final Position b = polyline.get(seg + 1);
        final Angle ta = tangentAt(polyline, seg);
        final Angle tb = tangentAt(polyline, seg + 1);
        if (platform) {
            return Rail.newPlatformRail(a, ta, b, tb,
                    Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    new ObjectArrayList<>(), TransportMode.TRAIN);
        }
        return Rail.newRail(a, ta, b, tb, Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<String>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
    }

    private static Rail makePlatformRail(Position p1, Position p2) {
        final Rail rail = Rail.newPlatformRail(p1, angleOf(p1, p2), p2, angleOf(p2, p1),
                Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<>(), TransportMode.TRAIN);
        if (rail == null || !rail.isValid()) {
            throw new IllegalStateException("platform rail invalid: " + p1 + " -> " + p2);
        }
        return rail;
    }

    private static Rail makeRail(Position p1, Position p2) {
        final Rail rail = Rail.newRail(p1, angleOf(p1, p2), p2, angleOf(p2, p1),
                Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<>(), 1, 2, false, false, true, false, true, TransportMode.TRAIN);
        if (rail == null || !rail.isValid()) {
            throw new IllegalStateException("rail invalid: " + p1 + " -> " + p2);
        }
        return rail;
    }

    private static JsonObject positionJson(Position p) {
        final JsonObject json = new JsonObject();
        json.addProperty("x", p.getX());
        json.addProperty("y", p.getY());
        json.addProperty("z", p.getZ());
        return json;
    }

    @Test
    void generateNetwork() {
        // IMPORTANT: every MTR object must be constructed with the Simulator as
        // its Data back-reference, otherwise updateRailCache reads the wrong
        // positionsToRail and the platforms are pruned as invalid on sync.
        // Start from a clean save: stale files would leak old ids into sync
        deleteRecursively(WORLD_MTR.toFile());
        final Simulator simulator = new Simulator(DIMENSION, new String[]{DIMENSION}, WORLD_MTR, false);
        // Corridor nodes along z=0, then a bend down to the south
        final Position n0 = node(0, 0);
        final Position n1 = node(80, 0);
        final Position n2 = node(180, 80);
        final Position n3 = node(280, 80);
        final Position n4 = node(380, 0);
        final Position n5 = node(480, 0);

        // One tangent-continuous polyline: n0..n5 then the siding straight on
        final List<Position> polyline = List.of(n0, n1, n2, n3, n4, n5,
                node(600, 0));
        // Rails: platform rails at each station + plain connecting rails
        final Rail railAlpha = railOnSegment(polyline, 0, true);
        final Rail shared = railOnSegment(polyline, 1, false);
        final Rail railBravo = railOnSegment(polyline, 2, true);
        final Rail bend = railOnSegment(polyline, 3, false);
        final Rail railCharlie = railOnSegment(polyline, 4, true);

        // Platforms (one per platform rail); MTR generates a random unique id
        final Platform alpha = platform(simulator, n0, n1, railAlpha, "Alpha Platform");
        final Platform bravo = platform(simulator, n2, n3, railBravo, "Bravo Platform");
        final Platform charlie = platform(simulator, n4, n5, railCharlie, "Charlie Platform");

        // Stations covering their platforms (for JourneyMap landmarks + waypoints)
        final Station stationAlpha = station(simulator, "Alpha", 15073280, node(-20, -20), node(100, 20));
        final Station stationBravo = station(simulator, "Bravo", 28440, node(200, 60), node(340, 100));
        final Station stationCharlie = station(simulator, "Charlie", 22016, node(360, -20), node(500, 20));

        // Routes: Express (Alpha->Bravo), Local (Alpha->Bravo->Charlie), Local Return
        final Route express = route(simulator, "Express", 15073280, List.of(alpha, bravo));
        final Route local = route(simulator, "Local", 28440, List.of(alpha, bravo, charlie));
        final Route localReturn = route(simulator, "Local Return", 28440, List.of(charlie, bravo, alpha));

        // Siding continues the polyline (segment 5) with tangent continuity
        final Position s0 = node(480, 0);
        final Position s1 = node(600, 0);
        final Rail sidingRail = Rail.newSidingRail(s0, tangentAt(polyline, 5), s1, tangentAt(polyline, 6),
                Rail.Shape.QUADRATIC, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                new ObjectArrayList<String>(), TransportMode.TRAIN);
        final org.mtr.core.data.Siding siding = new org.mtr.core.data.Siding(s0, s1, 0, TransportMode.TRAIN, simulator);
        siding.rail = sidingRail;
        simulator.sidings.add(siding);
        simulator.rails.add(sidingRail);

        final Depot depot = new Depot(TransportMode.TRAIN, simulator);
        final JsonObject depotJson = new JsonObject();
        depotJson.addProperty("name", "Test Depot");
        final org.mtr.libraries.com.google.gson.JsonArray routeIds = new org.mtr.libraries.com.google.gson.JsonArray();
        routeIds.add(local.getId());
        depotJson.add("routeIds", routeIds);
        depotJson.addProperty("color", 28440);
        depotJson.add("position1", positionJson(node(470, -10)));
        depotJson.add("position2", positionJson(node(610, 10)));
        depot.updateData(new JsonReader(depotJson));
        depot.routes.add(local);
        simulator.depots.add(depot);

        simulator.stations.add(stationAlpha);
        simulator.stations.add(stationBravo);
        simulator.stations.add(stationCharlie);
        simulator.platforms.add(alpha);
        simulator.platforms.add(bravo);
        simulator.platforms.add(charlie);
        simulator.routes.add(express);
        simulator.routes.add(local);
        simulator.routes.add(localReturn);
        simulator.rails.add(railAlpha);
        simulator.rails.add(shared);
        simulator.rails.add(railBravo);
        simulator.rails.add(bend);
        simulator.rails.add(railCharlie);
        System.out.println("[TestWorldGenerator] immediately after adds: platforms=" + simulator.platforms.size()
                + " alpha.isInvalidSavedRail=" + alpha.isInvalidSavedRail()
                + " alpha.rail=" + (alpha.rail == null ? "NULL" : "ok")
                + " positionsToRailContainsN0=" + simulator.positionsToRail.containsKey(n0));
        simulator.sync();
        System.out.println("[TestWorldGenerator] platform ids: alpha=" + alpha.getId()
                + " bravo=" + bravo.getId() + " charlie=" + charlie.getId()
                + " sidingId=" + siding.getId());
        System.out.println("[TestWorldGenerator] pre-generate: depot path size=" + depot.getPath().size()
                + " depot savedRails=" + depot.savedRails.size()
                + " siding valid=" + sidingRail.isValid() + " isSiding=" + sidingRail.isSiding());
        // Drive MTR's own route generation to completion: generateDepots()
        // queues the path finders (generateMainRoute), tick() progresses them
        // (findPathTick - normally driven by the server's tick loop).
        org.mtr.core.data.Depot.generateDepots(simulator, new ObjectArrayList<>(List.of(depot)));
        for (int i = 0; i < 5000 && depot.getPath().isEmpty(); i++) {
            simulator.tick();
        }
        System.out.println("[TestWorldGenerator] post-generate: depot path size=" + depot.getPath().size()
                + " status=" + depot.getLastGeneratedStatus());
        depot.getFailedPlatformIds(
                (startId, endId) -> System.out.println("[TestWorldGenerator] failed stretch: " + startId + " -> " + endId),
                sidingCount -> System.out.println("[TestWorldGenerator] failed siding count: " + sidingCount));
        System.out.println("[TestWorldGenerator] post-sync positionsToRail keys:");
        simulator.positionsToRail.keySet().forEach(k ->
                System.out.println("[TestWorldGenerator]   key: " + k.getX() + "," + k.getY() + "," + k.getZ()));
        System.out.println("[TestWorldGenerator] pre-sync: platforms=" + simulator.platforms.size()
                + " alpha.invalid=" + alpha.isInvalidSavedRail()
                + " alpha.inSet=" + simulator.platforms.contains(alpha));
        final Object lookup = simulator.positionsToRail.containsKey(n0) ? simulator.positionsToRail.get(n0).get(n1) : null;
        System.out.println("[TestWorldGenerator] alpha serialized: " + alpha);
        System.out.println("[TestWorldGenerator] lookup n0->n1 identity==railAlpha: " + (lookup == railAlpha)
                + " alpha.rail after sync=" + (alpha.rail == null ? "NULL" : "set")
                + " alpha.isValid=" + alpha.isValid()
                + " alpha.railIsPlatform=" + (alpha.rail != null && alpha.rail.isPlatform()));
        System.out.println("[TestWorldGenerator] after sync: platforms=" + simulator.platforms.size()
                + " routes=" + simulator.routes.size() + " rails=" + simulator.rails.size());
        simulator.platforms.forEach(platform -> System.out.println("[TestWorldGenerator]   platform valid="
                + platform.isValid() + " invalidSavedRail=" + platform.isInvalidSavedRail()
                + " rail=" + (platform.rail == null ? "null" : platform.rail.isPlatform())));
        simulator.save();
        simulator.stop();
        System.out.println("[TestWorldGenerator] world data written to " + WORLD_MTR.toAbsolutePath());
    }

    private static Platform platform(Simulator simulator, Position p1, Position p2, Rail rail, String name) {
        final Platform platform = new Platform(p1, p2, TransportMode.TRAIN, simulator);
        platform.rail = rail;
        final JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("color", 7829367);
        platform.updateData(new JsonReader(json));
        return platform;
    }

    private static Route route(Simulator simulator, String name, int color, List<Platform> platforms) {
        final Route route = new Route(TransportMode.TRAIN, simulator);
        final JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("color", color);
        final org.mtr.libraries.com.google.gson.JsonArray array = new org.mtr.libraries.com.google.gson.JsonArray();
        for (Platform platform : platforms) {
            final JsonObject entry = new JsonObject();
            entry.addProperty("platformId", platform.getId());
            array.add(entry);
        }
        json.add("routePlatformData", array);
        route.updateData(new JsonReader(json));

        // Re-wire platform references by list order (updateData rebuilds the list)
        for (int i = 0; i < platforms.size(); i++) {
            route.getRoutePlatforms().get(i).platform = platforms.get(i);
        }
        if (route.getRoutePlatforms().size() != platforms.size()) {
            throw new IllegalStateException("route platform data not applied for " + name);
        }
        if (!name.equals(route.getName())) {
            throw new IllegalStateException("route name not applied: " + route.getName());
        }
        return route;
    }

    private static JsonObject json(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static Map<Position, Map<Position, org.mtr.core.data.Rail>> index(List<org.mtr.core.data.Rail> rails) {
        final Map<Position, Map<Position, org.mtr.core.data.Rail>> index = new HashMap<>();
        for (org.mtr.core.data.Rail rail : rails) {
            final org.mtr.core.tool.Vector v1 = rail.railMath.getPosition(0, false);
            final org.mtr.core.tool.Vector v2 = rail.railMath.getPosition(rail.railMath.getLength(), false);
            final Position p1 = new Position((long) v1.x(), 64, (long) v1.z());
            final Position p2 = new Position((long) v2.x(), 64, (long) v2.z());
            index.computeIfAbsent(p1, k -> new HashMap<>()).put(p2, rail);
            index.computeIfAbsent(p2, k -> new HashMap<>()).put(p1, rail);
        }
        return index;
    }
}

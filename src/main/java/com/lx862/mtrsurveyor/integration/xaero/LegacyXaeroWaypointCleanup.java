package com.lx862.mtrsurveyor.integration.xaero;

import com.lx862.mtrsurveyor.MTRSurveyor;

import java.util.HashSet;
import java.util.Set;

/** Removes waypoints created by pre-map-overlay releases; never creates or updates waypoints. */
public final class LegacyXaeroWaypointCleanup {

    private static final String LEGACY_PREFIX = "[MTR] ";
    private static final Set<String> CLEANED_WORLDS = new HashSet<>();

    private LegacyXaeroWaypointCleanup() {
    }

    public static void onClientTick() {
        try {
            final xaero.common.XaeroMinimapSession session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null || session.getWaypointsManager() == null
                    || session.getWaypointsManager().getCurrentWorld() == null
                    || session.getWaypointsManager().getCurrentWorld().getCurrentSet() == null) {
                return;
            }
            final xaero.common.minimap.waypoints.WaypointWorld world =
                    session.getWaypointsManager().getCurrentWorld();
            if (!CLEANED_WORLDS.add(world.getFullId())) {
                return;
            }
            final int before = world.getCurrentSet().getList().size();
            world.getCurrentSet().getList().removeIf(waypoint -> waypoint.getName() != null
                    && waypoint.getName().startsWith(LEGACY_PREFIX));
            final int removed = before - world.getCurrentSet().getList().size();
            if (removed > 0) {
                MTRSurveyor.LOGGER.info("[MTRSurveyor] Removed {} legacy Xaero waypoints; landmarks are map-only now",
                        removed);
            }
        } catch (Throwable e) {
            MTRSurveyor.LOGGER.debug("[MTRSurveyor] Legacy Xaero waypoint cleanup deferred: {}", e.getMessage());
        }
    }
}

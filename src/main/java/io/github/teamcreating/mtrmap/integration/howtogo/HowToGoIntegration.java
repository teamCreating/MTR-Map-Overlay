package io.github.teamcreating.mtrmap.integration.howtogo;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import net.minecraft.client.Minecraft;

/** The destination is handed to HowToGo only after an explicit Navigate action. */
public final class HowToGoIntegration {
    private static boolean initialized;
    private HowToGoIntegration() {}
    public static void initialize(Client client) {
        if (initialized) return;
        initialized = true;
        try {
            var bridge = HowToGoBridge.find(HowToGoIntegration.class.getClassLoader());
            if (bridge.isEmpty()) return;
            client.registerNavigation("mtrmap:howtogo", "HowToGo", target -> {
                var mc = Minecraft.getInstance();
                return mc.player != null && mc.level != null && mc.level.dimension().location().toString().equals(target.world().dimensionId())
                        && HowToGoBridge.supports(station(client,target));
            }, target -> {
                try {
                    bridge.get().navigate(station(client,target));
                    Minecraft.getInstance().setScreen(null);
                } catch (ReflectiveOperationException e) { throw new IllegalStateException("HowToGo rejected the navigation target", e); }
            });
            MTRMap.LOGGER.info("[MTRMap] HowToGo navigation provider registered");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            MTRMap.LOGGER.warn("[MTRMap] HowToGo navigation integration unavailable", e);
        }
    }
    private static Station station(Client client, NavigationTarget target) {
        var catalog = client.catalog(target.world());
        return catalog.status() == Status.READY ? catalog.stations().stream().filter(s -> s.id().equals(target.stationId())).findFirst().orElse(null) : null;
    }
}

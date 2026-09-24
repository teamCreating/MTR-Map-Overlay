package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.resources.ResourceLocation;

/** Native JourneyMap fullscreen add-on buttons for the two path layers. */
final class JourneyMapToolbar {

    private static final ResourceLocation TRACK_ICON = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/item/rail.png");
    private static final ResourceLocation ROUTE_ICON = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/item/filled_map.png");
    private static IThemeButton trackButton;
    private static IThemeButton routeButton;
    private static long lastTrackClick;
    private static long lastRouteClick;
    private static boolean registered;

    private JourneyMapToolbar() {
    }

    static void register() {
        if (registered) {
            return;
        }
        registered = true;
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MTRMap.MOD_ID, event -> {
            trackButton = event.getThemeButtonDisplay().addThemeToggleButton("TRACK ON", "TRACK OFF",
                    TRACK_ICON, MTRMapConfig.INSTANCE.trackLinesEnabled.get(), button -> toggle(button, true));
            routeButton = event.getThemeButtonDisplay().addThemeToggleButton("ROUTE ON", "ROUTE OFF",
                    ROUTE_ICON, MTRMapConfig.INSTANCE.routeLinesEnabled.get(), button -> toggle(button, false));
            trackButton.setTooltip("TRACK: physical rails");
            routeButton.setTooltip("ROUTE: coloured route bands");
        });
    }

    static void refreshButtons() {
        if (trackButton != null) {
            trackButton.setToggled(MTRMapConfig.INSTANCE.trackLinesEnabled.get());
        }
        if (routeButton != null) {
            routeButton.setToggled(MTRMapConfig.INSTANCE.routeLinesEnabled.get());
        }
    }

    private static void toggle(IThemeButton button, boolean track) {
        // Some JourneyMap versions call an add-on action twice for one click.
        final long now = System.nanoTime();
        final long previous = track ? lastTrackClick : lastRouteClick;
        if (now - previous < 150_000_000L) {
            refreshButtons();
            return;
        }
        if (track) {
            lastTrackClick = now;
            final boolean enabled = !MTRMapConfig.INSTANCE.trackLinesEnabled.get();
            MTRMapConfig.INSTANCE.trackLinesEnabled.set(enabled);
            button.setToggled(enabled);
        } else {
            lastRouteClick = now;
            final boolean enabled = !MTRMapConfig.INSTANCE.routeLinesEnabled.get();
            MTRMapConfig.INSTANCE.routeLinesEnabled.set(enabled);
            button.setToggled(enabled);
        }
        JourneyMapIntegration.requestSync();
    }
}

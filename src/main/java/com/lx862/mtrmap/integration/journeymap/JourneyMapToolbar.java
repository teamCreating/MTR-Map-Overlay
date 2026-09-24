package com.lx862.mtrmap.integration.journeymap;

import com.lx862.mtrmap.MTRMap;
import com.lx862.mtrmap.config.MTRMapConfig;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native JourneyMap fullscreen add-on buttons for the two path layers. */
final class JourneyMapToolbar {

    private static final ResourceLocation TRACK_ICON_ON = icon("track_icon_on");
    private static final ResourceLocation TRACK_ICON_OFF = icon("track_icon_off");
    private static final ResourceLocation ROUTE_ICON_ON = icon("route_icon_on");
    private static final ResourceLocation ROUTE_ICON_OFF = icon("route_icon_off");
    private static IThemeButton trackButton;
    private static IThemeButton routeButton;
    private static Field themeIconField;
    private static Field themeTextureField;
    private static Method loadThemeTexture;
    private static boolean iconAccessAttempted;
    private static boolean iconWarningLogged;
    private static Boolean trackIconEnabled;
    private static Boolean routeIconEnabled;
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
            final boolean tracksEnabled = MTRMapConfig.INSTANCE.trackLinesEnabled.get();
            final boolean routesEnabled = MTRMapConfig.INSTANCE.routeLinesEnabled.get();
            trackButton = event.getThemeButtonDisplay().addThemeToggleButton("TRACKS ON", "TRACKS OFF",
                    tracksEnabled ? TRACK_ICON_ON : TRACK_ICON_OFF, tracksEnabled, button -> toggle(true));
            routeButton = event.getThemeButtonDisplay().addThemeToggleButton("ROUTES ON", "ROUTES OFF",
                    routesEnabled ? ROUTE_ICON_ON : ROUTE_ICON_OFF, routesEnabled, button -> toggle(false));
            trackIconEnabled = tracksEnabled;
            routeIconEnabled = routesEnabled;
            trackButton.setTooltip("TRACKS: physical rails");
            routeButton.setTooltip("ROUTES: coloured route bands");
        });
    }

    static void refreshButtons() {
        if (trackButton != null) {
            final boolean enabled = MTRMapConfig.INSTANCE.trackLinesEnabled.get();
            trackButton.setToggled(enabled);
            if (!Boolean.valueOf(enabled).equals(trackIconEnabled)) {
                updateIcon(trackButton, enabled ? TRACK_ICON_ON : TRACK_ICON_OFF);
                trackIconEnabled = enabled;
            }
        }
        if (routeButton != null) {
            final boolean enabled = MTRMapConfig.INSTANCE.routeLinesEnabled.get();
            routeButton.setToggled(enabled);
            if (!Boolean.valueOf(enabled).equals(routeIconEnabled)) {
                updateIcon(routeButton, enabled ? ROUTE_ICON_ON : ROUTE_ICON_OFF);
                routeIconEnabled = enabled;
            }
        }
    }

    private static void toggle(boolean track) {
        // Some JourneyMap versions call an add-on action twice for one click.
        final long now = System.nanoTime();
        final long previous = track ? lastTrackClick : lastRouteClick;
        if (now - previous < 150_000_000L) {
            refreshButtons();
            return;
        }
        if (track) {
            lastTrackClick = now;
            MTRMapConfig.INSTANCE.trackLinesEnabled.set(!MTRMapConfig.INSTANCE.trackLinesEnabled.get());
        } else {
            lastRouteClick = now;
            MTRMapConfig.INSTANCE.routeLinesEnabled.set(!MTRMapConfig.INSTANCE.routeLinesEnabled.get());
        }
        refreshButtons();
        JourneyMapIntegration.requestSync();
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(MTRMap.MOD_ID, "textures/gui/" + name + ".png");
    }

    private static void updateIcon(IThemeButton button, ResourceLocation icon) {
        // The public v2 button API exposes toggle state but not icon replacement.
        // JourneyMap 6.0.8 stores the icon and its cached texture on ThemeButton;
        // update both so the red/green stripe changes without reopening the map.
        try {
            if (!iconAccessAttempted) {
                iconAccessAttempted = true;
                final Class<?> themeButton = Class.forName("journeymap.client.ui.theme.ThemeButton");
                themeIconField = themeButton.getDeclaredField("icon");
                themeTextureField = themeButton.getDeclaredField("textureIcon");
                themeIconField.setAccessible(true);
                themeTextureField.setAccessible(true);
                loadThemeTexture = Class.forName("journeymap.client.texture.TextureCache")
                        .getMethod("getThemeTextureFromResource", ResourceLocation.class);
            }
            final Object texture = loadThemeTexture.invoke(null, icon);
            themeIconField.set(button.getButton(), icon);
            themeTextureField.set(button.getButton(), texture);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!iconWarningLogged) {
                iconWarningLogged = true;
                MTRMap.LOGGER.warn("[MTRMap] JourneyMap icon state could not be refreshed: {}", e.getMessage());
            }
        }
    }
}

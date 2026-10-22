package io.github.teamcreating.mtrmap.integration.journeymap;

import io.github.teamcreating.mtrmap.MTRMap;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import journeymap.client.api.display.IThemeButton;
import journeymap.client.api.display.ThemeButtonDisplay;
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
        JourneyMapEventBridge.register();
    }

    static void addButtons(ThemeButtonDisplay display) {
        final boolean tracksEnabled = MTRMapConfig.INSTANCE.trackLinesEnabled.get();
        final boolean routesEnabled = MTRMapConfig.INSTANCE.routeLinesEnabled.get();
        trackButton = display.addThemeToggleButton("TRACKS", "grid",
                tracksEnabled, button -> toggle(true));
        routeButton = display.addThemeToggleButton("ROUTES", "grid",
                routesEnabled, button -> toggle(false));
        trackIconEnabled = tracksEnabled;
        routeIconEnabled = routesEnabled;
        trackButton.setTooltip("TRACKS: physical rails");
        routeButton.setTooltip("ROUTES: coloured route bands");
        updateIcon(trackButton, tracksEnabled ? TRACK_ICON_ON : TRACK_ICON_OFF);
        updateIcon(routeButton, routesEnabled ? ROUTE_ICON_ON : ROUTE_ICON_OFF);
    }

    static void refreshButtons() {
        if (trackButton != null) {
            final boolean enabled = MTRMapConfig.INSTANCE.trackLinesEnabled.get();
            if (!Boolean.valueOf(enabled).equals(trackIconEnabled)) {
                trackButton.setToggled(enabled);
                trackIconEnabled = enabled;
            }
            updateIcon(trackButton, enabled ? TRACK_ICON_ON : TRACK_ICON_OFF);
        }
        if (routeButton != null) {
            final boolean enabled = MTRMapConfig.INSTANCE.routeLinesEnabled.get();
            if (!Boolean.valueOf(enabled).equals(routeIconEnabled)) {
                routeButton.setToggled(enabled);
                routeIconEnabled = enabled;
            }
            updateIcon(routeButton, enabled ? ROUTE_ICON_ON : ROUTE_ICON_OFF);
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
        return new ResourceLocation(MTRMap.MOD_ID, "textures/gui/" + name + ".png");
    }

    private static void updateIcon(IThemeButton button, ResourceLocation icon) {
        // JourneyMap 5 accepts theme icon names, so supply our cached resource texture directly.
        try {
            if (!iconAccessAttempted) {
                iconAccessAttempted = true;
                final Class<?> themeButton = Class.forName("journeymap.client.ui.theme.ThemeButton");
                themeTextureField = themeButton.getDeclaredField("textureIcon");
                themeTextureField.setAccessible(true);
                loadThemeTexture = Class.forName("journeymap.client.texture.TextureCache")
                        .getMethod("getTexture", ResourceLocation.class);
            }
            final Object texture = loadThemeTexture.invoke(null, icon);
            // JourneyMap may reload all button themes after an add-on action.
            // Restore only a replaced icon, without resetting the native toggle each frame.
            if (themeTextureField.get(button.getButton()) != texture) {
                themeTextureField.set(button.getButton(), texture);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!iconWarningLogged) {
                iconWarningLogged = true;
                MTRMap.LOGGER.warn("[MTRMap] JourneyMap icon state could not be refreshed: {}", e.getMessage());
            }
        }
    }
}

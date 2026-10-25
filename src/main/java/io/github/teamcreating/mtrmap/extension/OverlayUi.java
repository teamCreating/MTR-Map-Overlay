package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.mapdata.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.DoubleBinaryOperator;

/** Shared map-only UI. Host callbacks supply projection; UI never owns the map's camera. */
public final class OverlayUi {
    private record Button(int x, int y, int w, int h, Component label, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private record Target(double x, double y, String station, String name) {}
    private static final List<Button> buttons = new ArrayList<>();
    private static final List<Target> targets = new ArrayList<>();
    private static final List<Target> choices = new ArrayList<>();
    private static Object screen;
    private static String dimension, selected, title;
    private static boolean menu, expanded, press, dragged, uiPress;
    private static double pressX, pressY;
    private static Button pressed;
    private static int width, height, panelX, panelY, panelW, panelH, scroll, menuScroll, focus = -1;
    private static long lastSeen, boardReceived;
    private static BoardSubscription subscription;
    private static Board board;
    private static final List<Component> rows = new ArrayList<>();
    private static long rowSecond = Long.MIN_VALUE;
    private OverlayUi() {}
    private static Component text(String key, Object... args) { return Component.translatable("mtrmap.ui." + key, args); }
    public static boolean live() { return valid() && expanded && selected != null && MTRMapConfig.INSTANCE.enabled.get(); }
    private static boolean valid() { return screen != null && Minecraft.getInstance().screen == screen && System.nanoTime() - lastSeen < 2_000_000_000L; }
    public static boolean blocks(double x, double y) {
        return valid() && (buttons.stream().anyMatch(b -> b.contains(x, y)) || menu || !choices.isEmpty()
                || selected != null && x >= panelX && x < panelX + panelW && y >= panelY && y < panelY + panelH);
    }
    public static boolean hasPoint(double x, double y) { return valid() && !near(x, y).isEmpty(); }
    private static List<Target> near(double x, double y) {
        return targets.stream().filter(t -> Math.hypot(t.x - x, t.y - y) <= 9)
                .sorted(Comparator.comparingDouble(t -> Math.hypot(t.x - x, t.y - y))).limit(13).toList();
    }
    public static void render(GuiGraphics g, String dim, MapDataCache.DimensionData data, double scale,
                              DoubleBinaryOperator projectX, DoubleBinaryOperator projectY, int mouseX, int mouseY, boolean markers) {
        var mc = Minecraft.getInstance();
        if (screen != mc.screen || !Objects.equals(dimension, dim)) { clear(); screen = mc.screen; dimension = dim; }
        if (screen == null) return;
        lastSeen = System.nanoTime(); width = g.guiWidth(); height = g.guiHeight(); buttons.clear(); targets.clear();
        ClientOverlayRuntime.INSTANCE.world(dim.replaceFirst("/", ":"));
        if (MTRMapConfig.INSTANCE.enabled.get()) {
            var catalog = ClientOverlayRuntime.INSTANCE.world(dim.replaceFirst("/", ":")).map(ClientOverlayRuntime.INSTANCE::catalog).orElse(null);
            for (var marker : data.landmarks) {
                boolean station = marker.type() == MapLandmark.Type.STATION;
                boolean platform = marker.type() == MapLandmark.Type.PLATFORM;
                if (station && !MTRMapConfig.INSTANCE.showStationLandmarks.get() || platform && (!MTRMapConfig.INSTANCE.showPlatformLandmarks.get() || scale < 0.15)
                        || marker.type() == MapLandmark.Type.DEPOT && !MTRMapConfig.INSTANCE.showDepotLandmarks.get()
                        || !marker.hasRoutes() && !MTRMapConfig.INSTANCE.showEmptyStation.get() && marker.type() != MapLandmark.Type.DEPOT) continue;
                double x = projectX.applyAsDouble(marker.x(), marker.z()), y = projectY.applyAsDouble(marker.x(), marker.z());
                if (x < -16 || x > width + 16 || y < -16 || y > height + 16) continue;
                if (markers) {
                    int size = platform ? 5 : 12;
                    var icon = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mtrmap", "textures/atlas/marker/train_" + (marker.type() == MapLandmark.Type.DEPOT ? "depot" : "station") + ".png");
                    if (platform) g.fill((int) x - 2, (int) y - 2, (int) x + 3, (int) y + 3, 0xFFE6D25A);
                    else g.blit(icon, (int) x - size / 2, (int) y - size / 2, size, size, 0, 0, 32, 32, 32, 32);
                }
                String id = station && marker.id().startsWith("station:") ? canonical(marker.id().substring(8)) : null;
                if (platform && catalog != null && marker.id().startsWith("platform:")) {
                    String pid = canonical(marker.id().substring(9));
                    id = catalog.platforms().stream().filter(p -> p.id().equals(pid)).map(Platform::stationId).findFirst().orElse(null);
                }
                if (id != null) targets.add(new Target(x, y, id, marker.name()));
            }
        }
        button(g, 6, 34, 52, text("menu"), () -> { menu = !menu; choices.clear(); focus = -1; });
        // Both map hosts put controls on the side edges and along the bottom.
        panelW = Math.min(280, Math.max(80, width - 64)); panelX = 32; panelY = 60;
        panelH = selected == null ? 0 : expanded ? Math.max(40, Math.min(height - panelY - 36, 240)) : 24;
        if (width < 440 && selected != null) { panelH = expanded ? Math.max(40, Math.min(180, (height - 60) / 2)) : 24; panelY = Math.max(60, height - panelH - 36); }
        if (selected != null && !menu && choices.isEmpty()) renderPanel(g);
        if (menu) renderMenu(g);
        if (!choices.isEmpty()) {
            int y = 60; g.fill(6, y - 2, Math.min(width - 6, 286), Math.min(height - 20, y + choices.size() * 22 + 4), 0xF0111111);
            for (var target : List.copyOf(choices).subList(0, Math.min(choices.size(), Math.max(1, (height - y - 30) / 22)))) {
                button(g, 10, y, Math.min(width - 24, 268), Component.literal(target.name), () -> select(target)); y += 22;
            }
        }
        var registry = ClientOverlayRuntime.INSTANCE.views();
        var active = registry.focus(dim.replaceFirst("/", ":"));
        if (active.isPresent() || registry.overview()) button(g, Math.max(64, width - 210), height - 24, Math.min(204, width - 70),
                registry.overview() ? text("resume_focus") : text("focus", active.get().name()), registry::toggleOverview);
        if (!blocks(mouseX, mouseY) && MTRMapConfig.INSTANCE.enabled.get()) {
            var candidates = near(mouseX, mouseY);
            if (!candidates.isEmpty() && !Objects.equals(candidates.getFirst().station, selected))
                g.renderComponentTooltip(mc.font, List.of(Component.literal(candidates.getFirst().name), candidates.size() > 1 ? text("overlap", candidates.size()) : text("station_hint")), mouseX, mouseY);
        }
    }
    private static void button(GuiGraphics g, int x, int y, int w, Component label, Runnable action) {
        var b = new Button(x, y, w, 20, label, action); buttons.add(b);
        var mc = Minecraft.getInstance(); double mx = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale(), my = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
        int bg = b.contains(mx, my) ? 0xFF444444 : 0xEE222222;
        g.fill(x, y, x + w, y + 20, focus == buttons.size() - 1 ? 0xFFFFFFFF : 0xFF777777);
        g.fill(x + 1, y + 1, x + w - 1, y + 19, bg);
        g.drawString(mc.font, net.minecraft.locale.Language.getInstance().getVisualOrder(mc.font.substrByWidth(label, Math.max(0, w - 8))), x + 4, y + 6, 0xFFFFFF, false);
    }
    private static void renderMenu(GuiGraphics g) {
        var c = MTRMapConfig.INSTANCE;
        g.fill(6, 58, Math.min(width - 6, 228), Math.min(height - 20, 250), 0xF0111111);
        boolean[] values = {c.enabled.get(), c.routeLinesEnabled.get(), c.trackLinesEnabled.get(), c.trainCarsEnabled.get(), c.showStationLandmarks.get(), c.showPlatformLandmarks.get(), c.showDepotLandmarks.get(), c.showEmptyStation.get()};
        String[] names = {"enabled", "routes", "tracks", "trains", "stations", "platforms", "depots", "empty"};
        Runnable[] actions = {() -> c.enabled.set(!c.enabled.get()), () -> c.routeLinesEnabled.set(!c.routeLinesEnabled.get()), () -> c.trackLinesEnabled.set(!c.trackLinesEnabled.get()), () -> c.trainCarsEnabled.set(!c.trainCarsEnabled.get()), () -> c.showStationLandmarks.set(!c.showStationLandmarks.get()), () -> c.showPlatformLandmarks.set(!c.showPlatformLandmarks.get()), () -> c.showDepotLandmarks.set(!c.showDepotLandmarks.get()), () -> c.showEmptyStation.set(!c.showEmptyStation.get())};
        int max = Math.max(1, (height - 90) / 22); int first = Math.min(menuScroll, Math.max(0, names.length - max));
        for (int i = first; i < Math.min(names.length, first + max); i++) button(g, 10, 62 + (i - first) * 22, Math.min(width - 28, 210),
                Component.literal(values[i] ? "✓ " : "□ ").append(text(names[i])), actions[i]);
    }
    private static void renderPanel(GuiGraphics g) {
        var font = Minecraft.getInstance().font;
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0111111);
        g.drawString(font, font.plainSubstrByWidth(title == null ? "" : title.replace('|', ' '), Math.max(0, panelW - 64)), panelX + 6, panelY + 7, 0xFFFFFF, false);
        button(g, panelX + panelW - 50, panelY + 2, 22, Component.literal(expanded ? "−" : "+"), () -> { expanded = !expanded; stop(); });
        button(g, panelX + panelW - 25, panelY + 2, 22, Component.literal("×"), () -> { selected = null; stop(); });
        if (!expanded) return;
        rebuildRows(); int lineHeight = 14, first = Math.min(scroll, Math.max(0, rows.size() - 1));
        g.enableScissor(panelX + 2, panelY + 25, panelX + panelW - 2, panelY + panelH - 2);
        for (int i = first, y = panelY + 28; i < rows.size() && y < panelY + panelH; i++, y += lineHeight)
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(rows.get(i), panelW - 12)), panelX + 6, y, 0xE6E6E6, false);
        g.disableScissor();
    }
    private static void rebuildRows() {
        long second = System.nanoTime() / 1_000_000_000L; if (second == rowSecond) return; rowSecond = second; rows.clear();
        if (board == null) { rows.add(text(io.github.teamcreating.mtrmap.network.MTRNetworkClient.canSendOverlay() ? "loading" : "unsupported")); return; }
        if (board.status() != Status.READY) rows.add(text("state." + board.status().name().toLowerCase(Locale.ROOT)));
        long now = board.serverNow() + (board.status() == Status.STALE ? 0 : Math.max(0, System.nanoTime() - boardReceived) / 1_000_000);
        var services = board.services().stream().sorted(Comparator.comparing(Service::routeId).thenComparing(Service::platformId)).toList();
        var byService = new HashMap<String, List<Arrival>>();
        for (var a : board.arrivals()) byService.computeIfAbsent(a.routeId() + ":" + a.platformId(), ignored -> new ArrayList<>()).add(a);
        String route = "";
        for (var service : services) {
            if (!service.routeId().equals(route)) { route = service.routeId(); rows.add(Component.literal("■ " + service.routeName().replace('|', ' ')).withColor(service.color())); }
            var arrivals = byService.getOrDefault(service.routeId() + ":" + service.platformId(), List.of());
            if (arrivals.isEmpty()) { rows.add(text("no_prediction", service.platformName())); continue; }
            for (var a : arrivals) {
                long seconds = Math.max(0, (a.arrivalAt() - now + 999) / 1000);
                Component eta = a.departureAt() < now ? text("past") : a.arrivalAt() <= now ? text("arrived") : text("eta", seconds / 60, seconds % 60);
                rows.add(Component.literal("→ " + a.destination().replace('|', ' ') + " · " + a.platformName() + " · ").append(eta));
                if (!a.realtime() || a.terminating()) rows.add(text(a.terminating() ? "terminating" : "scheduled"));
            }
        }
        if (services.isEmpty() && board.status() == Status.READY) rows.add(text("no_service"));
    }
    private static String canonical(String id) { return Long.toUnsignedString(Long.parseUnsignedLong(id, 16), 16); }
    private static void select(Target target) { selected = target.station; title = target.name; expanded = true; menu = false; choices.clear(); scroll = 0; stop(); }
    public static boolean down(double x, double y, int button) {
        if (!valid() || button != 0) return false;
        press = true; dragged = false; pressX = x; pressY = y; pressed = buttons.stream().filter(b -> b.contains(x, y)).findFirst().orElse(null); uiPress = blocks(x, y);
        return uiPress;
    }
    public static void pointer(double x, double y) { if (press && Math.hypot(x - pressX, y - pressY) > 4) dragged = true; }
    public static boolean up(double x, double y, int button) {
        if (!valid() || button != 0 || !press) return false;
        pointer(x, y); press = false; boolean consumed = uiPress;
        if (!dragged) {
            if (pressed != null && pressed.contains(x, y)) { focus = buttons.indexOf(pressed); pressed.action.run(); }
            else if (uiPress && (menu || !choices.isEmpty())) { menu = false; choices.clear(); }
            else if (!uiPress && MTRMapConfig.INSTANCE.enabled.get()) {
                var candidates = near(x, y); var unique = new LinkedHashMap<String, Target>(); candidates.forEach(t -> unique.putIfAbsent(t.station, t));
                if (unique.size() == 1) select(unique.values().iterator().next());
                else if (!unique.isEmpty()) { choices.clear(); choices.addAll(unique.values()); menu = false; }
            }
        }
        pressed = null; return consumed;
    }
    public static boolean wheel(double x, double y, double amount) {
        if (!blocks(x, y)) return false;
        if (menu) menuScroll = Math.max(0, Math.min(7, menuScroll - (int) Math.signum(amount) * 3));
        else scroll = Math.max(0, Math.min(Math.max(0, rows.size() - 1), scroll - (int) Math.signum(amount) * 3)); return true;
    }
    public static boolean key(int key, int modifiers) {
        if (!valid()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (menu || !choices.isEmpty()) { menu = false; choices.clear(); return true; }
            if (selected != null) { selected = null; stop(); return true; }
            return false;
        }
        if (key == GLFW.GLFW_KEY_TAB && (menu || selected != null || !choices.isEmpty())) {
            if (!buttons.isEmpty()) { focus = Math.floorMod(focus + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1), buttons.size()); Minecraft.getInstance().getNarrator().sayNow(buttons.get(focus).label); } return true;
        }
        if ((menu || selected != null || !choices.isEmpty()) && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && focus >= 0 && focus < buttons.size()) { buttons.get(focus).action.run(); return true; }
        if (focus >= 0 && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) { scroll = Math.max(0, scroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 8 : -8)); return true; }
        return false;
    }
    public static void tick() {
        if (!valid() || !MTRMapConfig.INSTANCE.enabled.get()) { if (subscription != null) stop(); if (!valid() && screen != null) clear(); return; }
        if (!expanded || selected == null) return;
        if (subscription == null) ClientOverlayRuntime.INSTANCE.world(dimension.replaceFirst("/", ":")).ifPresent(world -> {
            subscription = ClientOverlayRuntime.INSTANCE.watchStation(world, selected, value -> { board = value; boardReceived = System.nanoTime(); rowSecond = Long.MIN_VALUE; });
            board = subscription.latest(); rowSecond = Long.MIN_VALUE;
        });
    }
    private static void stop() { if (subscription != null) { subscription.close(); subscription = null; } board = null; rowSecond = Long.MIN_VALUE; }
    public static void clear() { stop(); screen = null; dimension = selected = title = null; menu = expanded = press = uiPress = false; buttons.clear(); targets.clear(); choices.clear(); scroll = 0; focus = -1; }
}

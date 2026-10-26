package io.github.teamcreating.mtrmap.extension;

import io.github.teamcreating.mtrmap.api.MtrMapApi.*;
import io.github.teamcreating.mtrmap.config.MTRMapConfig;
import io.github.teamcreating.mtrmap.config.PidsStyle;
import io.github.teamcreating.mtrmap.mapdata.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.DoubleBinaryOperator;

/** Classic station tooltip with an optional native arrival board. Host retains the camera. */
public final class OverlayUi {
    private record Button(int x, int y, int w, int h, Component label, boolean enabled, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }
    private record Target(double x, double y, String station, MapLandmark marker) {}
    private static final List<Button> buttons = new ArrayList<>();
    private static final List<Target> targets = new ArrayList<>(), choices = new ArrayList<>();
    private static Object screen;
    private static String dimension, selected;
    private static MapLandmark marker;
    private static boolean expanded, press, dragged, uiPress, navigationChoices;
    private static double pressX, pressY;
    private static Button pressed;
    private static int width, height, panelX, panelY, panelW, panelH, anchorX, anchorY, scroll, visibleRows, leftSafe, focus = -1;
    private static long lastSeen, boardReceived, rowSecond = Long.MIN_VALUE;
    private static BoardSubscription subscription;
    private static Board board;
    private static Catalog targetCatalog;
    private static Map<String,String> platformStations = Map.of();
    private static List<PidsBoardModel.Row> rows = List.of();
    private static final int PAD = 8, ROW_HEIGHT = 22;
    private OverlayUi() {}
    private static Component text(String key, Object... args) { return Component.translatable("mtrmap.ui." + key, args); }
    private static String clean(String value) { return value == null ? "" : value.replace('|', ' '); }
    public static boolean live() { return valid() && expanded && selected != null && MTRMapConfig.INSTANCE.enabled.get(); }
    private static boolean valid() { return screen != null && Minecraft.getInstance().screen == screen && System.nanoTime() - lastSeen < 2_000_000_000L; }
    public static boolean blocks(double x, double y) {
        return valid() && (buttons.stream().anyMatch(b -> b.contains(x, y)) || !choices.isEmpty() || navigationChoices
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
        leftSafe = markers ? 32 : 88;
        var runtime = ClientOverlayRuntime.INSTANCE;
        var catalog = runtime.world(dim.replaceFirst("/", ":")).map(runtime::catalog).orElse(null);
        if (catalog != null && (targetCatalog == null || catalog.platforms() != targetCatalog.platforms())) {
            var index = new HashMap<String,String>(); catalog.platforms().forEach(p -> index.put(p.id(),p.stationId()));
            platformStations = Map.copyOf(index); targetCatalog = catalog;
        }
        if (MTRMapConfig.INSTANCE.enabled.get()) for (var value : data.landmarks) {
            boolean station = value.type() == MapLandmark.Type.STATION, platform = value.type() == MapLandmark.Type.PLATFORM;
            var c = MTRMapConfig.INSTANCE;
            if (station && !c.showStationLandmarks.get() || platform && (!c.showPlatformLandmarks.get() || scale < .15)
                    || value.type() == MapLandmark.Type.DEPOT && !c.showDepotLandmarks.get()
                    || !value.hasRoutes() && !c.showEmptyStation.get() && value.type() != MapLandmark.Type.DEPOT) continue;
            double x = projectX.applyAsDouble(value.x(), value.z()), y = projectY.applyAsDouble(value.x(), value.z());
            if (x < -16 || x > width + 16 || y < -16 || y > height + 16) continue;
            if (markers) {
                int size = platform ? 5 : 12;
                var icon = ResourceLocation.fromNamespaceAndPath("mtrmap", "textures/atlas/marker/train_" + (value.type() == MapLandmark.Type.DEPOT ? "depot" : "station") + ".png");
                if (platform) { g.fill((int)x - 3, (int)y - 3, (int)x + 4, (int)y + 4, 0xDD101010); g.fill((int)x - 2, (int)y - 2, (int)x + 3, (int)y + 3, 0xFFE6D25A); }
                else g.blit(icon, (int)x - size / 2, (int)y - size / 2, size, size, 0, 0, 32, 32, 32, 32);
            }
            String id = null;
            try {
                if (station && value.id().startsWith("station:")) id = canonical(value.id().substring(8));
                if (platform && value.id().startsWith("platform:")) id = platformStations.get(canonical(value.id().substring(9)));
            } catch (NumberFormatException ignored) { }
            if (id != null) {
                targets.add(new Target(x, y, id, value));
                if (id.equals(selected) && (marker == null || value.id().equals(marker.id()))) marker = value;
            }
        }
        // Restore Xaero's original two route/track controls. JourneyMap keeps its native toolbar buttons.
        if (!markers) { toggle(g, 0, "route", MTRMapConfig.INSTANCE.routeLinesEnabled.get()); toggle(g, 1, "track", MTRMapConfig.INSTANCE.trackLinesEnabled.get()); }
        if (selected != null && MTRMapConfig.INSTANCE.enabled.get()) renderPanel(g, mouseX, mouseY);
        if (!choices.isEmpty()) {
            int y = 58, w = Math.min(248, width - 64); frame(g, 32, y, w, Math.min(height - y - 32, choices.size() * 20 + 8));
            for (var target : List.copyOf(choices).subList(0, Math.min(choices.size(), Math.max(1, (height - y - 48) / 20)))) {
                action(g, 36, y + 4, w - 8, 20, Component.literal(clean(target.marker.name())), true, () -> select(target)); y += 20;
            }
        }
        var registry = runtime.views(); var active = registry.focus(dim.replaceFirst("/", ":"));
        if (active.isPresent() || registry.overview()) action(g, Math.max(88, width - 212), height - 22, Math.min(200, width - 96), 18,
                registry.overview() ? text("resume_focus") : text("focus", active.get().name()), true, registry::toggleOverview);
        if (!blocks(mouseX, mouseY) && MTRMapConfig.INSTANCE.enabled.get()) {
            var candidates = near(mouseX, mouseY);
            if (!candidates.isEmpty() && !Objects.equals(candidates.getFirst().station, selected)) {
                var lines = new ArrayList<>(metadata(candidates.getFirst().marker));
                lines.add(candidates.size() > 1 ? text("overlap", candidates.size()) : text("expand").copy().withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(mc.font, lines, mouseX, mouseY);
            }
        }
    }
    private static List<Component> metadata(MapLandmark value) {
        if (value == null) return List.of();
        var result = new ArrayList<Component>();
        result.add(Component.literal(clean(value.name())).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        if (value.type() == MapLandmark.Type.PLATFORM) result.add(text("platform_label", value.symbol()).copy().withStyle(ChatFormatting.GRAY));
        if (!value.description().isEmpty()) {
            result.add(text("routes_label").copy().withStyle(ChatFormatting.GRAY));
            for (String route : value.description().split(", ")) { result.add(Component.literal("- " + clean(route))); if (result.size() >= 8) break; }
        }
        return result;
    }
    private static void toggle(GuiGraphics g, int index, String name, boolean enabled) {
        int x = 5, y = 60 + index * 28;
        var b = new Button(x, y, 78, 23, text(index == 0 ? "routes" : "tracks"), true, () -> {
            var c = MTRMapConfig.INSTANCE; if (index == 0) c.routeLinesEnabled.set(!c.routeLinesEnabled.get()); else c.trackLinesEnabled.set(!c.trackLinesEnabled.get());
        }); buttons.add(b);
        var icon = ResourceLocation.fromNamespaceAndPath("mtrmap", "textures/gui/" + name + (enabled ? "_on" : "_off") + ".png");
        g.blit(icon, x, y, 78, 23, 0, 0, 156, 46, 156, 46);
    }
    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFA100C18);
        g.fill(x, y, x + w, y + 1, 0xFF503080); g.fill(x, y + h - 1, x + w, y + h, 0xFF302040);
        g.fill(x, y, x + 1, y + h, 0xFF503080); g.fill(x + w - 1, y, x + w, y + h, 0xFF302040);
    }
    private static void label(GuiGraphics g, Component value, int x, int y, int w, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(value, Math.max(0,w))), x, y, color, false);
    }
    private static void action(GuiGraphics g, int x, int y, int w, int h, Component value, boolean enabled, Runnable action) {
        var button = new Button(x,y,w,h,value,enabled,action); buttons.add(button);
        var mc = Minecraft.getInstance(); double mx = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale(), my = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();
        if (button.contains(mx,my) || focus == buttons.size() - 1) g.fill(x, y, x+w, y+h, enabled ? 0x503A304C : 0x20202020);
        label(g,value,x+3,y+(h-9)/2,w-6,enabled ? 0xDDDDDD : 0x777777);
    }
    private static void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font; var lines = metadata(marker);
        boolean compact = expanded && height < 260;
        int maxW = Math.max(170, width - leftSafe - 32);
        panelW = expanded ? Math.min(340,maxW) : Math.min(maxW,Math.max(154,lines.stream().mapToInt(font::width).max().orElse(130)+20));
        int metadataHeight = compact ? 29 : 8 + lines.size()*11;
        rebuildRows();
        int available = Math.max(80, height - 60);
        int tableFixed = 32 + (board != null && board.status() != Status.READY ? 12 : 0);
        visibleRows = expanded ? Math.max(1, Math.min(6, (available-metadataHeight-tableFixed-25)/ROW_HEIGHT)) : 0;
        panelH = metadataHeight + (expanded ? tableFixed + visibleRows*ROW_HEIGHT : 5) + 24;
        panelX = Math.max(leftSafe,Math.min(width-panelW-32,anchorX));
        panelY = Math.max(30,Math.min(height-panelH-30,anchorY));
        frame(g,panelX,panelY,panelW,panelH);
        int left=panelX+PAD, content=panelW-PAD*2;
        if (compact) {
            label(g,lines.isEmpty()?Component.literal(""):lines.getFirst(),left,panelY+8,content-15,0xFFFF55);
            label(g,text("routes_summary",marker == null ? "" : clean(marker.description())),left,panelY+19,content,0xA0A0A0);
        } else for(int i=0;i<lines.size();i++) label(g,lines.get(i),left,panelY+8+i*11,content-14,i==0?0xFFFF55:0xDDDDDD);
        action(g,panelX+panelW-18,panelY+3,14,14,Component.literal("×"),true,()->{selected=null;stop();});
        int y=panelY+metadataHeight;
        g.fill(left,y,panelX+panelW-PAD,y+1,0xFF49424F);
        if (expanded) renderBoard(g,left,y+6,content,mouseX,mouseY);
        int footer=panelY+panelH-22;
        g.fill(left,footer,panelX+panelW-PAD,footer+1,0xFF49424F);
        action(g,left-2,footer+2,content/2,18,text(expanded?"collapse":"expand"),true,()->{expanded=!expanded;scroll=0;stop();});
        var providers=ClientOverlayRuntime.INSTANCE.navigation();
        action(g,left+content/2,footer+2,content-content/2+2,18,text("navigate"),!providers.isEmpty(),()->{
            if(providers.size()==1) navigate(providers.getFirst()); else navigationChoices=!navigationChoices;
        });
        if(providers.isEmpty() && mouseX>=left+content/2 && mouseX<left+content && mouseY>=footer && mouseY<footer+20)
            g.renderComponentTooltip(font,List.of(text("navigation_unavailable")),mouseX,mouseY);
        if(navigationChoices) {
            int choiceY=panelY+22; frame(g,panelX+4,choiceY,panelW-8,Math.min(panelH-25,providers.size()*20+8));
            for(var provider:providers) {
                if(choiceY+24>panelY+panelH)break;
                var p=provider; action(g,panelX+8,choiceY+4,panelW-16,20,Component.literal(p.name()),true,()->navigate(p));choiceY+=20;
            }
        }
    }
    private static void navigate(ClientOverlayRuntime.Navigation provider) {
        navigationChoices=false;
        ClientOverlayRuntime.INSTANCE.world(dimension.replaceFirst("/", ":")).ifPresent(world->ClientOverlayRuntime.INSTANCE.navigate(provider,world,selected));
    }
    private static Component eta(PidsBoardModel.Row row) {
        return switch(row.eta()) {
            case MINUTES -> text("minutes",row.value()); case SECONDS -> text("seconds",row.value());
            case ARRIVED -> text("arrived"); case MISSING -> Component.literal("—");
        };
    }
    private static void renderBoard(GuiGraphics g,int left,int y,int content,int mouseX,int mouseY) {
        var theme=PidsStyle.parse(MTRMapConfig.INSTANCE.pidsStyle.get()); var font=Minecraft.getInstance().font;
        var count=Component.literal((Math.min(scroll+1,rows.size()))+"–"+Math.min(rows.size(),scroll+visibleRows)+" / "+rows.size());
        int countWidth=rows.size()>visibleRows?font.width(count)+6:0;
        label(g,text("next_arrivals"),left,y,content-countWidth,theme.destination);
        if(countWidth>0)label(g,count,left+content-countWidth+6,y,countWidth-6,0x77727E);y+=13;
        if(board!=null && board.status()!=Status.READY) { label(g,text("short."+board.status().name().toLowerCase(Locale.ROOT)),left,y,content,0xA6A1AA);y+=12; }
        var cols=PidsBoardModel.columns(content);
        g.fill(left-2,y-2,left+content+2,y+11,0xFF000000|theme.header);
        label(g,text("destination"),left,y,cols.destinationWidth(),0xC8CAD0);
        label(g,text("line"),left+cols.line(),y,cols.lineWidth(),0xC8CAD0);
        label(g,text("platform_short"),left+cols.platform(),y,cols.platformWidth(),0xC8CAD0);
        var in=text("in");label(g,in,left+content-font.width(in),y,cols.etaWidth(),0xC8CAD0); y+=13;
        if(rows.isEmpty()) {
            String state=board==null?(io.github.teamcreating.mtrmap.network.MTRNetworkClient.canSendOverlay()?"loading":"unsupported"):board.status()==Status.READY?"no_service":"state."+board.status().name().toLowerCase(Locale.ROOT);
            label(g,text(state),left,y,content,0xAAA5AE); return;
        }
        scroll=Math.min(scroll,Math.max(0,rows.size()-visibleRows));
        for(int i=scroll;i<Math.min(rows.size(),scroll+visibleRows);i++) {
            var row=rows.get(i);int top=y+(i-scroll)*ROW_HEIGHT;
            if(theme.stripes && i%2==0)g.fill(left-2,top-1,left+content+2,top+ROW_HEIGHT-1,0xFF171B22);
            g.fill(left,top+ROW_HEIGHT-2,left+content,top+ROW_HEIGHT-1,0xFF29252F);
            label(g,Component.literal(clean(row.destination())),left,top+2,cols.destinationWidth(),theme.destination);
            g.fill(left+cols.line(),top+3,left+cols.line()+5,top+8,0xFF000000|row.color());
            label(g,Component.literal(clean(row.route())),left+cols.line()+8,top+2,cols.lineWidth()-8,theme.secondary);
            var platform=Component.literal(clean(row.platform()));
            label(g,platform,left+cols.platform()+Math.max(0,(cols.platformWidth()-font.width(platform))/2),top+2,cols.platformWidth(),theme.destination);
            var time=eta(row);label(g,time,left+content-Math.min(cols.etaWidth(),font.width(time)),top+2,cols.etaWidth(),0xFFD34E);
            if(row.scheduled()||row.terminating())label(g,text(row.terminating()?"terminating":"scheduled_short"),left,top+12,content,0x918C96);
            if(mouseX>=left && mouseX<left+content && mouseY>=top && mouseY<top+ROW_HEIGHT && !navigationChoices) {
                var details=new ArrayList<Component>();details.add(Component.literal(clean(row.destination())).withStyle(ChatFormatting.YELLOW));
                details.add(Component.literal(clean(row.route())));details.add(text("platform_label",clean(row.platform())));details.add(eta(row));
                if(row.eta()==PidsBoardModel.Eta.MISSING)details.add(text("no_prediction",clean(row.platform())));
                if(row.terminating())details.add(text("terminating"));if(row.scheduled())details.add(text("scheduled"));
                g.renderComponentTooltip(font,details,mouseX,mouseY);
            }
        }
    }
    private static void rebuildRows() {
        long second=System.nanoTime()/1_000_000_000L;if(second==rowSecond)return;rowSecond=second;
        long now=board==null?0:board.serverNow()+(board.status()==Status.STALE?0:Math.max(0,System.nanoTime()-boardReceived)/1_000_000);
        rows=PidsBoardModel.rows(board,now);
    }
    private static String canonical(String id) { return Long.toUnsignedString(Long.parseUnsignedLong(id,16),16); }
    private static void select(Target target) {
        selected=target.station;marker=target.marker;expanded=false;choices.clear();navigationChoices=false;scroll=0;
        anchorX=(int)target.x+12;anchorY=(int)target.y-10;stop();
    }
    public static boolean down(double x,double y,int button) {
        if(!valid()||button!=0)return false;
        press=true;dragged=false;pressX=x;pressY=y;
        pressed=buttons.stream().filter(b->b.contains(x,y)).reduce((a,b)->b).orElse(null);uiPress=blocks(x,y);return uiPress;
    }
    public static void pointer(double x,double y) { if(press&&Math.hypot(x-pressX,y-pressY)>4)dragged=true; }
    public static boolean up(double x,double y,int button) {
        if(!valid()||button!=0||!press)return false;
        pointer(x,y);press=false;boolean consumed=uiPress;
        if(!dragged) {
            if(pressed!=null&&pressed.contains(x,y)&&pressed.enabled) { focus=buttons.indexOf(pressed);pressed.action.run(); }
            else if(uiPress&&(!choices.isEmpty()||navigationChoices)) { choices.clear();navigationChoices=false; }
            else if(!uiPress&&MTRMapConfig.INSTANCE.enabled.get()) {
                var unique=new LinkedHashMap<String,Target>();near(x,y).forEach(t->unique.putIfAbsent(t.station,t));
                if(unique.size()==1)select(unique.values().iterator().next());
                else if(!unique.isEmpty()) { choices.clear();choices.addAll(unique.values()); }
            }
        }
        pressed=null;return consumed;
    }
    public static boolean wheel(double x,double y,double amount) {
        if(!blocks(x,y))return false;
        scroll=Math.max(0,Math.min(Math.max(0,rows.size()-visibleRows),scroll-(int)Math.signum(amount)*3));return true;
    }
    public static boolean key(int key,int modifiers) {
        if(!valid())return false;
        if(key==GLFW.GLFW_KEY_ESCAPE) {
            if(!choices.isEmpty()||navigationChoices) { choices.clear();navigationChoices=false;return true; }
            if(selected!=null) { selected=null;stop();return true; }return false;
        }
        if(key==GLFW.GLFW_KEY_TAB&&selected!=null) {
            if(!buttons.isEmpty()) { focus=Math.floorMod(focus+((modifiers&GLFW.GLFW_MOD_SHIFT)!=0?-1:1),buttons.size());Minecraft.getInstance().getNarrator().sayNow(buttons.get(focus).label); }return true;
        }
        if(selected!=null&&(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE)&&focus>=0&&focus<buttons.size()) {
            var b=buttons.get(focus);if(b.enabled)b.action.run();return true;
        }
        if(selected!=null&&expanded&&(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP)) {
            scroll=Math.max(0,Math.min(Math.max(0,rows.size()-visibleRows),scroll+(key==GLFW.GLFW_KEY_PAGE_DOWN?visibleRows:-visibleRows)));return true;
        }
        return false;
    }
    public static void tick() {
        if(!valid()||!MTRMapConfig.INSTANCE.enabled.get()) { if(subscription!=null)stop();if(!valid()&&screen!=null)clear();return; }
        if(!expanded||selected==null)return;
        if(subscription==null)ClientOverlayRuntime.INSTANCE.world(dimension.replaceFirst("/", ":")).ifPresent(world->{
            subscription=ClientOverlayRuntime.INSTANCE.watchStation(world,selected,value->{board=value;boardReceived=System.nanoTime();rowSecond=Long.MIN_VALUE;});
            board=subscription.latest();rowSecond=Long.MIN_VALUE;
        });
    }
    private static void stop() { if(subscription!=null) { subscription.close();subscription=null; }board=null;rows=List.of();rowSecond=Long.MIN_VALUE; }
    public static void clear() { stop();screen=null;dimension=selected=null;marker=null;targetCatalog=null;platformStations=Map.of();expanded=press=uiPress=navigationChoices=false;buttons.clear();targets.clear();choices.clear();scroll=0;focus=-1; }
}

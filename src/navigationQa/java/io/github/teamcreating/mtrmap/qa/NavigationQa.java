package io.github.teamcreating.mtrmap.qa;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import io.github.teamcreating.mtrmap.extension.*;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Real screen input, resource reload, raw framebuffer capture, and read-only integration evidence. */
public final class NavigationQa {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path output=Path.of(System.getProperty("mtrmap.qa.output"));
    private KeyMapping held; private int ticks,release;
    public NavigationQa() throws Exception {
        Files.createDirectories(output);
        var http=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),18762),0);
        http.createContext("/",exchange->{
            try {
                var request=exchange.getRequestMethod().equals("GET")?null:JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                var future=new CompletableFuture<JsonElement>();
                Minecraft.getInstance().execute(()->{try{future.complete(request==null?state():action(request));}catch(Throwable e){future.completeExceptionally(e);}});
                byte[] data=JSON.toJson(future.get(30,TimeUnit.SECONDS)).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);
            }catch(Exception e){byte[] data=e.toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(500,data.length);exchange.getResponseBody().write(data);}finally{exchange.close();}
        });
        http.start();

        System.out.println("[NavigationQA] Local CLI on "+http.getAddress());
    }
    private JsonObject state() throws Exception {
        var mc=Minecraft.getInstance();var result=new JsonObject();
        result.addProperty("connected",mc.player!=null);result.addProperty("screen",mc.screen==null?"":mc.screen.getClass().getName());
        result.addProperty("screenRole",mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen?"TITLE":mc.screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen?"WORLDS":mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen?"DISCONNECTED":"OTHER");
        result.addProperty("overlayCodeSource",ClientOverlayRuntime.class.getProtectionDomain().getCodeSource().getLocation().toString());
        result.addProperty("paused",mc.isPaused());result.addProperty("language",mc.options.languageCode);
        result.addProperty("languageReloading",mc.getOverlay()!=null);
        var labels=new JsonObject();for(var key:List.of("navigate","navigation_unavailable","expand","collapse","routes","tracks","next_arrivals"))labels.addProperty(key,Component.translatable("mtrmap.ui."+key).getString());result.add("labels",labels);
        result.addProperty("guiWidth",mc.getWindow().getGuiScaledWidth());result.addProperty("guiHeight",mc.getWindow().getGuiScaledHeight());
        if(mc.screen instanceof io.github.teamcreating.mtrmap.mixin.client.xaero.XaeroWorldMapAccessor map){
            double scale=map.getScale()/((double)mc.getWindow().getWidth()/mc.getWindow().getGuiScaledWidth());
            result.add("mapProjection",JSON.toJsonTree(Map.of("originX",mc.screen.width/2d-map.getCameraX()*scale,"originY",mc.screen.height/2d-map.getCameraZ()*scale,"xStepX",scale,"xStepY",0d,"zStepX",0d,"zStepY",scale)));
        }else if(mc.screen!=null&&mc.screen.getClass().getName().equals("journeymap.client.ui.fullscreen.Fullscreen")){
            var renderer=Class.forName("io.github.teamcreating.mtrmap.integration.journeymap.JourneyMapForegroundRenderer");
            var field=renderer.getDeclaredField("mapRenderer");field.setAccessible(true);
            if(field.get(null)!=null){
                var project=renderer.getDeclaredMethod("project",double.class,double.class);project.setAccessible(true);
                var origin=(java.awt.geom.Point2D)project.invoke(null,0d,0d);var x=(java.awt.geom.Point2D)project.invoke(null,1d,0d);var z=(java.awt.geom.Point2D)project.invoke(null,0d,1d);double scale=mc.getWindow().getGuiScale();
                result.add("mapProjection",JSON.toJsonTree(Map.of("originX",origin.getX()/scale,"originY",origin.getY()/scale,"xStepX",(x.getX()-origin.getX())/scale,"xStepY",(x.getY()-origin.getY())/scale,"zStepX",(z.getX()-origin.getX())/scale,"zStepY",(z.getY()-origin.getY())/scale)));
            }
        }
        var nativeButtons=new JsonArray();
        if(mc.screen!=null)for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)
            nativeButtons.add(JSON.toJsonTree(Map.of("label",widget.getMessage().getString(),"x",widget.getX(),"y",widget.getY(),"w",widget.getWidth(),"h",widget.getHeight(),"active",widget.active,"visible",widget.visible)));
        result.add("nativeButtons",nativeButtons);
        var runtime=ClientOverlayRuntime.INSTANCE;var providers=new JsonArray();runtime.navigation().forEach(p->providers.add(p.owner()));result.add("providers",providers);
        if(mc.level!=null){
            String dimension=mc.level.dimension().location().getNamespace()+"/"+mc.level.dimension().location().getPath();
            var mapData=io.github.teamcreating.mtrmap.mapdata.MapDataCache.get(dimension);
            result.add("nativeMapData",JSON.toJsonTree(Map.of("serverSynced",io.github.teamcreating.mtrmap.mapdata.MapDataCache.hasServerData(dimension),"routes",mapData.routes.size(),"tracks",mapData.tracks.size(),"landmarks",mapData.landmarks.size())));
            var unsupported=io.github.teamcreating.mtrmap.train.ClientTrainStream.class.getDeclaredField("unsupported");unsupported.setAccessible(true);
            boolean nearby=unsupported.getBoolean(null)||!io.github.teamcreating.mtrmap.config.MTRMapConfig.INSTANCE.networkSyncEnabled.get();
            Class<?> carOwner=nearby?io.github.teamcreating.mtrmap.mapdata.ClientTrainCollector.class:io.github.teamcreating.mtrmap.train.ClientTrainStream.class;
            var renderedCars=carOwner.getDeclaredField(nearby?"cars":"cached");renderedCars.setAccessible(true);
            result.addProperty("trainDataSource",nearby?"nearby":"server");result.add("renderedCars",JSON.toJsonTree(renderedCars.get(null)));
            var world=runtime.world(mc.level.dimension().location().toString()).orElse(null);
            if(world!=null){var catalog=runtime.catalog(world);result.add("catalog",JSON.toJsonTree(catalog));
                var available=new JsonObject();for(var station:catalog.stations())available.add(station.id(),JSON.toJsonTree(runtime.navigation(world,station.id()).stream().map(p->p.owner()).toList()));
                result.add("available",available);result.addProperty("unknownStationAvailable",runtime.navigation(world,"unknown-qa-station").size());
            }
        }
        var selected=OverlayUi.class.getDeclaredField("selected");selected.setAccessible(true);result.add("selected",JSON.toJsonTree(selected.get(null)));
        var targets=OverlayUi.class.getDeclaredField("targets");targets.setAccessible(true);result.add("targets",JSON.toJsonTree(targets.get(null)));
        var buttons=OverlayUi.class.getDeclaredField("buttons");buttons.setAccessible(true);var values=new JsonArray();
        for(var button:(List<?>)buttons.get(null)){
            var row=new JsonObject();for(String key:List.of("x","y","w","h","enabled","label")){
                var method=button.getClass().getDeclaredMethod(key);method.setAccessible(true);var value=method.invoke(button);
                row.add(key,value instanceof Component c?new JsonPrimitive(c.getString()):JSON.toJsonTree(value));
            }values.add(row);
        }result.add("buttons",values);
        try{result.add("serverEvents",JSON.toJsonTree(Class.forName("io.github.teamcreating.mtrmap.qa.QaForge").getField("EVENTS").get(null)));}catch(ReflectiveOperationException ignored){}
        var nativeBoard=OverlayUi.class.getDeclaredField("board");nativeBoard.setAccessible(true);result.add("board",JSON.toJsonTree(nativeBoard.get(null)));
        try {
            var navigation=Class.forName("bili.dongsz.howtogo.client.Navigation");var target=navigation.getMethod("target").invoke(null);
            if(target!=null){var row=new JsonObject();for(String key:List.of("name","x","y","z","source"))row.add(key,JSON.toJsonTree(target.getClass().getMethod(key).invoke(target)));result.add("howtogoTarget",row);}
        }catch(ClassNotFoundException ignored){}
        return result;
    }
    private JsonElement action(JsonObject request) throws Exception {
        var mc=Minecraft.getInstance();String op=request.get("op").getAsString();
        switch(op){
            case "resume"->{mc.setScreen(null);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(6);mc.options.framerateLimit().set(60);}
            case "command"->{var server=mc.getSingleplayerServer();if(server==null)throw new IllegalStateException("Singleplayer only");String command=request.get("command").getAsString();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),command));}
            case "key"->{held=Arrays.stream(mc.options.keyMappings).filter(k->k.getName().equals(request.get("name").getAsString())).findFirst().orElseThrow();key(1);release=ticks+2;}
            case "escape"->{key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,1);key(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0);}
            case "click"->{
                move(request.get("x").getAsDouble(),request.get("y").getAsDouble());
                var method=nativeInput(new String[]{"onPress","m_91530_","method_1601"},long.class,int.class,int.class,int.class);
                method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),0,1,0);
                method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),0,0,0);
            }
            case "move"->move(request.get("x").getAsDouble(),request.get("y").getAsDouble());
            case "scroll"->{move(mc.getWindow().getGuiScaledWidth()/2d,mc.getWindow().getGuiScaledHeight()/2d);var method=nativeInput(new String[]{"onScroll","m_91526_","method_1598"},long.class,double.class,double.class);method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),0d,request.get("amount").getAsDouble());}
            case "language"->{String language=request.get("language").getAsString();if(mc.getLanguageManager().getLanguage(language)==null)throw new IllegalArgumentException("Unknown language");mc.options.languageCode=language;mc.getLanguageManager().setSelected(language);mc.options.save();mc.reloadResourcePacks();}
            case "screenshot"->{String name=request.get("name").getAsString();if(!name.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Simple screenshot name required");try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(output.resolve(name+".png"));}}
            case "quit"->mc.stop();
            default->throw new IllegalArgumentException(op);
        }
        return JSON.toJsonTree(Map.of("ok",true,"op",op));
    }
    public void tick() { ticks++;if(held!=null&&ticks>=release){try{key(0);}catch(Exception e){throw new IllegalStateException("Native key release failed",e);}held=null;} }
    private void key(int action) throws Exception {
        key(com.mojang.blaze3d.platform.InputConstants.getKey(held.saveString()).getValue(),action);
    }
    private void key(int keyCode,int action) throws Exception {
        var mc=Minecraft.getInstance();
        for(var name:List.of("keyPress","m_90893_","method_1466"))try{
            var method=KeyboardHandler.class.getDeclaredMethod(name,long.class,int.class,int.class,int.class,int.class);method.setAccessible(true);
            method.invoke(mc.keyboardHandler,mc.getWindow().getWindow(),keyCode,0,action,0);return;
        }catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException("Native keyboard input mapping unavailable");
    }
    // Official Mojang/SRG/intermediary names from the selected toolchains; QA input only.
    private static java.lang.reflect.Method nativeInput(String[] names,Class<?>... types) throws NoSuchMethodException {
        for(var name:names)try{var method=MouseHandler.class.getDeclaredMethod(name,types);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException("Native mouse input mapping unavailable");
    }
    private static void move(double x,double y) throws Exception {
        var mc=Minecraft.getInstance();var method=nativeInput(new String[]{"onMove","m_91561_","method_1600"},long.class,double.class,double.class);
        method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x*mc.getWindow().getGuiScale(),y*mc.getWindow().getGuiScale());
    }
}

package io.github.teamcreating.mtrmap.qa;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
@Mod("mtrmapqa")
public final class QaForge {
 public static final java.util.List<String> EVENTS=new java.util.concurrent.CopyOnWriteArrayList<>();
 public QaForge() throws Exception {var qa=new NavigationQa();
 if(Boolean.getBoolean("mtrmap.qa.loadWarning")) {
  net.minecraftforge.fml.ModLoader.get().addWarning(new net.minecraftforge.fml.ModLoadingWarning(
   net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer().getModInfo(),
   net.minecraftforge.fml.ModLoadingStage.CONSTRUCT,"fml.modloading.brokenresources",
   "mtrmapqa (intentional acceptance warning)"));
 }
 net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
  (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e)->{
   if(Boolean.getBoolean("mtrmap.qa.failClientSetup"))
    throw new IllegalStateException("Intentional acceptance-test client setup failure");
  });
 MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerAboutToStartEvent e)->trace("about-to-start",e.getServer()));
 MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartingEvent e)->trace("starting",e.getServer()));
 MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e)->trace("started",e.getServer()));
MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e)->{if(e.phase==TickEvent.Phase.END)qa.tick();});}
 private static void trace(String stage,net.minecraft.server.MinecraftServer server) {
  EVENTS.add(System.currentTimeMillis()+" "+stage+" event received");
  var logger=org.slf4j.LoggerFactory.getLogger("AcceptanceQA");
  for(var type:java.util.List.of("xaero.map.server.MinecraftServerData","xaero.common.server.MinecraftServerData"))try {
   var data=Class.forName(type).getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,server);
   EVENTS.add(System.currentTimeMillis()+" "+stage+" server="+System.identityHashCode(server)+" "+type+" initialized="+(data!=null));
   logger.info("[AcceptanceQA] {} server={} {} initialized={}",stage,System.identityHashCode(server),type,data!=null);
  }catch(ReflectiveOperationException e){logger.info("[AcceptanceQA] {} {} unavailable",stage,type);}
 }
}

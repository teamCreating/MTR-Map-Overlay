package io.github.teamcreating.mtrmap.qa;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
@Mod("mtrmapqa")
public final class QaForge {
 public QaForge() throws Exception {var qa=new NavigationQa();MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e)->{if(e.phase==TickEvent.Phase.END)qa.tick();});}
}

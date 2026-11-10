package io.github.teamcreating.mtrmap.qa;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@Mod(value="mtrmapqa",dist=Dist.CLIENT)
public final class QaNeoForge {
 public QaNeoForge() throws Exception { var qa=new NavigationQa();NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->qa.tick()); }
}

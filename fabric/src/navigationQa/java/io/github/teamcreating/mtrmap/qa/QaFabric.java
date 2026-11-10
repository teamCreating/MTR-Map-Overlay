package io.github.teamcreating.mtrmap.qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
public final class QaFabric implements ClientModInitializer {
 public void onInitializeClient() { try {var qa=new NavigationQa();ClientTickEvents.END_CLIENT_TICK.register(mc->qa.tick());}catch(Exception e){throw new IllegalStateException(e);} }
}

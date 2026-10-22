package io.github.teamcreating.mtrmap.integration.journeymap;

import net.minecraftforge.common.MinecraftForge;
import journeymap.client.api.event.forge.FullscreenDisplayEvent;

final class JourneyMapEventBridge {
    static void register() {
        MinecraftForge.EVENT_BUS.addListener((FullscreenDisplayEvent.AddonButtonDisplayEvent event) ->
                JourneyMapToolbar.addButtons(event.getThemeButtonDisplay()));
    }
}

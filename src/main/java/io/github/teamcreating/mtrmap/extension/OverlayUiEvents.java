package io.github.teamcreating.mtrmap.extension;
import io.github.teamcreating.mtrmap.MTRMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = MTRMap.MOD_ID, value = Dist.CLIENT)
public final class OverlayUiEvents {
    private OverlayUiEvents() {}
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void render(ScreenEvent.Render.Post e) {
        if (e.getScreen().getClass().getName().equals("xaero.map.gui.GuiMap")) io.github.teamcreating.mtrmap.integration.xaero.XaeroRouteRenderer.renderUi();
    }
    @SubscribeEvent public static void down(ScreenEvent.MouseButtonPressed.Pre e) { if (OverlayUi.down(e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true); }
    @SubscribeEvent public static void up(ScreenEvent.MouseButtonReleased.Pre e) { if (OverlayUi.up(e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true); }
    @SubscribeEvent public static void wheel(ScreenEvent.MouseScrolled.Pre e) { if (OverlayUi.wheel(e.getMouseX(), e.getMouseY(), e.getScrollDeltaY())) e.setCanceled(true); }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre e) { if (OverlayUi.key(e.getKeyCode(), e.getModifiers())) e.setCanceled(true); }
}

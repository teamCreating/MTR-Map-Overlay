package com.lx862.mtrmap.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin plugin that conditionally loads Xaero World Map mixins
 * only if the Xaero World Map mod is present on the classpath.
 * Xaero's World Map is closed-source and has no official overlay API, so the
 * path layer is rendered by mixin-ing into {@code xaero.map.gui.GuiMap}.
 */
public class XaeroMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("MTRMap-Mixin");
    private static final String XAERO_WORLD_MAP_CLASS = "xaero.map.gui.GuiMap";
    private boolean xaeroWorldMapPresent;

    @Override
    public void onLoad(String mixinPackage) {
        xaeroWorldMapPresent = getClass().getClassLoader()
                .getResource(XAERO_WORLD_MAP_CLASS.replace('.', '/') + ".class") != null;
        if (xaeroWorldMapPresent) {
            LOGGER.info("[MTRMap] Xaero's World Map detected - map path layer mixins will be applied");
        } else {
            LOGGER.info("[MTRMap] Xaero's World Map not found - map path layer mixins skipped");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Only apply xaero-related mixins if Xaero World Map is present
        if (mixinClassName.contains(".xaero.")) {
            return xaeroWorldMapPresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains(".xaero.")) {
            LOGGER.info("[MTRMap] Applied {} to {}", mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1),
                    targetClassName.substring(targetClassName.lastIndexOf('.') + 1));
        }
    }
}

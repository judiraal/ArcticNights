package com.judiraal.arcticnights;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionalMixin implements IMixinConfigPlugin {
    private static final Pattern MIXIN_PATTERN = Pattern.compile("mixin\\.([^.]+)");
    private static final String SERENE_SEASONS_MOD_ID = "sereneseasons";
    private static final String VANILLA_SERVER_LEVEL_PRECIPITATION_MIXIN =
            "com.judiraal.arcticnights.mixin.minecraft.VanillaServerLevelPrecipitationMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (VANILLA_SERVER_LEVEL_PRECIPITATION_MIXIN.equals(mixinClassName)
                && FMLLoader.getLoadingModList().getModFileById(SERENE_SEASONS_MOD_ID) != null) {
            return false;
        }
        Matcher matcher = MIXIN_PATTERN.matcher(mixinClassName);
        if (matcher.find()) return FMLLoader.getLoadingModList().getModFileById(matcher.group(1)) != null;
        return false;
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
    }
}

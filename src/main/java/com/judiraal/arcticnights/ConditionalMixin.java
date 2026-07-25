package com.judiraal.arcticnights;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConditionalMixin implements IMixinConfigPlugin {
    private static final String SERENE_SEASONS_MOD_ID = "sereneseasons";
    private static final String VANILLA_SERVER_LEVEL_PRECIPITATION_MIXIN =
            "com.judiraal.arcticnights.mixin.minecraft.VanillaServerLevelPrecipitationMixin";
    private static final Set<String> BASE_MIXINS = Set.of(
            "com.judiraal.arcticnights.mixin.minecraft.BiomeManagerAccessor",
            "com.judiraal.arcticnights.mixin.minecraft.ChunkStatusTasksMixin",
            "com.judiraal.arcticnights.mixin.minecraft.ChunkMapAccessor",
            "com.judiraal.arcticnights.mixin.minecraft.ChunkGeneratorMixin",
            "com.judiraal.arcticnights.mixin.minecraft.Climate$SamplerMixin",
            "com.judiraal.arcticnights.mixin.minecraft.ClientLevelMixin",
            "com.judiraal.arcticnights.mixin.minecraft.DaylightDetectorBlockMixin",
            "com.judiraal.arcticnights.mixin.minecraft.FogRendererMixin",
            "com.judiraal.arcticnights.mixin.minecraft.FrostedIceBlockMixin",
            "com.judiraal.arcticnights.mixin.minecraft.IceBlockMixin",
            "com.judiraal.arcticnights.mixin.minecraft.ItemPropertiesMixin",
            "com.judiraal.arcticnights.mixin.minecraft.LevelChunkMixin",
            "com.judiraal.arcticnights.mixin.minecraft.LevelRandomAccessor",
            "com.judiraal.arcticnights.mixin.minecraft.LevelMixin",
            "com.judiraal.arcticnights.mixin.minecraft.LevelRendererMixin",
            "com.judiraal.arcticnights.mixin.minecraft.LocalMobCapCalculatorMixin",
            "com.judiraal.arcticnights.mixin.minecraft.NaturalSpawnerMixin",
            "com.judiraal.arcticnights.mixin.minecraft.NoiseBasedChunkGeneratorMixin",
            "com.judiraal.arcticnights.mixin.minecraft.RenderChunkRegionVisualSnowMixin",
            "com.judiraal.arcticnights.mixin.minecraft.SectionCompilerVisualSnowMixin",
            "com.judiraal.arcticnights.mixin.minecraft.ServerChunkCacheAccessor",
            "com.judiraal.arcticnights.mixin.minecraft.ServerLevelMixin",
            VANILLA_SERVER_LEVEL_PRECIPITATION_MIXIN,
            "com.judiraal.arcticnights.mixin.minecraft.SnowLayerBlockMixin",
            "com.judiraal.arcticnights.mixin.minecraft.SpreadingSnowyDirtBlockMixin",
            "com.judiraal.arcticnights.mixin.minecraft.UndeadIceSpawnSurfaceMixin"
    );
    private static final Map<String, String> REQUIRED_MOD_BY_MIXIN = Map.ofEntries(
            Map.entry("com.judiraal.arcticnights.mixin.cold_sweat.RegistryMultiMapMixin", "cold_sweat"),
            Map.entry("com.judiraal.arcticnights.mixin.cold_sweat.WorldTemperatureReadoutMixin", "cold_sweat"),
            Map.entry("com.judiraal.arcticnights.mixin.iris.CelestialUniformsMixin", "iris"),
            Map.entry("com.judiraal.arcticnights.mixin.iris.IrisRenderingPipelineMixin", "iris"),
            Map.entry("com.judiraal.arcticnights.mixin.iris.PackDirectivesMixin", "iris"),
            Map.entry("com.judiraal.arcticnights.mixin.iris.ShadowRendererMixin", "iris"),
            Map.entry("com.judiraal.arcticnights.mixin.iris.WorldTimeUniformsMixin", "iris"),
            Map.entry("com.judiraal.arcticnights.mixin.sodium.ChunkBuilderMeshingTaskVisualSnowMixin", "sodium"),
            Map.entry("com.judiraal.arcticnights.mixin.sodium.LevelSliceVisualSnowPatchMixin", "sodium"),
            Map.entry("com.judiraal.arcticnights.mixin.sereneseasons.RandomUpdateHandlerMixin", SERENE_SEASONS_MOD_ID),
            Map.entry("com.judiraal.arcticnights.mixin.sereneseasons.SeasonHooksMixin", SERENE_SEASONS_MOD_ID),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.ClientWeatherHelperMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.ClientWeatherProxyMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.SceneEnhancerMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.StormObjectTopYBlockMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.StormObjectTornadoSeasonMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.TornadoHelperMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.WeatherCommandMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.WeatherObjectParticleStormMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.WeatherUtilBlockMixin", "weather2"),
            Map.entry("com.judiraal.arcticnights.mixin.weather2.WindManagerHeightMixin", "weather2")
    );

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
                && isLoaded(SERENE_SEASONS_MOD_ID)) {
            return false;
        }

        String requiredMod = REQUIRED_MOD_BY_MIXIN.get(mixinClassName);
        if (requiredMod != null) return isLoaded(requiredMod);
        if (BASE_MIXINS.contains(mixinClassName)) return true;

        ArcticNights.LOGGER.warn("No explicit Arctic Nights mixin rule for {}; not applying", mixinClassName);
        return false;
    }

    private static boolean isLoaded(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
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

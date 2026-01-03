package com.judiraal.arcticnights.mixin.weather2;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.util.TornadoHandler;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import weather2.config.ConfigTornado;
import weather2.util.WeatherUtil;
import weather2.weathersystem.storm.StormObject;
import weather2.weathersystem.storm.TornadoHelper;

@Mixin(TornadoHelper.class)
public class TornadoHelperMixin {
    @Shadow
    public StormObject storm;

    @Redirect(method = "canGrab", at = @At(value = "INVOKE", target = "Lweather2/util/WeatherUtil;shouldGrabBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean arcticnights$protectTornadoTaggedBlocks(Level level, BlockState state, @Local(argsOnly = true) BlockPos pos) {
        if (state.is(ArcticNights.Blocks.TORNADO_PROTECTED)) return false;
        if (!ArcticNightsConfig.alternativeTornadoStrengthGrabbing.getAsBoolean())
            return WeatherUtil.shouldGrabBlock(level, state);
        if (ConfigTornado.Storm_Tornado_GrabCond_List || !ConfigTornado.Storm_Tornado_GrabCond_StrengthGrabbing)
            return WeatherUtil.shouldGrabBlock(level, state);
        return TornadoHandler.shouldGrabBlock(level, state, pos, storm);
    }
}

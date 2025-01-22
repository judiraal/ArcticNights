package com.judiraal.arcticnights.mixin.iris;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IrisRenderingPipeline.class)
public class IrisRenderingPipelineMixin {
    @Final
    @Shadow
    private float sunPathRotation;

    /**
     * @author Judiraal
     * @reason Dynamic sunPathRotation
     */
    @Overwrite
    public float getSunPathRotation() {
        return ArcticNightsConfig.shaderSunPathRotation.get() ? ArcticNights.Client.currentSunAngle() / (float) Math.PI * 180 : sunPathRotation;
    }
}

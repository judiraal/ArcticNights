package com.judiraal.arcticnights.mixin.oculus;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PackDirectives.class)
public class PackDirectivesMixin {
    @Shadow
    private float sunPathRotation;

    /**
     * @author Judiraal
     * @reason Dynamic sunPathRotation
     */
    @Overwrite(remap = false)
    public float getSunPathRotation() {
        return ArcticNightsConfig.shaderSunPathRotation.get() ? ArcticNights.Client.currentSunAngle() / (float) Math.PI * 180 : sunPathRotation;
    }
}

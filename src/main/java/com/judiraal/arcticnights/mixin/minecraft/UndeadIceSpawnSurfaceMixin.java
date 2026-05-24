package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.judiraal.arcticnights.util.ArcticSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class UndeadIceSpawnSurfaceMixin {
    @Shadow
    public abstract BlockState asState();

    @Inject(method = "isValidSpawn", at = @At("HEAD"), cancellable = true)
    private void arcticnights$undeadColdSpawnSurface(BlockGetter level, BlockPos pos, EntityType<?> entityType, CallbackInfoReturnable<Boolean> cir) {
        if (!ArcticNightsFeatures.arcticSpawning()) return;
        if (level instanceof Level actualLevel && !ArcticNights.appliesTo(actualLevel)) return;
        if (ArcticSpawner.isUndeadColdSpawnSurface(this.asState(), entityType)) {
            cir.setReturnValue(true);
        }
    }
}

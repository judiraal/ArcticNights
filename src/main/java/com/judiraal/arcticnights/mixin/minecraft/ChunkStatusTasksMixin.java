package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
    @ModifyExpressionValue(method = "generateStructureStarts", at = @At(value = "INVOKE", target = "net/minecraft/world/level/levelgen/WorldOptions.generateStructures ()Z"))
    private static boolean arcticnights$checkStructureGen(boolean original, @Local(argsOnly = true) ChunkAccess chunk, @Local(argsOnly = true) WorldGenContext worldGenContext) {
        if (worldGenContext.level().dimension() != Level.OVERWORLD) return original;
        int z = chunk.getPos().z << 4;
        if (z >= ArcticNightsConfig.structureDenyRange.get().get(0) && z < ArcticNightsConfig.structureDenyRange.get().get(1)) return false;
        return original;
    }
}

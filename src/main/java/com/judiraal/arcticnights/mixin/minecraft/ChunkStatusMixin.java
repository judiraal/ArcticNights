package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkStatus.class)
public class ChunkStatusMixin {
    @Redirect(method = "lambda$static$2", at = @At(value = "INVOKE", target = "net/minecraft/world/level/levelgen/WorldOptions.generateStructures ()Z"))
    private static boolean an$checkStructureGen(WorldOptions instance, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) ChunkAccess chunk) {
        if (level.dimension() != Level.OVERWORLD) return instance.generateStructures();
        int z = chunk.getPos().z << 4;
        if (z >= ArcticNightsConfig.structureDenyRange.get().get(0) && z < ArcticNightsConfig.structureDenyRange.get().get(1)) return false;
        return instance.generateStructures();
    }
}

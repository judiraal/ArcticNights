package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.worldgen.StructureExclusionContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusTasksMixin {
    @WrapOperation(
            method = "generateStructureStarts",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;createStructures(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;)V")
    )
    private static void arcticnights$filterStructureStarts(
            ChunkGenerator generator,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            ChunkAccess chunk,
            StructureTemplateManager structureTemplateManager,
            Operation<Void> original,
            @Local(argsOnly = true) WorldGenContext worldGenContext
    ) {
        if (worldGenContext.level().dimension() != Level.OVERWORLD || !isInStructureDenyRange(chunk)) {
            original.call(generator, registryAccess, structureState, structureManager, chunk, structureTemplateManager);
            return;
        }

        StructureExclusionContext.withExclusion(() ->
                original.call(generator, registryAccess, structureState, structureManager, chunk, structureTemplateManager));
    }

    private static boolean isInStructureDenyRange(ChunkAccess chunk) {
        int z = chunk.getPos().z << 4;
        return z >= ArcticNightsConfig.structureDenyRange.get().get(0) && z < ArcticNightsConfig.structureDenyRange.get().get(1);
    }
}

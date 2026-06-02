package com.judiraal.arcticnights.mixin.sodium;

import com.judiraal.arcticnights.client.VisualSnowLevelSliceBridge;
import com.judiraal.arcticnights.client.VisualSnowPipeline;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskVisualSnowMixin {
    @Final
    @Shadow
    private ChunkRenderContext renderContext;

    @WrapMethod(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;")
    private ChunkBuildOutput arcticnights$withVisualSnowCoverSubstitution(
            ChunkBuildContext buildContext,
            CancellationToken cancellationToken,
            Operation<ChunkBuildOutput> original
    ) {
        try (VisualSnowPipeline.Scope ignored = VisualSnowPipeline.pushFromRenderers(renderContext.getRenderers())) {
            VisualSnowPipeline.Substitution substitution = VisualSnowPipeline.currentSubstitution();
            if (substitution != null) {
                substitution.beginSodiumBuild();
            }
            return original.call(buildContext, cancellationToken);
        }
    }

    @Redirect(
            method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;"
            )
    )
    private BlockState arcticnights$substituteVisualSnowCoverState(
            LevelSlice levelSlice,
            int x,
            int y,
            int z
    ) {
        Object bridgeTarget = levelSlice;
        VisualSnowLevelSliceBridge bridge = (VisualSnowLevelSliceBridge) bridgeTarget;
        BlockState state = bridge.arcticnights$blockStateAt(x, y, z);
        VisualSnowPipeline.Substitution substitution = VisualSnowPipeline.currentSubstitution();
        if (substitution == null) {
            return state;
        }

        return bridge.arcticnights$substituteVisualSnowAt(substitution, x, y, z, state);
    }
}

package com.judiraal.arcticnights.mixin.minecraft;

import com.judiraal.arcticnights.client.VisualSnowPipeline;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SectionCompiler.class)
public class SectionCompilerVisualSnowMixin {
    @WrapMethod(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;")
    private SectionCompiler.Results arcticnights$withVisualSnowCoverSubstitution(
            SectionPos sectionPos,
            RenderChunkRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack sectionBufferBuilderPack,
            List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
            Operation<SectionCompiler.Results> original
    ) {
        try (VisualSnowPipeline.Scope ignored = VisualSnowPipeline.pushFromRenderers(additionalRenderers)) {
            return original.call(sectionPos, region, vertexSorting, sectionBufferBuilderPack, additionalRenderers);
        }
    }
}

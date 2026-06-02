package com.judiraal.arcticnights.client;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = ArcticNights.MOD_ID, value = Dist.CLIENT)
public final class VisualSnowCoverRenderer {
    private static List<String> cachedAllowEntries = List.of();
    private static List<String> cachedDenyEntries = List.of();
    private static List<String> cachedBuriedOriginalEntries = List.of();
    private static Matcher cachedMatcher = new Matcher(MatchSet.EMPTY, MatchSet.EMPTY);
    private static MatchSet cachedBuriedOriginals = MatchSet.EMPTY;

    private VisualSnowCoverRenderer() {
    }

    @SubscribeEvent
    public static void onAddSectionGeometry(AddSectionGeometryEvent event) {
        if (!ArcticNightsFeatures.visualSnowCover()) return;

        Level level = event.getLevel();
        if (!ArcticNights.appliesTo(level)) return;
        if (!ArcticNightsFeatures.visualSnowCoverPipelineSubstitution()) return;

        Rules rules = Rules.fromConfig();
        Matcher matcher = matcherFromConfig();
        MatchSet buriedOriginals = buriedOriginalsFromConfig();
        VisualSnowPipeline.Substitution substitution = new VisualSnowPipeline.Substitution(matcher, rules);

        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        event.addRenderer(new OriginalCoverRenderer(blockRenderer, substitution, buriedOriginals));
    }

    private static Matcher matcherFromConfig() {
        List<String> allowEntries = copyEntries(ArcticNightsConfig.visualSnowCoverAllowlist.get());
        List<String> denyEntries = copyEntries(ArcticNightsConfig.visualSnowCoverDenylist.get());
        if (allowEntries.equals(cachedAllowEntries) && denyEntries.equals(cachedDenyEntries)) {
            return cachedMatcher;
        }

        cachedAllowEntries = allowEntries;
        cachedDenyEntries = denyEntries;
        cachedMatcher = new Matcher(MatchSet.from(allowEntries), MatchSet.from(denyEntries));
        return cachedMatcher;
    }

    private static MatchSet buriedOriginalsFromConfig() {
        List<String> entries = copyEntries(ArcticNightsConfig.visualSnowCoverBuriedOriginals.get());
        if (entries.equals(cachedBuriedOriginalEntries)) {
            return cachedBuriedOriginals;
        }

        cachedBuriedOriginalEntries = entries;
        cachedBuriedOriginals = MatchSet.from(entries);
        return cachedBuriedOriginals;
    }

    private static List<String> copyEntries(List<? extends String> entries) {
        List<String> copy = new ArrayList<>();
        for (String entry : entries) {
            copy.add(entry);
        }
        return List.copyOf(copy);
    }

    private static void renderOriginalCover(
            AddSectionGeometryEvent.SectionRenderingContext context,
            BlockRenderDispatcher blockRenderer,
            VisualSnowPipeline.Substitution substitution,
            MatchSet buriedOriginals
    ) {
        BlockAndTintGetter region = new OriginalStateRegion(context.getRegion(), substitution);
        PoseStack poseStack = context.getPoseStack();
        RandomSource random = RandomSource.create();

        VisualSnowPipeline.withOriginalStates(() -> {
            for (VisualSnowPipeline.Entry entry : substitution.renderEntries()) {
                if (!substitution.wasSubstituted(entry.pos())) continue;

                BlockState state = entry.state();
                if (state.getRenderShape() != RenderShape.MODEL) continue;
                if (buriedOriginals.matches(state)) continue;

                BlockPos pos = entry.pos();
                BakedModel model = blockRenderer.getBlockModel(state);
                ModelData modelData = model.getModelData(region, pos, state, region.getModelData(pos));
                random.setSeed(state.getSeed(pos));

                for (RenderType renderType : model.getRenderTypes(state, random, modelData)) {
                    poseStack.pushPose();
                    poseStack.translate(
                            (float) SectionPos.sectionRelative(pos.getX()),
                            (float) SectionPos.sectionRelative(pos.getY()),
                            (float) SectionPos.sectionRelative(pos.getZ())
                    );
                    blockRenderer.renderBatched(
                            state,
                            pos,
                            region,
                            poseStack,
                            context.getOrCreateChunkBuffer(renderType),
                            true,
                            random,
                            modelData,
                            renderType
                    );
                    poseStack.popPose();
                }
            }
        });
    }

    static boolean shouldRenderVisualSnow(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Matcher matcher,
            Rules rules
    ) {
        if (!isEligibleVisualSnowCover(level, pos, state, matcher)) return false;

        int realSnowLayers = 0;
        int effectiveLayers = 0;
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            neighbor.setWithOffset(pos, direction);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.is(Blocks.SNOW)) {
                realSnowLayers++;
                effectiveLayers++;
            } else if (rules.countEligibleNeighbors()
                    && isEligibleVisualSnowCover(level, neighbor, neighborState, matcher)) {
                effectiveLayers++;
            }
        }

        if (rules.countEligibleNeighbors()) {
            return realSnowLayers >= rules.requiredRealAdjacentLayers()
                    && effectiveLayers >= rules.requiredAdjacentLayers();
        }
        return realSnowLayers >= rules.requiredAdjacentLayers();
    }

    static boolean isEligibleVisualSnowCover(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Matcher matcher
    ) {
        if (state.isAir()
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        return matcher.allows(state) && canSupportVisualSnow(level, pos);
    }

    private static boolean canSupportVisualSnow(BlockAndTintGetter level, BlockPos pos) {
        BlockPos supportPos = pos.below();
        BlockState support = level.getBlockState(supportPos);
        if (support.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) return false;
        if (support.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) return true;
        return Block.isFaceFull(support.getCollisionShape(level, supportPos), Direction.UP)
                || support.is(Blocks.SNOW) && support.getValue(SnowLayerBlock.LAYERS) == 8;
    }

    private record OriginalCoverRenderer(
            BlockRenderDispatcher blockRenderer,
            VisualSnowPipeline.Substitution substitution,
            MatchSet buriedOriginals
    ) implements AddSectionGeometryEvent.AdditionalSectionRenderer, VisualSnowPipeline.Source {
        @Override
        public void render(AddSectionGeometryEvent.SectionRenderingContext context) {
            renderOriginalCover(context, blockRenderer, substitution, buriedOriginals);
        }

        @Override
        public VisualSnowPipeline.Substitution arcticnights$visualSnowSubstitution() {
            return substitution;
        }
    }

    record Matcher(MatchSet allow, MatchSet deny) {
        boolean allows(BlockState state) {
            return allow.matches(state) && !deny.matches(state);
        }
    }

    record Rules(
            int requiredAdjacentLayers,
            boolean countEligibleNeighbors,
            int requiredRealAdjacentLayers
    ) {
        static Rules fromConfig() {
            return new Rules(
                    ArcticNightsFeatures.visualSnowCoverRequiredAdjacentLayers(),
                    ArcticNightsFeatures.visualSnowCoverCountEligibleNeighbors(),
                    ArcticNightsFeatures.visualSnowCoverRequiredRealAdjacentLayers()
            );
        }
    }

    record MatchSet(ReferenceSet<Block> blocks, List<TagKey<Block>> tags) {
        private static final MatchSet EMPTY = new MatchSet(new ReferenceOpenHashSet<>(), List.of());

        static MatchSet from(List<? extends String> entries) {
            ReferenceSet<Block> blocks = new ReferenceOpenHashSet<>();
            List<TagKey<Block>> tags = new ArrayList<>();

            for (String rawEntry : entries) {
                String entry = rawEntry == null ? "" : rawEntry.trim();
                if (entry.isEmpty()) continue;

                boolean tag = entry.charAt(0) == '#';
                ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
                if (id == null) continue;

                if (tag) {
                    tags.add(TagKey.create(Registries.BLOCK, id));
                } else {
                    BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
                }
            }

            return new MatchSet(blocks, List.copyOf(tags));
        }

        boolean matches(BlockState state) {
            if (blocks.contains(state.getBlock())) return true;
            for (TagKey<Block> tag : tags) {
                if (state.is(tag)) return true;
            }
            return false;
        }
    }

    private record OriginalStateRegion(
            BlockAndTintGetter delegate,
            VisualSnowPipeline.Substitution substitution
    ) implements BlockAndTintGetter {
        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
            return delegate.getBlockTint(blockPos, colorResolver);
        }

        @Override
        public int getBrightness(LightLayer lightType, BlockPos blockPos) {
            return delegate.getBrightness(lightType, blockPos);
        }

        @Override
        public int getRawBrightness(BlockPos blockPos, int amount) {
            return delegate.getRawBrightness(blockPos, amount);
        }

        @Override
        public boolean canSeeSky(BlockPos blockPos) {
            return delegate.canSeeSky(blockPos);
        }

        @Override
        public ModelData getModelData(BlockPos pos) {
            return delegate.getModelData(pos);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState original = substitution.originalStateAt(pos);
            return original != null ? original : delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            BlockState original = substitution.originalStateAt(pos);
            return original != null ? original.getFluidState() : delegate.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }

}

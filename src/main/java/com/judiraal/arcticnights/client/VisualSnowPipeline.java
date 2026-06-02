package com.judiraal.arcticnights.client;

import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class VisualSnowPipeline {
    private static final BlockState SNOW_LAYER = Blocks.SNOW.defaultBlockState();
    private static final ThreadLocal<Substitution> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RESTORE_ORIGINALS = ThreadLocal.withInitial(() -> false);

    private VisualSnowPipeline() {
    }

    public static Scope pushFromRenderers(List<?> renderers) {
        for (Object renderer : renderers) {
            if (renderer instanceof Source source) {
                return push(source.arcticnights$visualSnowSubstitution());
            }
        }
        return Scope.NOOP;
    }

    public static BlockState blockStateOverride(BlockPos pos) {
        return blockStateOverride(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockState blockStateOverride(int x, int y, int z) {
        Substitution substitution = CURRENT.get();
        if (substitution == null || substitution.isEmpty()) return null;

        Entry entry = substitution.overlayEntriesByPos.get(BlockPos.asLong(x, y, z));
        if (entry == null) return null;
        if (RESTORE_ORIGINALS.get()) return entry.state();

        substitution.recordSubstitution(x, y, z);
        return SNOW_LAYER;
    }

    @Nullable
    public static Substitution currentSubstitution() {
        return CURRENT.get();
    }

    public static void withOriginalStates(Runnable action) {
        boolean previous = RESTORE_ORIGINALS.get();
        RESTORE_ORIGINALS.set(true);
        try {
            action.run();
        } finally {
            RESTORE_ORIGINALS.set(previous);
        }
    }

    private static Scope push(Substitution substitution) {
        if (substitution == null) return Scope.NOOP;

        Substitution previous = CURRENT.get();
        CURRENT.set(substitution);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public interface Scope extends AutoCloseable {
        Scope NOOP = () -> {
        };

        @Override
        void close();
    }

    public interface Source {
        Substitution arcticnights$visualSnowSubstitution();
    }

    public record Entry(BlockPos pos, BlockState state, long packedPos) {
        public Entry(BlockPos pos, BlockState state) {
            this(pos, state, pos.asLong());
        }

        public Entry {
            pos = pos.immutable();
        }
    }

    public static final class Substitution {
        private static final int SODIUM_SECTION_ARRAY_LENGTH = 3;
        private static final int SODIUM_SECTION_ARRAY_AREA =
                SODIUM_SECTION_ARRAY_LENGTH * SODIUM_SECTION_ARRAY_LENGTH;

        private final VisualSnowCoverRenderer.Matcher matcher;
        private final VisualSnowCoverRenderer.Rules rules;
        private final List<Entry> overlayEntries = new ArrayList<>();
        private final Long2ObjectMap<Entry> overlayEntriesByPos = new Long2ObjectOpenHashMap<>();
        private final List<Entry> renderEntries = new ArrayList<>();
        private final LongSet renderEntryPositions = new LongOpenHashSet();
        private final LongSet substitutedPositions = new LongOpenHashSet();

        public Substitution(VisualSnowCoverRenderer.Matcher matcher, VisualSnowCoverRenderer.Rules rules) {
            this.matcher = matcher;
            this.rules = rules;
        }

        public boolean isEmpty() {
            return overlayEntriesByPos.isEmpty() || renderEntries.isEmpty();
        }

        public List<Entry> renderEntries() {
            return renderEntries;
        }

        public boolean hasOverlay(BlockPos pos) {
            return overlayEntriesByPos.containsKey(pos.asLong());
        }

        @Nullable
        public BlockState originalStateAt(BlockPos pos) {
            Entry entry = overlayEntriesByPos.get(pos.asLong());
            return entry == null ? null : entry.state();
        }

        public boolean wasSubstituted(BlockPos pos) {
            return substitutedPositions.contains(pos.asLong());
        }

        public void beginSodiumBuild() {
            clearBuildEntries();
        }

        public BlockState substituteSodiumBlock(
                BlockAndTintGetter level,
                BlockState[][] blockArrays,
                int originBlockX,
                int originBlockY,
                int originBlockZ,
                int x,
                int y,
                int z,
                BlockState originalState
        ) {
            long packedPos = BlockPos.asLong(x, y, z);
            Entry existing = overlayEntriesByPos.get(packedPos);
            if (existing != null) {
                promoteRenderEntry(existing);
                recordSubstitution(packedPos);
                addSodiumContext(
                        new OriginalStateView(level, this),
                        blockArrays,
                        originBlockX,
                        originBlockY,
                        originBlockZ,
                        existing.pos()
                );
                return SNOW_LAYER;
            }

            if (!couldEverRenderVisualSnow(originalState)) {
                return originalState;
            }

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
            BlockAndTintGetter originalView = new OriginalStateView(level, this);
            if (!VisualSnowCoverRenderer.shouldRenderVisualSnow(originalView, pos, originalState, matcher, rules)) {
                return originalState;
            }

            Entry entry = addAndPatchSodiumEntry(
                    blockArrays,
                    originBlockX,
                    originBlockY,
                    originBlockZ,
                    pos,
                    originalState,
                    true
            );
            if (entry == null) {
                return originalState;
            }

            recordSubstitution(packedPos);
            addSodiumContext(originalView, blockArrays, originBlockX, originBlockY, originBlockZ, entry.pos());
            return SNOW_LAYER;
        }

        private void clearBuildEntries() {
            overlayEntries.clear();
            overlayEntriesByPos.clear();
            renderEntries.clear();
            renderEntryPositions.clear();
            substitutedPositions.clear();
        }

        private boolean couldEverRenderVisualSnow(BlockState state) {
            return !state.isAir()
                    && !state.is(Blocks.SNOW)
                    && !state.is(Blocks.SNOW_BLOCK)
                    && state.getFluidState().isEmpty()
                    && matcher.allows(state);
        }

        private void addSodiumContext(
                BlockAndTintGetter originalView,
                BlockState[][] blockArrays,
                int originBlockX,
                int originBlockY,
                int originBlockZ,
                BlockPos center
        ) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    pos.set(center.getX() + xOffset, center.getY(), center.getZ() + zOffset);
                    addContextEntry(originalView, blockArrays, originBlockX, originBlockY, originBlockZ, pos);
                }
            }
        }

        private void addContextEntry(
                BlockAndTintGetter originalView,
                BlockState[][] blockArrays,
                int originBlockX,
                int originBlockY,
                int originBlockZ,
                BlockPos.MutableBlockPos pos
        ) {
            if (overlayEntriesByPos.containsKey(pos.asLong())) return;

            BlockState state = originalStateAt(pos);
            if (state == null) {
                state = stateAt(blockArrays, originBlockX, originBlockY, originBlockZ, pos);
            }
            if (state == null || !couldEverRenderVisualSnow(state)) return;
            if (VisualSnowCoverRenderer.shouldRenderVisualSnow(originalView, pos, state, matcher, rules)) {
                addAndPatchSodiumEntry(blockArrays, originBlockX, originBlockY, originBlockZ, pos, state, false);
            }
        }

        @Nullable
        private Entry addAndPatchSodiumEntry(
                BlockState[][] blockArrays,
                int originBlockX,
                int originBlockY,
                int originBlockZ,
                BlockPos pos,
                BlockState state,
                boolean renderOriginal
        ) {
            long packedPos = pos.asLong();
            Entry entry = overlayEntriesByPos.get(packedPos);
            if (entry == null) {
                entry = new Entry(pos, state);
                if (!patchSodiumBlockArrayEntry(blockArrays, originBlockX, originBlockY, originBlockZ, entry)) {
                    return null;
                }
                overlayEntriesByPos.put(packedPos, entry);
                overlayEntries.add(entry);
            }

            if (renderOriginal) {
                promoteRenderEntry(entry);
            }
            return entry;
        }

        private void promoteRenderEntry(Entry entry) {
            if (renderEntryPositions.add(entry.packedPos())) {
                renderEntries.add(entry);
            }
        }

        @Nullable
        private static BlockState stateAt(
                BlockState[][] blockArrays,
                int originBlockX,
                int originBlockY,
                int originBlockZ,
                BlockPos pos
        ) {
            int relativeX = pos.getX() - originBlockX;
            int relativeY = pos.getY() - originBlockY;
            int relativeZ = pos.getZ() - originBlockZ;

            int sectionX = relativeX >> 4;
            int sectionY = relativeY >> 4;
            int sectionZ = relativeZ >> 4;
            if (sectionX < 0 || sectionX >= SODIUM_SECTION_ARRAY_LENGTH
                    || sectionY < 0 || sectionY >= SODIUM_SECTION_ARRAY_LENGTH
                    || sectionZ < 0 || sectionZ >= SODIUM_SECTION_ARRAY_LENGTH) {
                return null;
            }

            int sectionIndex = sodiumSectionIndex(sectionX, sectionY, sectionZ);
            if (sectionIndex < 0 || sectionIndex >= blockArrays.length) return null;

            BlockState[] states = blockArrays[sectionIndex];
            if (states == null) return null;

            return states[localBlockIndex(relativeX & 15, relativeY & 15, relativeZ & 15)];
        }

        private void recordSubstitution(long packedPos) {
            substitutedPositions.add(packedPos);
        }

        private void recordSubstitution(int x, int y, int z) {
            recordSubstitution(BlockPos.asLong(x, y, z));
        }

        private static boolean patchSodiumBlockArrayEntry(
                BlockState[][] blockArrays,
                int originBlockX,
                int originBlockY,
                int originBlockZ,
                Entry entry
        ) {
            BlockPos pos = entry.pos();
            int relativeX = pos.getX() - originBlockX;
            int relativeY = pos.getY() - originBlockY;
            int relativeZ = pos.getZ() - originBlockZ;

            int sectionX = relativeX >> 4;
            int sectionY = relativeY >> 4;
            int sectionZ = relativeZ >> 4;
            if (sectionX < 0 || sectionX >= SODIUM_SECTION_ARRAY_LENGTH
                    || sectionY < 0 || sectionY >= SODIUM_SECTION_ARRAY_LENGTH
                    || sectionZ < 0 || sectionZ >= SODIUM_SECTION_ARRAY_LENGTH) {
                return false;
            }

            int sectionIndex = sodiumSectionIndex(sectionX, sectionY, sectionZ);
            if (sectionIndex < 0 || sectionIndex >= blockArrays.length) return false;

            BlockState[] states = blockArrays[sectionIndex];
            if (states == null) return false;

            int blockIndex = localBlockIndex(relativeX & 15, relativeY & 15, relativeZ & 15);
            BlockState currentState = states[blockIndex];
            if (currentState != entry.state() && !currentState.equals(entry.state())) return false;

            states[blockIndex] = SNOW_LAYER;
            return true;
        }

        private static int sodiumSectionIndex(int x, int y, int z) {
            return y * SODIUM_SECTION_ARRAY_AREA + z * SODIUM_SECTION_ARRAY_LENGTH + x;
        }

        private static int localBlockIndex(int x, int y, int z) {
            return (y << 8) | (z << 4) | x;
        }
    }

    private record OriginalStateView(
            BlockAndTintGetter delegate,
            Substitution substitution
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

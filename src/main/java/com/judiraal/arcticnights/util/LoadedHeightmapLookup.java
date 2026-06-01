package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public final class LoadedHeightmapLookup {
    public static final int UNLOADED_HEIGHT = -255;

    private LoadedHeightmapLookup() {
    }

    public static BlockPos heightmapPosNoWait(Level level, Heightmap.Types heightmapType, BlockPos pos) {
        return new BlockPos(pos.getX(), heightNoWait(level, heightmapType, pos.getX(), pos.getZ()), pos.getZ());
    }

    public static int heightNoWait(Level level, Heightmap.Types heightmapType, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z)
        );
        return chunk != null ? chunk.getHeight(heightmapType, x & 15, z & 15) + 1 : UNLOADED_HEIGHT;
    }
}

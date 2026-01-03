package com.judiraal.arcticnights.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import weather2.config.ConfigTornado;
import weather2.util.WeatherUtil;
import weather2.weathersystem.storm.StormObject;

import java.util.concurrent.ConcurrentHashMap;

public final class TornadoHandler {
    private TornadoHandler() {}

    private static final BlockPos ZERO = BlockPos.ZERO;
    private static final float MAX_HARDNESS = 5.0F;
    private static final ItemStack DIAMOND_AXE = new ItemStack(Items.DIAMOND_AXE);
    private static final ConcurrentHashMap<Block, Float> AXE_SPEED = new ConcurrentHashMap<>();

    public static boolean isProbablyImportant(Level level, BlockState state, Block block, int intensity) {
        // Unbreakable / special stuff: treat as important
        float hardness = state.getDestroySpeed(level, ZERO);
        if (hardness < 0) return true;

        // Other rules for important blocks fire with decreased probability as storm intensity increases
        if (level.random.nextFloat() < 0.01F * intensity) return false;

        // Most functional blocks/machines/controllers/inventories are block entities
        if (state.hasBlockEntity()) return true;

        // If it's too hard, it's likely "structure" rather than loose debris
        if (hardness >= MAX_HARDNESS) return true;

        return false;
    }

    public static boolean isDebrisCandidate(BlockState state, Block block) {
        // Anything replaceable is basically “loose”: plants, snow layers, etc.
        if (state.canBeReplaced()) return true;

        // Leaves + vegetation
        if (block instanceof LeavesBlock) return true;
        if (block instanceof TallGrassBlock) return true;

        // Vanilla tags (stable, not “manual per mod” tagging)
        if (state.is(BlockTags.LEAVES)) return true;
        if (state.is(BlockTags.FLOWERS)) return true;
        if (state.is(BlockTags.SAPLINGS)) return true;
        if (state.is(BlockTags.TALL_FLOWERS)) return true;

        return false;
    }

    private static float grabStrength(Level level, BlockState state) {
        float destroySpeed = state.getDestroySpeed(level, net.minecraft.core.BlockPos.ZERO);
        float axeSpeed = AXE_SPEED.computeIfAbsent(state.getBlock(), b -> DIAMOND_AXE.getDestroySpeed(b.defaultBlockState()));
        return destroySpeed - (axeSpeed - 1.0F) / 4.0F;
    }

    private static final float STRENGTH_MIN = 0.0F;
    private static final float STRENGTH_MAX = 0.74F;

    public static boolean shouldGrabBlock(Level level, BlockState state, BlockPos pos, StormObject storm) {
        Block block = state.getBlock();
        if (isProbablyImportant(level, state, block, storm.levelCurIntensityStage)) return false;
        if (isDebrisCandidate(state, block)) return true;

        float strVsBlock = grabStrength(level, state);
        boolean inRange = strVsBlock <= STRENGTH_MAX && strVsBlock >= STRENGTH_MIN;

        boolean woodish =
                block.defaultMapColor() == MapColor.WOOD ||
                        block.defaultMapColor() == MapColor.WOOL ||
                        block.defaultMapColor() == MapColor.PLANT;

        boolean result = inRange || woodish;

        if (result && ConfigTornado.Storm_Tornado_RefinedGrabRules) {
            if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT ||
                    block == Blocks.SAND || block == Blocks.RED_SAND) {
                return false;
            }
            if (storm.levelCurIntensityStage < StormObject.STATE_STAGE4 && block == Blocks.GRASS_BLOCK) {
                return false;
            }
            if (storm.levelCurIntensityStage < StormObject.STATE_STAGE5 && block == Blocks.DIRT_PATH) {
                return false;
            }
            if (!WeatherUtil.canTornadoGrabBlockRefinedRules(state)) {
                return false;
            }
        }

        if (!result && storm.levelCurIntensityStage >= 3 && pos.getY() > 0) {
            var mpos = pos.mutable();
            mpos.setWithOffset(pos, Direction.UP);
            strVsBlock += grabStrength(level, level.getBlockState(mpos))*2;
            mpos.setWithOffset(pos, Direction.DOWN);
            strVsBlock += grabStrength(level, level.getBlockState(mpos))*3;
            mpos.setWithOffset(pos, Direction.EAST);
            strVsBlock += grabStrength(level, level.getBlockState(mpos));
            mpos.setWithOffset(pos, Direction.WEST);
            strVsBlock += grabStrength(level, level.getBlockState(mpos));
            mpos.setWithOffset(pos, Direction.NORTH);
            strVsBlock += grabStrength(level, level.getBlockState(mpos));
            mpos.setWithOffset(pos, Direction.SOUTH);
            strVsBlock += grabStrength(level, level.getBlockState(mpos));

            if (strVsBlock <= storm.levelCurIntensityStage * level.random.nextFloat() * 1.5F && strVsBlock >= STRENGTH_MIN) return true;
        }

        return result;
    }
}

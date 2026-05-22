package com.judiraal.arcticnights.compat.cold_sweat;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.temperature.block_temp.ConfiguredBlockTemp;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.momosoftworks.coldsweat.data.codec.configuration.BlockTempData;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.function.Function;

public class ArcticBlockTempModifier extends TempModifier {
    private static final double LOG_INVERSE_EXPONENT = 1.923076923076923D;
    private static final double LOG_EXPONENT = 0.52D;

    private final Object2DoubleOpenHashMap<BlockTemp> blockTempTotals = new Object2DoubleOpenHashMap<>(16);
    private final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos rayPos = new BlockPos.MutableBlockPos();

    public ArcticBlockTempModifier() {
        this.blockTempTotals.defaultReturnValue(0.0D);
    }

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        this.blockTempTotals.clear();
        if (trait != Temperature.Trait.WORLD) {
            return temperature -> temperature;
        }

        Level level = entity.level();
        int range = ConfigSettings.BLOCK_RANGE.get();
        BlockPos entityPos = entity.blockPosition();
        int centerX = entityPos.getX();
        int centerY = entityPos.getY();
        int centerZ = entityPos.getZ();

        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        ChunkAccess chunk = null;

        for (int offsetX = -range; offsetX < range; offsetX++) {
            int blockX = centerX + offsetX;
            int chunkX = blockX >> 4;
            for (int offsetZ = -range; offsetZ < range; offsetZ++) {
                int blockZ = centerZ + offsetZ;
                int chunkZ = blockZ >> 4;
                if (chunk == null || chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    chunk = WorldHelper.getChunk(level, chunkX, chunkZ);
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                }
                if (chunk == null) continue;

                for (int offsetY = -range; offsetY < range; offsetY++) {
                    int blockY = centerY + offsetY;
                    this.scanPos.set(blockX, blockY, blockZ);

                    BlockState state = getBlockState(chunk, blockX, blockY, blockZ);
                    if (state.isAir()) continue;

                    Collection<BlockTemp> blockTemps = BlockTempRegistry.getBlockTempsFor(state);
                    if (isOnlyDefault(blockTemps) || !this.hasBlockTempInRange(blockTemps)) continue;

                    double blockCenterX = blockX + 0.5D;
                    double blockCenterY = blockY + 0.5D;
                    double blockCenterZ = blockZ + 0.5D;
                    AABB bounds = entity.getBoundingBox();
                    double closestX = clamp(blockCenterX, bounds.minX, bounds.maxX);
                    double closestY = clamp(blockCenterY, bounds.minY, bounds.maxY);
                    double closestZ = clamp(blockCenterZ, bounds.minZ, bounds.maxZ);
                    double rayX = blockCenterX - closestX;
                    double rayY = blockCenterY - closestY;
                    double rayZ = blockCenterZ - closestZ;
                    double distance = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
                    Direction direction = Direction.getNearest(rayX, rayY, rayZ);
                    int blocked = countSpreadBlocked(level, chunk, closestX, closestY, closestZ, blockCenterX, blockCenterY, blockCenterZ, direction);

                    for (BlockTemp blockTemp : blockTemps) {
                        if (!blockTemp.isValid(level, this.scanPos, state)) continue;

                        double temperature = blockTemp.getTemperature(level, entity, state, this.scanPos, distance);
                        if (temperature == 0.0D) continue;

                        if (blockTemp.fade()) {
                            temperature = blend(temperature, 0.0D, distance, 0.5D, blockTemp.range());
                        }

                        this.addBlockTemperature(blockTemp, temperature, blocked);
                    }
                }
            }
        }

        return this::applyBlockTemps;
    }

    private static BlockState getBlockState(ChunkAccess chunk, int x, int y, int z) {
        LevelChunkSection section = WorldHelper.getChunkSection(chunk, y);
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    private static boolean isOnlyDefault(Collection<BlockTemp> blockTemps) {
        return blockTemps.isEmpty()
                || (blockTemps.size() == 1 && blockTemps.contains(BlockTempRegistry.DEFAULT_BLOCK_TEMP));
    }

    private boolean hasBlockTempInRange(Collection<BlockTemp> blockTemps) {
        for (BlockTemp blockTemp : blockTemps) {
            if (!this.blockTempTotals.containsKey(blockTemp)) return true;
            double groupTotal = this.getGroupTotal(blockTemp);
            if (betweenInclusive(groupTotal, blockTemp.minEffect(), blockTemp.maxEffect())) return true;
        }
        return false;
    }

    private void addBlockTemperature(BlockTemp blockTemp, double temperature, int blocked) {
        double currentTotal = this.blockTempTotals.getDouble(blockTemp);
        double groupTotal = this.getGroupTotal(blockTemp);
        double otherGroupTotal = groupTotal - currentTotal;

        double newTotal;
        if (blockTemp.logarithmic()) {
            double delta = Math.pow(Math.pow(currentTotal, LOG_INVERSE_EXPONENT) + temperature, LOG_EXPONENT) - currentTotal;
            newTotal = currentTotal + delta / (blocked + 1.0D);
        } else {
            newTotal = currentTotal + temperature / (blocked + 1.0D);
        }

        this.blockTempTotals.put(
                blockTemp,
                clamp(newTotal, blockTemp.minEffect() + otherGroupTotal, blockTemp.maxEffect() - otherGroupTotal)
        );
    }

    private double getGroupTotal(BlockTemp blockTemp) {
        TagKey<BlockTempData> group = null;
        if (blockTemp instanceof ConfiguredBlockTemp configuredBlockTemp) {
            group = configuredBlockTemp.getData().effectGroup().orElse(null);
        }
        if (group == null) {
            return this.blockTempTotals.getDouble(blockTemp);
        }

        double total = 0.0D;
        for (Object2DoubleMap.Entry<BlockTemp> entry : this.blockTempTotals.object2DoubleEntrySet()) {
            if (entry.getKey() instanceof ConfiguredBlockTemp configuredBlockTemp && configuredBlockTemp.isInGroup(group)) {
                total += entry.getDoubleValue();
            }
        }
        return total;
    }

    private double applyBlockTemps(double temperature) {
        double result = temperature;
        for (Object2DoubleMap.Entry<BlockTemp> entry : this.blockTempTotals.object2DoubleEntrySet()) {
            BlockTemp blockTemp = entry.getKey();
            double min = blockTemp.minTemperature();
            double max = blockTemp.maxTemperature();
            if (betweenInclusive(result, min, max)) {
                result = clamp(result + entry.getDoubleValue(), min, max);
            }
        }
        return result;
    }

    private int countSpreadBlocked(
            Level level,
            ChunkAccess initialChunk,
            double fromX,
            double fromY,
            double fromZ,
            double toX,
            double toY,
            double toZ,
            Direction direction
    ) {
        double rayX = toX - fromX;
        double rayY = toY - fromY;
        double rayZ = toZ - fromZ;
        double length = Math.sqrt(rayX * rayX + rayY * rayY + rayZ * rayZ);
        if (length == 0.0D) return 0;

        double normalX = rayX / length;
        double normalY = rayY / length;
        double normalZ = rayZ / length;
        int previousX = floor(fromX);
        int previousY = floor(fromY);
        int previousZ = floor(fromZ);
        int remainingHits = 3;
        int blocked = 0;
        ChunkAccess workingChunk = initialChunk;
        ChunkPos chunkPos = workingChunk == null ? null : workingChunk.getPos();
        int workingChunkX = chunkPos == null ? Integer.MIN_VALUE : chunkPos.x;
        int workingChunkZ = chunkPos == null ? Integer.MIN_VALUE : chunkPos.z;

        for (int step = 0; step < length; step++) {
            int x = floor(fromX + normalX * step);
            int y = floor(fromY + normalY * step);
            int z = floor(fromZ + normalZ * step);
            if (x == previousX && y == previousY && z == previousZ) continue;
            previousX = x;
            previousY = y;
            previousZ = z;
            this.rayPos.set(x, y, z);

            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (workingChunk == null || chunkX != workingChunkX || chunkZ != workingChunkZ) {
                workingChunk = WorldHelper.getChunk(level, chunkX, chunkZ);
                workingChunkX = chunkX;
                workingChunkZ = chunkZ;
            }
            if (workingChunk == null) continue;

            BlockState state = workingChunk.getBlockState(this.rayPos);
            if (!state.isAir() && --remainingHits <= 0) break;
            if (WorldHelper.isSpreadBlocked(level, state, this.rayPos, direction, direction)) {
                blocked++;
            }
        }

        return blocked;
    }

    private static double blend(double from, double to, double value, double min, double max) {
        if (min > max) return blend(to, from, value, max, min);
        if (value <= min) return from;
        if (value >= max) return to;
        return (to - from) / (max - min) * (value - min) + from;
    }

    private static boolean betweenInclusive(double value, double min, double max) {
        if (min > max) {
            double temp = min;
            min = max;
            max = temp;
        }
        return value >= min && value <= max;
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}

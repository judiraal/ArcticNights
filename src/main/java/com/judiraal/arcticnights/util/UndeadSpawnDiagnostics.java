package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class UndeadSpawnDiagnostics {
    private static final long REPORT_INTERVAL_TICKS = 20L * 30L;
    private static final long FINALIZE_JOIN_TTL_TICKS = 20L * 10L;
    private static final int MAX_POTENTIAL_GROUPS_PER_REPORT = 24;
    private static final int MAX_LIFECYCLE_GROUPS_PER_REPORT = 48;
    private static final Map<Key, Stats> POTENTIAL_STATS = new HashMap<>();
    private static final Map<Key, Stats> LIFECYCLE_STATS = new HashMap<>();
    private static final Map<UUID, Long> RECENT_NATURAL_FINALIZES = new HashMap<>();
    private static long lastReportGameTime = Long.MIN_VALUE;
    private static int potentialTotal;
    private static int lifecycleTotal;

    private UndeadSpawnDiagnostics() {
    }

    public static void recordPotential(ServerLevel level, BlockPos pos, EntityType<?> entityType, float factor, String result) {
        if (!shouldRecord(level, pos, entityType)) return;

        add(POTENTIAL_STATS, level, pos, entityType, factor, result);
        potentialTotal++;
        reportIfDue(level);
    }

    public static void recordLifecycle(ServerLevel level, BlockPos pos, EntityType<?> entityType, String result) {
        if (!shouldRecord(level, pos, entityType)) return;

        add(LIFECYCLE_STATS, level, pos, entityType, ArcticSpawner.spawnFactor(entityType, pos, level), result);
        lifecycleTotal++;
        reportIfDue(level);
    }

    public static void recordFinalize(ServerLevel level, Entity entity, String result, boolean rememberJoin) {
        BlockPos pos = entity.blockPosition();
        if (!shouldRecord(level, pos, entity.getType())) return;

        add(LIFECYCLE_STATS, level, pos, entity.getType(), ArcticSpawner.spawnFactor(entity.getType(), pos, level), result);
        lifecycleTotal++;
        if (rememberJoin) {
            cleanupRecentFinalizes(level.getGameTime());
            RECENT_NATURAL_FINALIZES.put(entity.getUUID(), level.getGameTime());
        }
        reportIfDue(level);
    }

    public static void recordJoin(ServerLevel level, Entity entity, boolean canceled) {
        cleanupRecentFinalizes(level.getGameTime());
        Long finalizedAt = RECENT_NATURAL_FINALIZES.remove(entity.getUUID());
        if (finalizedAt == null || level.getGameTime() - finalizedAt > FINALIZE_JOIN_TTL_TICKS) return;

        recordLifecycle(level, entity.blockPosition(), entity.getType(), canceled ? "join_canceled" : "join");
    }

    private static boolean shouldRecord(ServerLevel level, BlockPos pos, EntityType<?> entityType) {
        return ArcticNightsFeatures.spawnDiagnostics()
                && ArcticNights.LOGGER.isDebugEnabled()
                && entityType.is(SpawnEcology.REQUIRE_COLD)
                && ClimateService.isExposedToSky(level, pos);
    }

    private static void add(Map<Key, Stats> stats, ServerLevel level, BlockPos pos, EntityType<?> entityType, float factor, String result) {
        BlockState support = level.getBlockState(pos.below());
        String entityId = EntityType.getKey(entityType).toString();
        String supportId = supportId(support);
        stats.computeIfAbsent(new Key(entityId, supportId, result), key -> new Stats()).add(factor);
    }

    private static void reportIfDue(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (lastReportGameTime == Long.MIN_VALUE) {
            lastReportGameTime = gameTime;
            return;
        }
        if (gameTime - lastReportGameTime < REPORT_INTERVAL_TICKS) return;

        long windowTicks = gameTime - lastReportGameTime;
        reportStats("potentials", windowTicks, potentialTotal, POTENTIAL_STATS);
        reportStats("lifecycle", windowTicks, lifecycleTotal, LIFECYCLE_STATS);
        cleanupRecentFinalizes(gameTime);
        lastReportGameTime = gameTime;
    }

    private static void reportStats(String label, long windowTicks, int total, Map<Key, Stats> stats) {
        if (stats.isEmpty()) return;

        int maxGroups = "lifecycle".equals(label) ? MAX_LIFECYCLE_GROUPS_PER_REPORT : MAX_POTENTIAL_GROUPS_PER_REPORT;
        var groups = stats.entrySet().stream()
                .sorted(comparatorFor(label))
                .limit(maxGroups)
                .toList();
        int omitted = Math.max(0, stats.size() - maxGroups);
        ArcticNights.LOGGER.debug("ANSPAWN undead surface {} windowTicks={} total={} reportedGroups={} omittedGroups={}",
                label,
                windowTicks,
                total,
                groups.size(),
                omitted);
        for (Map.Entry<Key, Stats> entry : groups) {
            ArcticNights.LOGGER.debug(
                    "ANSPAWN undead surface {} group {}",
                    label,
                    entry.getKey().format(entry.getValue())
            );
        }
        stats.clear();
        if ("potentials".equals(label)) {
            potentialTotal = 0;
        } else {
            lifecycleTotal = 0;
        }
    }

    private static Comparator<Map.Entry<Key, Stats>> comparatorFor(String label) {
        Comparator<Map.Entry<Key, Stats>> countComparator = Comparator
                .<Map.Entry<Key, Stats>>comparingInt(entry -> entry.getValue().count)
                .reversed()
                .thenComparing(entry -> entry.getKey().entityId)
                .thenComparing(entry -> entry.getKey().supportId)
                .thenComparing(entry -> entry.getKey().result);
        if (!"lifecycle".equals(label)) return countComparator;

        return Comparator
                .<Map.Entry<Key, Stats>>comparingInt(entry -> lifecyclePriority(entry.getKey().result))
                .thenComparingInt(entry -> supportPriority(entry.getKey().supportId))
                .thenComparing(countComparator);
    }

    private static int lifecyclePriority(String result) {
        return switch (result) {
            case "join" -> 0;
            case "finalize" -> 1;
            case "position_pass" -> 2;
            case "placement_pass" -> 3;
            case "join_canceled", "finalize_canceled" -> 4;
            case "position_forced_pass", "placement_forced_pass" -> 5;
            case "position_fail_event", "placement_fail_event" -> 10;
            case "position_fail_rules", "position_fail_obstruction", "placement_fail_default" -> 11;
            default -> 20;
        };
    }

    private static int supportPriority(String supportId) {
        if (supportId.startsWith("minecraft:snow[")) return 0;
        if ("minecraft:snow_block".equals(supportId)) return 1;
        if (supportId.contains("ice")) return 2;
        return 3;
    }

    private static String supportId(BlockState support) {
        String id = BuiltInRegistries.BLOCK.getKey(support.getBlock()).toString();
        if (support.is(Blocks.SNOW) && support.hasProperty(SnowLayerBlock.LAYERS)) {
            return id + "[layers=" + support.getValue(SnowLayerBlock.LAYERS) + "]";
        }
        return id;
    }

    private static void cleanupRecentFinalizes(long gameTime) {
        RECENT_NATURAL_FINALIZES.entrySet().removeIf(entry -> gameTime - entry.getValue() > FINALIZE_JOIN_TTL_TICKS);
    }

    private record Key(String entityId, String supportId, String result) {
        String format(Stats stats) {
            return entityId
                    + " support=" + supportId
                    + " result=" + result
                    + " count=" + stats.count
                    + " factorAvg=" + f(stats.sum / stats.count)
                    + " factorMin=" + f(stats.min)
                    + " factorMax=" + f(stats.max);
        }
    }

    private static final class Stats {
        int count;
        float sum;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;

        void add(float factor) {
            count++;
            sum += factor;
            min = Math.min(min, factor);
            max = Math.max(max, factor);
        }
    }

    private static String f(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}

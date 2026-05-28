package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ClimatePlayerHarness {
    private static final String PLAYER_NAME = "[ANHarness]";
    private static final int DEFAULT_RAIN_TICKS = 240_000;
    private static final int MID_WINTER_DAY = 80;
    private static final int NOON_TICK = 6_000;
    private static final double DEFAULT_WALK_BLOCKS_PER_SECOND = 5.6D;
    private static ActiveHarness active;

    private ClimatePlayerHarness() {
    }

    public static StartResult start(ServerLevel level, BlockPos origin, Direction direction, int travelChunks, int waitSeconds) {
        stop("replaced");
        configureWinterStorm(level);

        double originX = origin.getX() + 0.5D;
        double originZ = origin.getZ() + 0.5D;
        double targetX = originX + direction.xStep * travelChunks * 16.0D;
        double targetZ = originZ + direction.zStep * travelChunks * 16.0D;
        ActiveHarness harness = new ActiveHarness(
                level,
                originX,
                surfaceY(level, originX, originZ),
                originZ,
                targetX,
                surfaceY(level, targetX, targetZ),
                targetZ,
                direction,
                travelChunks,
                secondsToTicks(waitSeconds),
                DEFAULT_WALK_BLOCKS_PER_SECOND / 20.0D
        );
        active = harness;
        harness.start();
        ArcticNights.LOGGER.info("Climate player harness started {}", harness.status().summary());
        return new StartResult(harness.status().summary());
    }

    public static Status status() {
        return active == null ? Status.inactive() : active.status();
    }

    public static boolean isActivePlayerCloseEnough(ServerLevel level, ChunkPos chunkPos, double maxDistanceSqr) {
        ActiveHarness harness = active;
        return harness != null && harness.isPlayerCloseEnough(level, chunkPos, maxDistanceSqr);
    }

    public static Status stop(String reason) {
        if (active == null) return Status.inactive();
        ActiveHarness harness = active;
        active = null;
        harness.stop(reason);
        ArcticNights.LOGGER.info("Climate player harness stopped reason={} {}", reason, harness.status().summary());
        return harness.status();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (active == null) return;
        if (!event.getServer().isSameThread()) return;
        active.tick();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        stop("server_stopped");
    }

    private static void configureWinterStorm(ServerLevel level) {
        level.setDayTime(MID_WINTER_DAY * 24_000L + NOON_TICK);
        if (ArcticNights.SERENE_SEASONS) {
            level.getServer().getCommands().performPrefixedCommand(
                    level.getServer().createCommandSourceStack()
                            .withLevel(level)
                            .withPermission(4)
                            .withSuppressedOutput(),
                    "season set mid_winter"
            );
        }
        level.setWeatherParameters(0, DEFAULT_RAIN_TICKS, true, false);
        level.setRainLevel(1.0F);
        level.setThunderLevel(0.0F);
        ClimateService.setDebugWeatherOffsetCelsius(level.getServer(), -20.0D);
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }

    private static double surfaceY(ServerLevel level, double x, double z) {
        int blockX = (int)Math.floor(x);
        int blockZ = (int)Math.floor(z);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) + 1.0D;
    }

    private static String chunkReport(ServerLevel level, ChunkPos pos) {
        ClimateSnowDiagnostics.ProfileResult profile = ClimateSnowDiagnostics.profile(level, pos);
        ClimateTickrateCatchup.InspectResult inspect = ClimateTickrateCatchup.inspect(level, pos);
        String stamp = inspect.stamp()
                .map(value -> "lastAppliedGame:" + value.lastAppliedGameTime()
                        + "|debt:" + Math.max(0L, level.getGameTime() - value.lastAppliedGameTime())
                        + "|burstTicks:" + value.burstTicks())
                .orElse("stamp:none");
        String last = inspect.lastResult()
                .map(value -> "last:" + value.status()
                        + "|source:" + value.source()
                        + "|multiplier:" + value.multiplier()
                        + "|effectiveRTS:" + value.effectiveRandomTickSpeed())
                .orElse("last:none");
        return pos.x + "," + pos.z
                + "|loaded:" + profile.loaded()
                + "|snow:" + profile.snowPresent()
                + "|layers:" + profile.snowLayerTotal()
                + "|factor:" + decimal(profile.averageSnowFactor())
                + "|" + stamp
                + "|" + last;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private enum Stage {
        ORIGIN_WAIT("origin_wait"),
        MOVE_OUT("move_out"),
        FAR_WAIT("far_wait"),
        MOVE_BACK("move_back"),
        COMPLETE("complete"),
        STOPPED("stopped");

        private final String key;

        Stage(String key) {
            this.key = key;
        }
    }

    public enum Direction {
        EAST("east", 1, 0),
        WEST("west", -1, 0),
        SOUTH("south", 0, 1),
        NORTH("north", 0, -1);

        private final String key;
        private final int xStep;
        private final int zStep;

        Direction(String key, int xStep, int zStep) {
            this.key = key;
            this.xStep = xStep;
            this.zStep = zStep;
        }

        public static Optional<Direction> parse(String value) {
            for (Direction direction : values()) {
                if (direction.key.equalsIgnoreCase(value)) return Optional.of(direction);
            }
            return Optional.empty();
        }

        public String key() {
            return key;
        }
    }

    private static final class ActiveHarness {
        private final ServerLevel level;
        private final FakePlayer player;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final double targetX;
        private final double targetY;
        private final double targetZ;
        private final Direction direction;
        private final int travelChunks;
        private final int waitTicks;
        private final double blocksPerTick;
        private final long startGameTime;
        private Stage stage = Stage.ORIGIN_WAIT;
        private int stageTicks;
        private boolean chunkTracked;

        private ActiveHarness(ServerLevel level, double originX, double originY, double originZ, double targetX, double targetY, double targetZ, Direction direction, int travelChunks, int waitTicks, double blocksPerTick) {
            this.level = level;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.direction = direction;
            this.travelChunks = travelChunks;
            this.waitTicks = waitTicks;
            this.blocksPerTick = blocksPerTick;
            this.startGameTime = level.getGameTime();
            this.player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), PLAYER_NAME));
        }

        private void start() {
            player.moveTo(originX, originY, originZ, 0.0F, 0.0F);
            level.getChunkSource().addEntity(player);
            chunkTracked = true;
            level.getChunkSource().move(player);
            logStage("start");
        }

        private void tick() {
            if (stage == Stage.STOPPED || stage == Stage.COMPLETE) {
                stageTicks++;
                return;
            }

            stageTicks++;
            switch (stage) {
                case ORIGIN_WAIT -> {
                    moveTo(originX, originY, originZ);
                    if (stageTicks >= waitTicks) advance(Stage.MOVE_OUT);
                }
                case MOVE_OUT -> {
                    if (moveBetween(originX, originY, originZ, targetX, targetY, targetZ)) advance(Stage.FAR_WAIT);
                }
                case FAR_WAIT -> {
                    moveTo(targetX, targetY, targetZ);
                    if (stageTicks >= waitTicks) advance(Stage.MOVE_BACK);
                }
                case MOVE_BACK -> {
                    if (moveBetween(targetX, targetY, targetZ, originX, originY, originZ)) advance(Stage.COMPLETE);
                }
                case COMPLETE, STOPPED -> {
                }
            }
        }

        private void advance(Stage next) {
            logStage("leaving_" + stage.key);
            stage = next;
            stageTicks = 0;
            if (stage == Stage.COMPLETE) {
                moveTo(originX, originY, originZ);
                logStage("complete");
            } else {
                logStage("entered_" + stage.key);
            }
        }

        private boolean moveBetween(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
            double distance = Math.hypot(toX - fromX, toZ - fromZ);
            double progress = Math.min(1.0D, (stageTicks * blocksPerTick) / Math.max(1.0D, distance));
            double x = fromX + (toX - fromX) * progress;
            double y = fromY + (toY - fromY) * progress;
            double z = fromZ + (toZ - fromZ) * progress;
            moveTo(x, y, z);
            return progress >= 1.0D;
        }

        private void moveTo(double x, double y, double z) {
            player.moveTo(x, y, z, 0.0F, 0.0F);
            level.getChunkSource().move(player);
        }

        private void stop(String reason) {
            stage = Stage.STOPPED;
            if (chunkTracked) {
                level.getChunkSource().removeEntity(player);
                chunkTracked = false;
            }
            player.remove(Entity.RemovalReason.DISCARDED);
            logStage("stopped_" + reason);
        }

        private boolean isPlayerCloseEnough(ServerLevel level, ChunkPos chunkPos, double maxDistanceSqr) {
            if (this.level != level || !chunkTracked) return false;
            double dx = player.getX() - chunkPos.getMiddleBlockX();
            double dz = player.getZ() - chunkPos.getMiddleBlockZ();
            return dx * dx + dz * dz <= maxDistanceSqr;
        }

        private void logStage(String event) {
            ArcticNights.LOGGER.info("Climate player harness event={} {}", event, status().summary());
        }

        private Status status() {
            ChunkPos currentChunk = player.chunkPosition();
            ChunkPos originChunk = new ChunkPos(BlockPos.containing(originX, originY, originZ));
            ChunkPos targetChunk = new ChunkPos(BlockPos.containing(targetX, targetY, targetZ));
            int remainingTicks = switch (stage) {
                case ORIGIN_WAIT, FAR_WAIT -> Math.max(0, waitTicks - stageTicks);
                case MOVE_OUT, MOVE_BACK -> {
                    double distance = Math.hypot(targetX - originX, targetZ - originZ);
                    int moveTicks = (int)Math.ceil(distance / Math.max(0.001D, blocksPerTick));
                    yield Math.max(0, moveTicks - stageTicks);
                }
                case COMPLETE, STOPPED -> 0;
            };
            String summary = "stage=" + stage.key
                    + ", ticksInStage=" + stageTicks
                    + ", remainingTicks=" + remainingTicks
                    + ", elapsedTicks=" + Math.max(0L, level.getGameTime() - startGameTime)
                    + ", playerPresent=" + chunkTracked
                    + ", playerChunk=" + currentChunk.x + "," + currentChunk.z
                    + ", originChunk=" + originChunk.x + "," + originChunk.z
                    + ", targetChunk=" + targetChunk.x + "," + targetChunk.z
                    + ", direction=" + direction.key
                    + ", travelChunks=" + travelChunks
                    + ", waitTicks=" + waitTicks
                    + ", speedBlocksPerSecond=" + decimal(blocksPerTick * 20.0D)
                    + ", randomTickSpeed=" + level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING)
                    + ", raining=" + level.isRaining()
                    + ", loadedChunks=" + level.getChunkSource().getLoadedChunksCount()
                    + ", current=" + chunkReport(level, currentChunk)
                    + ", origin=" + chunkReport(level, originChunk)
                    + ", target=" + chunkReport(level, targetChunk);
            return new Status(true, stage.key, summary);
        }
    }

    public record StartResult(String summary) {
    }

    public record Status(boolean active, String stage, String summary) {
        private static Status inactive() {
            return new Status(false, "inactive", "inactive");
        }
    }
}

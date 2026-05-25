package com.judiraal.arcticnights.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ClimateCatchupState extends SavedData {
    private static final String DATA_NAME = "arcticnights_climate_catchup";
    private static final String CHUNKS_KEY = "chunks";

    private final Map<Long, ChunkStamp> stamps = new HashMap<>();

    public static ClimateCatchupState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ClimateCatchupState::new, ClimateCatchupState::load), DATA_NAME);
    }

    private static ClimateCatchupState load(CompoundTag tag, HolderLookup.Provider provider) {
        ClimateCatchupState state = new ClimateCatchupState();
        ListTag chunks = tag.getList(CHUNKS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunkTag = chunks.getCompound(i);
            int x = chunkTag.getInt("x");
            int z = chunkTag.getInt("z");
            ChunkStamp stamp = new ChunkStamp(
                    x,
                    z,
                    chunkTag.getLong("day_time"),
                    chunkTag.getLong("game_time")
            );
            state.stamps.put(ChunkPos.asLong(x, z), stamp);
        }
        return state;
    }

    public Optional<ChunkStamp> stamp(ChunkPos pos) {
        return Optional.ofNullable(stamps.get(pos.toLong()));
    }

    public void mark(ChunkPos pos, long dayTime, long gameTime) {
        stamps.put(pos.toLong(), new ChunkStamp(pos.x, pos.z, dayTime, gameTime));
        setDirty();
    }

    public boolean reset(ChunkPos pos) {
        boolean changed = stamps.remove(pos.toLong()) != null;
        if (changed) setDirty();
        return changed;
    }

    public int resetAround(ChunkPos center, int radius) {
        int removed = 0;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                if (stamps.remove(ChunkPos.asLong(x, z)) != null) removed++;
            }
        }
        if (removed > 0) setDirty();
        return removed;
    }

    public int count() {
        return stamps.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag chunks = new ListTag();
        for (ChunkStamp stamp : stamps.values()) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("x", stamp.x());
            chunkTag.putInt("z", stamp.z());
            chunkTag.putLong("day_time", stamp.dayTime());
            chunkTag.putLong("game_time", stamp.gameTime());
            chunks.add(chunkTag);
        }
        tag.put(CHUNKS_KEY, chunks);
        return tag;
    }

    public record ChunkStamp(int x, int z, long dayTime, long gameTime) {
    }
}

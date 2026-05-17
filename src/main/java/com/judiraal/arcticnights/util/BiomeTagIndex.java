package com.judiraal.arcticnights.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class BiomeTagIndex {
    private BiomeTagIndex() {
    }

    static Map<String, List<String>> byBiome(MinecraftServer server) {
        Map<String, List<String>> tagsByBiome = new TreeMap<>();
        var biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        biomeRegistry.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> biomeRegistry.getHolder(entry.getKey())
                        .ifPresent(holder -> holder.tags().forEach(tag -> tagsByBiome
                                .computeIfAbsent(entry.getKey().location().toString(), ignored -> new ArrayList<>())
                                .add(tag.location().toString()))));
        tagsByBiome.replaceAll((key, values) -> values.stream().distinct().sorted().toList());
        return Map.copyOf(tagsByBiome);
    }
}

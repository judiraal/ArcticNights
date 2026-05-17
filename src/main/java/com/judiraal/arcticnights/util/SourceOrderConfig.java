package com.judiraal.arcticnights.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

record SourceOrderConfig(List<SourceOrderTier> tiers) {
    private static final ResourceLocation SOURCE_ORDER = ResourceLocation.fromNamespaceAndPath("iwjei", "source_order/phase_0_2.json");

    static SourceOrderConfig load(MinecraftServer server) {
        return server.getResourceManager().getResource(SOURCE_ORDER)
                .map(resource -> {
                    try (Reader reader = resource.openAsReader()) {
                        JsonElement parsed = JsonParser.parseReader(reader);
                        if (!parsed.isJsonObject()) return new SourceOrderConfig(List.of());
                        JsonArray tiers = parsed.getAsJsonObject().getAsJsonArray("tiers");
                        if (tiers == null) return new SourceOrderConfig(List.of());
                        List<SourceOrderTier> loaded = new ArrayList<>();
                        for (JsonElement element : tiers) {
                            if (!element.isJsonObject()) continue;
                            var object = element.getAsJsonObject();
                            loaded.add(new SourceOrderTier(
                                    AuditJson.string(object, "id"),
                                    AuditJson.integer(object, "phase"),
                                    AuditJson.strings(object, "biomes"),
                                    AuditJson.string(object, "notes")
                            ));
                        }
                        return new SourceOrderConfig(loaded);
                    } catch (IOException | JsonParseException | IllegalStateException ignored) {
                        return new SourceOrderConfig(List.of());
                    }
                })
                .orElseGet(() -> new SourceOrderConfig(List.of()));
    }

    SourceOrderTier tierForBiome(String biome, List<String> tags) {
        SourceOrderTier best = null;
        for (SourceOrderTier tier : tiers) {
            if (!tier.matchesBiome(biome, tags)) continue;
            if (best == null || tier.phase() > best.phase()) best = tier;
        }
        return best;
    }
}

record SourceOrderTier(String id, int phase, List<String> biomes, String notes) {
    boolean matchesBiome(String biome, List<String> tags) {
        for (String matcher : biomes) {
            if (matcher.startsWith("#") && tags.contains(matcher.substring(1))) return true;
            if (matcher.endsWith(":*") && biome.startsWith(matcher.substring(0, matcher.length() - 1))) return true;
            if (matcher.equals(biome)) return true;
        }
        return false;
    }
}

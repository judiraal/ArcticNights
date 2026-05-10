package com.judiraal.arcticnights.worldgen;

import com.judiraal.arcticnights.ArcticNightsConfig;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;

public final class StructureExclusionContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private StructureExclusionContext() {
    }

    public static void withExclusion(Runnable action) {
        ACTIVE.set(true);
        try {
            action.run();
        } finally {
            ACTIVE.remove();
        }
    }

    public static boolean shouldDeny(Holder<Structure> structure) {
        return ACTIVE.get() && !isAllowed(structure);
    }

    private static boolean isAllowed(Holder<Structure> structure) {
        return structure.unwrapKey()
                .map(key -> key.location().toString())
                .filter(StructureExclusionContext::matchesAllowlist)
                .isPresent();
    }

    private static boolean matchesAllowlist(String structureId) {
        List<? extends String> allowlist = ArcticNightsConfig.structureDenyAllowlist.get();
        if (allowlist.isEmpty()) return false;
        ResourceLocation id = ResourceLocation.tryParse(structureId);
        if (id == null) return false;

        for (String pattern : allowlist) {
            if (matchesPattern(structureId, pattern)) return true;
        }

        return false;
    }

    private static boolean matchesPattern(String value, String pattern) {
        if (pattern.isBlank()) return false;
        if (!pattern.contains("*")) return value.equals(pattern);

        int valueIndex = 0;
        String[] parts = pattern.split("\\*", -1);
        if (!parts[0].isEmpty()) {
            if (!value.startsWith(parts[0])) return false;
            valueIndex = parts[0].length();
        }

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            int next = value.indexOf(part, valueIndex);
            if (next < 0) return false;
            valueIndex = next + part.length();
        }

        String last = parts[parts.length - 1];
        return last.isEmpty() || value.endsWith(last);
    }
}

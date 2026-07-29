package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

enum CaveTemperatureProfile {
    COLD("cold", -10.0D),
    COOL("cool", -3.0D),
    MILD_COOL("mild_cool", -2.0D),
    WARM("warm", 8.0D),
    HOT("hot", 12.0D);

    private static final String TAG_PATH_PREFIX = "cave_temperature/";
    private static final CaveTemperatureProfile[] VALUES = values();

    private final TagKey<Biome> biomeTag;
    private final double celsiusOffset;
    private final float minecraftOffset;

    CaveTemperatureProfile(String tagPath, double celsiusOffset) {
        this.biomeTag = TagKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ArcticNights.MOD_ID, TAG_PATH_PREFIX + tagPath)
        );
        this.celsiusOffset = celsiusOffset;
        this.minecraftOffset = ClimateService.celsiusOffsetToMinecraftTemperatureOffset(celsiusOffset);
    }

    static float minecraftOffset(Holder<Biome> biome) {
        for (CaveTemperatureProfile profile : VALUES) {
            if (biome.is(profile.biomeTag)) {
                return profile.minecraftOffset;
            }
        }
        return 0.0F;
    }

    static boolean hasColdProfile(Holder<Biome> biome) {
        return biome.is(COLD.biomeTag);
    }

    TagKey<Biome> biomeTag() {
        return biomeTag;
    }

    double celsiusOffset() {
        return celsiusOffset;
    }
}

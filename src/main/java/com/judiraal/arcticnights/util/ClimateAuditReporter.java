package com.judiraal.arcticnights.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class ClimateAuditReporter {
    private static final ResourceLocation SOURCE_ORDER = ResourceLocation.fromNamespaceAndPath("iwjei", "source_order/phase_0_2.json");
    private static final List<Sample> SAMPLES = List.of(
            new Sample("surface", 64, true, 0.0F),
            new Sample("mountain", 128, true, 0.0F),
            new Sample("cave", 32, false, 0.8F)
    );
    private static final List<TimeSample> TIME_SAMPLES = List.of(
            new TimeSample("dawn", 0L),
            new TimeSample("noon", 6_000L),
            new TimeSample("dusk", 12_000L),
            new TimeSample("midnight", 18_000L)
    );
    private static final TimeSample SPAWN_TIME = new TimeSample("midnight", 18_000L);
    private static final List<WeatherSample> WEATHER_SAMPLES = List.of(
            new WeatherSample("clear", ClimateSnapshot.WeatherState.CLEAR),
            new WeatherSample("rain", ClimateSnapshot.WeatherState.RAIN),
            new WeatherSample("thunder", ClimateSnapshot.WeatherState.THUNDER)
    );
    private static final Sample SPAWN_SURFACE_SAMPLE = new Sample("surface", 64, true, 0.0F);
    private static final float UNDEAD_COLD_THRESHOLD = 0.0F / 25.0F;
    private static final float UNDEAD_COLD_RAMP = 0.48F;
    private static final float RAIN_STRAY_UNDEAD_FACTOR = 0.10F;
    private static final float RAIN_STRAY_UNDEAD_START = 3.0F / 25.0F;
    private static final float RAIN_STRAY_UNDEAD_FULL = -3.0F / 25.0F;
    private static final float MIN_MEANINGFUL_SPAWN_FACTOR = 0.08F;
    private static final float MIN_MEANINGFUL_SPIDER_FACTOR = 0.20F;
    private static final float CREEPER_HEAT_START = 20.0F / 25.0F;
    private static final float CREEPER_HEAT_FULL = 37.0F / 25.0F;
    private static final float MIN_MEANINGFUL_CREEPER_FACTOR = 0.08F;
    // Mirrors Serene Seasons' 12-subseason cadence without linking the audit path to its API.
    private static final String[] SUB_SEASON_NAMES = {
            "early_spring", "mid_spring", "late_spring",
            "early_summer", "mid_summer", "late_summer",
            "early_autumn", "mid_autumn", "late_autumn",
            "early_winter", "mid_winter", "late_winter"
    };

    private ClimateAuditReporter() {
    }

    public static ClimateAuditResult audit(MinecraftServer server) throws IOException {
        Path outputDir = server.getWorldPath(LevelResource.ROOT).resolve("logs/arcticnights");
        return audit(server, outputDir);
    }

    public static ClimateAuditResult audit(MinecraftServer server, Path outputDir) throws IOException {
        long started = System.nanoTime();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) throw new IOException("Overworld is not loaded");

        SourceOrderConfig sourceOrder = SourceOrderConfig.load(server);
        ColdSweatOutdoorConfig coldSweat = ColdSweatOutdoorConfig.load();
        Map<String, List<String>> tagsByBiome = biomeTags(server);
        List<ClimateAuditRow> rows = new ArrayList<>();
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        biomes.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    ResourceKey<Biome> key = entry.getKey();
                    Holder<Biome> holder = biomes.getHolder(key).orElse(null);
                    if (holder == null) return;
                    String biomeId = key.location().toString();
                    List<String> tags = tagsByBiome.getOrDefault(biomeId, List.of());
                    SourceOrderTier tier = sourceOrder.tierForBiome(biomeId, tags);
                    if (tier == null || tier.phase() > 2) return;
                    String archetype = archetype(biomeId, tags);
                    List<Sample> samples = relevantSamples(archetype);
                    for (SeasonSample season : seasonSamples()) {
                        for (WeatherSample weather : WEATHER_SAMPLES) {
                            for (TimeSample time : TIME_SAMPLES) {
                                for (LatitudeSample latitude : latitudeSamples()) {
                                    for (Sample sample : samples) {
                                        BlockPos pos = sample.pos(latitude);
                                        ClimateSnapshot snapshot = ClimateService.auditSnapshot(level, holder, pos, sample.exposedToSky(), season.seasonFactor(), weather.weatherState(), time.dayTime());
                                        rows.add(row(biomeId, tags, tier, archetype, season, weather, time, latitude, sample, snapshot, coldSweat));
                                    }
                                }
                            }
                        }
                    }
                });

        ClimateAuditReport report = new ClimateAuditReport(
                Instant.now().toString(),
                elapsedMillis(started),
                distinctBiomes(rows),
                rows.size(),
                flagRowCount(rows, ClimateAuditReporter::isPrimaryFlag),
                flagRowCount(rows, ClimateAuditReporter::isReferenceFlag),
                flagRowCount(rows, ClimateAuditReporter::isHypotheticalLatitudeFlag),
                countBy(rows, row -> row.phase() == null ? "unassigned" : "phase_" + row.phase()),
                countBy(rows, ClimateAuditRow::archetype),
                flagCounts(rows, ClimateAuditReporter::isPrimaryFlag),
                flagCounts(rows, ClimateAuditReporter::isReferenceFlag),
                flagCounts(rows, ClimateAuditReporter::isHypotheticalLatitudeFlag),
                rows
        );

        Files.createDirectories(outputDir);
        Path csv = outputDir.resolve("climate-audit.csv");
        Path markdown = outputDir.resolve("climate-audit.md");
        Files.writeString(csv, toCsv(report), StandardCharsets.UTF_8);
        Files.writeString(markdown, toMarkdown(report), StandardCharsets.UTF_8);
        return new ClimateAuditResult(report, csv, markdown);
    }

    public static SpawnMatrixResult auditSpawnMatrix(MinecraftServer server) throws IOException {
        Path outputDir = server.getWorldPath(LevelResource.ROOT).resolve("logs/arcticnights");
        return auditSpawnMatrix(server, outputDir);
    }

    public static SpawnMatrixResult auditSpawnMatrix(MinecraftServer server, Path outputDir) throws IOException {
        long started = System.nanoTime();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) throw new IOException("Overworld is not loaded");

        SourceOrderConfig sourceOrder = SourceOrderConfig.load(server);
        PhaseEntityGateConfig phaseEntityGates = PhaseEntityGateConfig.load(server);
        Map<String, List<String>> tagsByBiome = biomeTags(server);
        List<SpawnMatrixRow> rows = new ArrayList<>();
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        biomes.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    ResourceKey<Biome> key = entry.getKey();
                    Holder<Biome> holder = biomes.getHolder(key).orElse(null);
                    if (holder == null) return;
                    String biomeId = key.location().toString();
                    List<String> tags = tagsByBiome.getOrDefault(biomeId, List.of());
                    SourceOrderTier tier = sourceOrder.tierForBiome(biomeId, tags);
                    if (tier == null || tier.phase() > 2) return;
                    String archetype = archetype(biomeId, tags);
                    for (SeasonSample season : allSubSeasonSamples()) {
                        for (WeatherSample weather : WEATHER_SAMPLES) {
                            for (LatitudeSample latitude : latitudeSamples()) {
                                BlockPos pos = SPAWN_SURFACE_SAMPLE.pos(latitude);
                                ClimateSnapshot snapshot = ClimateService.auditSnapshot(level, holder, pos, true, season.seasonFactor(), weather.weatherState(), SPAWN_TIME.dayTime());
                                rows.add(spawnRow(biomeId, tags, holder, tier, archetype, season, weather, latitude, SPAWN_SURFACE_SAMPLE, SPAWN_TIME, snapshot, phaseEntityGates));
                            }
                        }
                    }
                });

        SpawnMatrixReport report = new SpawnMatrixReport(
                Instant.now().toString(),
                elapsedMillis(started),
                distinctSpawnBiomes(rows),
                rows.size(),
                (int) rows.stream().filter(SpawnMatrixRow::climateModified).count(),
                (int) rows.stream().filter(SpawnMatrixRow::packGateModified).count(),
                phaseEntityGates.summary(),
                countBy(rows, row -> "phase_" + row.phase()),
                countBy(rows, SpawnMatrixRow::archetype),
                countBy(rows, row -> familyCode(row.expectedFamilies())),
                countBy(rows, row -> familyCode(row.packExpectedFamilies())),
                rows
        );

        Files.createDirectories(outputDir);
        Path csv = outputDir.resolve("spawn-matrix.csv");
        Path markdown = outputDir.resolve("spawn-matrix.md");
        Files.writeString(csv, spawnMatrixCsv(report), StandardCharsets.UTF_8);
        Files.writeString(markdown, spawnMatrixMarkdown(report), StandardCharsets.UTF_8);
        return new SpawnMatrixResult(report, csv, markdown);
    }

    private static List<Sample> relevantSamples(String archetype) {
        if ("cave_deep".equals(archetype)) return List.of(SAMPLES.get(2));
        if (archetype.startsWith("ocean_")) return List.of(SAMPLES.get(0));
        return SAMPLES;
    }

    private static List<LatitudeSample> latitudeSamples() {
        int equator = ArcticNightsConfig.circumferenceBlockDistance.get() / 8;
        int middle = equator / 2;
        return List.of(
                new LatitudeSample("north_pole", -equator),
                new LatitudeSample("cold_band", -middle),
                new LatitudeSample("temperate_start", 0),
                new LatitudeSample("warm_band", middle),
                new LatitudeSample("equator", equator)
        );
    }

    private static List<SeasonSample> seasonSamples() {
        if (!ArcticNights.SERENE_SEASONS) {
            return List.of(new SeasonSample("current", null, null));
        }
        return List.of(
                subSeasonSample("spring", 1),
                subSeasonSample("summer", 4),
                subSeasonSample("autumn", 7),
                subSeasonSample("winter", 10)
        );
    }

    private static List<SeasonSample> allSubSeasonSamples() {
        if (!ArcticNights.SERENE_SEASONS) {
            return List.of(new SeasonSample("current", null, null));
        }
        List<SeasonSample> samples = new ArrayList<>();
        for (int ordinal = 0; ordinal < SUB_SEASON_NAMES.length; ordinal++) {
            samples.add(subSeasonSample(ordinal));
        }
        return List.copyOf(samples);
    }

    private static SeasonSample subSeasonSample(String name, int ordinal) {
        return new SeasonSample(name, seasonFactorForSubSeason(ordinal), ordinal);
    }

    private static SeasonSample subSeasonSample(int ordinal) {
        return subSeasonSample(SUB_SEASON_NAMES[ordinal], ordinal);
    }

    private static float seasonFactorForSubSeason(int ordinal) {
        float representativeDay = ordinal * 8.0F;
        return Mth.cos((representativeDay - 32.0F) / 48.0F * Mth.PI);
    }

    private static ClimateAuditRow row(String biomeId, List<String> tags, SourceOrderTier tier, String archetype, SeasonSample season, WeatherSample weather, TimeSample time, LatitudeSample latitude, Sample sample, ClimateSnapshot snapshot, ColdSweatOutdoorConfig coldSweat) {
        OutdoorTemperatureRangeC coldSweatOutdoor = coldSweat.outdoorC(biomeId, season.name(), snapshot.rainCooling());
        List<String> flags = flags(tags, archetype, season.name(), weather.name(), sample.exposedToSky(), snapshot, coldSweatOutdoor);
        BlockPos pos = sample.pos(latitude);
        return new ClimateAuditRow(
                biomeId,
                tier.phase(),
                tier.id(),
                archetype,
                season.name(),
                weather.name(),
                time.name(),
                latitude.name(),
                sample.name(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                sample.exposedToSky(),
                climateTags(tags),
                snapshot.minecraftTemperature(),
                snapshot.estimatedCelsius(),
                coldSweatOutdoor == null ? null : coldSweatOutdoor.nightC(),
                coldSweatOutdoor == null ? null : coldSweatOutdoor.noonC(),
                coldSweatOutdoor == null ? null : coldSweatOutdoor.midpointC(),
                snapshot.rainCooling(),
                snapshot.precipitationKind().name().toLowerCase(),
                snapshot.snowBehavior().name().toLowerCase(),
                undeadFactor(tags, spawnTemperature(snapshot), snapshot.minecraftTemperature(), sample.caveFactor(), snapshot.rainCooling()),
                creeperFactor(tags, snapshot, sample.caveFactor()),
                flags
        );
    }

    private static SpawnMatrixRow spawnRow(String biomeId, List<String> tags, Holder<Biome> biome, SourceOrderTier tier, String archetype, SeasonSample season, WeatherSample weather, LatitudeSample latitude, Sample sample, TimeSample time, ClimateSnapshot snapshot, PhaseEntityGateConfig phaseEntityGates) {
        float spawnTemperature = spawnTemperature(snapshot);
        float undeadFactor = undeadFactor(tags, spawnTemperature, snapshot.clearOutdoorMinecraftTemperature(), 0.0F, snapshot.rainCooling());
        float creeperFactor = creeperFactor(tags, snapshot, 0.0F);
        float spiderFactor = spiderFactor(season.subSeasonOrdinal(), snapshot, 0.0F);
        BlockPos pos = sample.pos(latitude);
        List<SpawnEntry> baseEntries = baseMonsterEntries(biome);
        List<SpawnEntry> expectedEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();
        boolean climateModified = false;
        for (SpawnEntry entry : baseEntries) {
            float factor = spawnFactor(entry.type(), tags, spawnTemperature, snapshot, season.subSeasonOrdinal(), 0.0F);
            if (factor <= 0.0F) {
                removedEntries.add(entry.id());
                climateModified = true;
                continue;
            }
            if (factor != 1.0F) climateModified = true;
            expectedEntries.add(entry.withFactor(factor));
        }
        List<SpawnEntry> packExpectedEntries = new ArrayList<>();
        List<String> packRemovedEntries = new ArrayList<>();
        for (SpawnEntry entry : expectedEntries) {
            if (phaseEntityGates.blocks(entry.id(), tier.phase())) {
                packRemovedEntries.add(entry.id());
            } else {
                packExpectedEntries.add(entry);
            }
        }
        boolean packGateModified = !packRemovedEntries.isEmpty();
        return new SpawnMatrixRow(
                biomeId,
                tier.phase(),
                tier.id(),
                archetype,
                season.name(),
                weather.name(),
                latitude.name(),
                sample.name(),
                time.name(),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                snapshot.minecraftTemperature(),
                snapshot.estimatedCelsius(),
                spawnTemperature,
                ClimateService.estimatedCelsius(spawnTemperature),
                snapshot.rainCooling(),
                snapshot.precipitationKind().name().toLowerCase(Locale.ROOT),
                snapshot.snowBehavior().name().toLowerCase(Locale.ROOT),
                undeadFactor,
                creeperFactor,
                spiderFactor,
                entries(baseEntries),
                entries(expectedEntries),
                String.join(";", removedEntries),
                expectedFamilies(expectedEntries),
                entries(packExpectedEntries),
                String.join(";", packRemovedEntries),
                expectedFamilies(packExpectedEntries),
                packGateModified,
                climateModified
        );
    }

    private static List<String> flags(List<String> tags, String archetype, String season, String weather, boolean exposedToSky, ClimateSnapshot snapshot, OutdoorTemperatureRangeC coldSweatOutdoor) {
        List<String> flags = new ArrayList<>();
        if ("unclassified".equals(archetype)) flags.add("unclassified_biome");
        if (exposedToSky && snapshot.estimatedCelsius() > 3.0D && snapshot.snowBehavior() != ClimateSnapshot.SnowBehavior.MELTS) {
            flags.add("snow_above_3c");
        }
        if (exposedToSky && snapshot.estimatedCelsius() < -3.0D && snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.RAIN) {
            flags.add("rain_below_minus_3c");
        }
        if ((tags.contains("c:is_hot") || tags.contains("c:is_hot/overworld"))
                && archetype.contains("cold")
                && !hasFrozenStory(tags)) {
            flags.add("hot_tag_cold_archetype");
        }
        if ((tags.contains("c:is_cold") || tags.contains("c:is_cold/overworld")) && ("warm_humid".equals(archetype) || "hot_arid".equals(archetype))) {
            flags.add("cold_tag_warm_archetype");
        }
        if ("winter".equals(season) && "temperate_forest".equals(archetype) && "clear".equals(weather) && snapshot.estimatedCelsius() > 8.0D) {
            flags.add("warm_temperate_winter");
        }
        if ("summer".equals(season) && ("alpine_cold".equals(archetype) || "ocean_cold_frozen".equals(archetype)) && snapshot.estimatedCelsius() > 16.0D) {
            flags.add("hot_cold_archetype_summer");
        }
        if (exposedToSky && coldSweatOutdoor != null && coldSweatOutdoor.noonC() > 3.0D && snapshot.snowBehavior() != ClimateSnapshot.SnowBehavior.MELTS) {
            flags.add("cold_sweat_config_noon_snow_above_3c");
        }
        if (exposedToSky && coldSweatOutdoor != null && coldSweatOutdoor.nightC() < -3.0D && snapshot.precipitationKind() == ClimateSnapshot.PrecipitationKind.RAIN) {
            flags.add("cold_sweat_config_night_rain_below_minus_3c");
        }
        return flags;
    }

    private static float spawnTemperature(ClimateSnapshot snapshot) {
        return snapshot.outdoorMinecraftTemperature();
    }

    private static float undeadFactor(List<String> tags, float spawnTemperature, float dryMinecraftTemperature, float caveFactor, boolean rainCooling) {
        if (tags.contains("c:is_hot") || tags.contains("c:is_hot/overworld")) return 0.0F;
        float rainStrayFactor = rainCooling && dryMinecraftTemperature > UNDEAD_COLD_THRESHOLD
                ? coldRainUndeadFactor(spawnTemperature)
                : 0.0F;
        if (rainStrayFactor < MIN_MEANINGFUL_SPAWN_FACTOR) rainStrayFactor = 0.0F;
        float coldSeverity = Mth.clamp((UNDEAD_COLD_THRESHOLD - spawnTemperature) / UNDEAD_COLD_RAMP, 0.0F, 1.0F);
        float factor = Mth.lerp(caveFactor / 2.0F, coldSeverity * coldSeverity * 2.2F, 1.0F);
        if (factor < MIN_MEANINGFUL_SPAWN_FACTOR) factor = 0.0F;
        return Math.max(factor, rainStrayFactor);
    }

    private static float creeperFactor(List<String> tags, ClimateSnapshot snapshot, float caveFactor) {
        if (tags.contains("c:is_cold") || tags.contains("c:is_cold/overworld")) return 0.0F;
        float heatSeverity = smoothStep((snapshot.outdoorMinecraftTemperature() - CREEPER_HEAT_START) / (CREEPER_HEAT_FULL - CREEPER_HEAT_START));
        float factor = Mth.lerp(caveFactor / 2.0F, heatSeverity * 2.0F, 1.0F);
        return factor < MIN_MEANINGFUL_CREEPER_FACTOR ? 0.0F : factor;
    }

    private static float spiderFactor(Integer subSeasonOrdinal, ClimateSnapshot snapshot, float caveFactor) {
        float deepFactor = caveFactor >= 0.5F ? 1.0F : 0.0F;
        if (deepFactor > 0.0F) return deepFactor;
        float seasonFactor = autumnProgressionFactor(subSeasonOrdinal);
        if (seasonFactor <= 0.0F) return 0.0F;
        float temp = snapshot.outdoorMinecraftTemperature();
        float coolFactor = 1.0F - smoothStep(Mth.clamp((temp - 0.55F) / 0.35F, 0.0F, 1.0F));
        float weatherFactor = snapshot.rainCooling() ? 0.35F : 0.0F;
        if (snapshot.weatherState() == ClimateSnapshot.WeatherState.THUNDER) weatherFactor += 0.2F;
        float climateFactor = Mth.clamp(0.5F + coolFactor * 0.8F + weatherFactor, 0.35F, 1.65F);
        float factor = seasonFactor * climateFactor;
        return factor < MIN_MEANINGFUL_SPIDER_FACTOR ? 0.0F : factor;
    }

    private static float autumnProgressionFactor(Integer subSeasonOrdinal) {
        if (subSeasonOrdinal == null) return 0.0F;
        float representativeDay = subSeasonOrdinal * 8.0F + 4.0F;
        if (representativeDay < 40.0F) return 0.0F;
        if (representativeDay < 60.0F) return smoothStep((representativeDay - 40.0F) / 20.0F);
        if (representativeDay < 84.0F) return Mth.lerp(smoothStep((representativeDay - 60.0F) / 24.0F), 1.0F, 0.45F);
        if (representativeDay < 96.0F) return Mth.lerp(smoothStep((representativeDay - 84.0F) / 12.0F), 0.45F, 0.0F);
        return 0.0F;
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float coldRainUndeadFactor(float outdoorMinecraftTemperature) {
        if (outdoorMinecraftTemperature >= RAIN_STRAY_UNDEAD_START) return 0.0F;
        float severity = (RAIN_STRAY_UNDEAD_START - outdoorMinecraftTemperature)
                / (RAIN_STRAY_UNDEAD_START - RAIN_STRAY_UNDEAD_FULL);
        return RAIN_STRAY_UNDEAD_FACTOR * smoothStep(severity);
    }

    private static float spawnFactor(EntityType<?> type, List<String> tags, float spawnTemperature, ClimateSnapshot snapshot, Integer subSeasonOrdinal, float caveFactor) {
        if (type == EntityType.WITCH) return tags.contains("arcticnights:witch_wetlands") ? 1.0F : 0.0F;
        if (type.is(ArcticSpawner.REQUIRE_COLD)) return undeadFactor(tags, spawnTemperature, snapshot.clearOutdoorMinecraftTemperature(), caveFactor, snapshot.rainCooling());
        if (type.is(ArcticSpawner.REQUIRE_HOT)) return creeperFactor(tags, snapshot, caveFactor);
        if (type.is(ArcticSpawner.REQUIRE_AUTUMN_OR_DEEP)) return spiderFactor(subSeasonOrdinal, snapshot, caveFactor);
        return 1.0F;
    }

    private static List<SpawnEntry> baseMonsterEntries(Holder<Biome> biome) {
        return biome.value().getMobSettings().getMobs(MobCategory.MONSTER).unwrap().stream()
                .map(ClimateAuditReporter::spawnEntry)
                .sorted(Comparator.comparing(SpawnEntry::id))
                .toList();
    }

    private static SpawnEntry spawnEntry(MobSpawnSettings.SpawnerData data) {
        return new SpawnEntry(
                EntityType.getKey(data.type).toString(),
                data.type,
                data.getWeight().asInt(),
                data.minCount,
                data.maxCount,
                1.0F
        );
    }

    private static String entries(List<SpawnEntry> entries) {
        return entries.stream()
                .map(entry -> entry.id()
                        + "@"
                        + format(entry.factor())
                        + "[w"
                        + entry.weight()
                        + " "
                        + entry.minCount()
                        + "-"
                        + entry.maxCount()
                        + "]")
                .collect(Collectors.joining(";"));
    }

    private static String expectedFamilies(List<SpawnEntry> entries) {
        List<String> families = new ArrayList<>();
        if (entries.stream().anyMatch(entry -> entry.type().is(EntityTypeTags.UNDEAD))) families.add("undead");
        if (entries.stream().anyMatch(entry -> entry.type() == EntityType.CREEPER)) families.add("creeper");
        if (entries.stream().anyMatch(entry -> entry.type() == EntityType.SPIDER)) families.add("spider");
        entries.stream()
                .filter(entry -> !entry.type().is(EntityTypeTags.UNDEAD))
                .filter(entry -> entry.type() != EntityType.CREEPER)
                .filter(entry -> entry.type() != EntityType.SPIDER)
                .map(SpawnEntry::id)
                .distinct()
                .sorted()
                .forEach(families::add);
        if (families.isEmpty()) return "none";
        return String.join("+", families);
    }

    private static String archetype(String biomeId, List<String> tags) {
        if (biomeId.contains("alpha_islands_winter")) return "ocean_cold_frozen";
        if (biomeId.contains("alpha_islands")) return "ocean_temperate_warm";
        if (has(tags, "terralith:caves", "terralith:reference/cave", "terralith:reference/deep_cave") || biomeId.contains("cave") || biomeId.endsWith(":deep_dark")) {
            return "cave_deep";
        }
        if (has(tags, "terralith:volcanic") || biomeId.contains("volcanic")) {
            return "volcanic_geothermal";
        }
        if (has(tags, "c:is_icy", "c:is_snowy", "c:is_aquatic_icy", "minecraft:is_snowy", "terralith:reference/temperature/frozen_all", "terralith:reference/temperature/frozen_with_structures")) {
            if (has(tags, "minecraft:is_ocean", "c:is_shallow_ocean", "c:is_deep_ocean")) return "ocean_cold_frozen";
            return "alpine_cold";
        }
        if (has(tags, "c:is_cold", "c:is_cold/overworld", "terralith:reference/temperature/cold")) {
            if (has(tags, "minecraft:is_ocean", "c:is_shallow_ocean", "c:is_deep_ocean")) return "ocean_cold_frozen";
            return "cool_taiga";
        }
        if (has(tags, "minecraft:is_ocean", "c:is_shallow_ocean", "c:is_deep_ocean")) {
            return "ocean_temperate_warm";
        }
        if (has(tags, "minecraft:is_beach", "minecraft:is_river", "c:is_beach") || biomeId.contains("beach") || biomeId.contains("river")) {
            return "coastal_wet";
        }
        if (has(tags, "minecraft:is_jungle", "c:is_swamp", "c:is_wet", "c:is_wet/overworld", "terralith:reference/jungle", "terralith:reference/swamp", "terralith:reference/temperature/lukewarm", "terralith:reference/temperature/warm")) {
            return "warm_humid";
        }
        if (has(tags, "minecraft:is_badlands", "minecraft:is_savanna", "c:is_hot", "c:is_hot/overworld", "c:is_dry", "c:is_dry/overworld", "terralith:reference/desert_all", "terralith:reference/savanna")) {
            return "hot_arid";
        }
        if (has(tags, "minecraft:is_mountain", "c:is_mountain", "c:is_mountain/peak", "c:is_mountain/slope", "terralith:reference/mountain_peak", "terralith:reference/mountain_slope", "terralith:reference/windswept")) {
            return "alpine_cold";
        }
        if (has(tags, "minecraft:is_taiga", "c:is_taiga")) {
            return "cool_taiga";
        }
        if (has(tags, "minecraft:is_forest", "c:is_forest", "terralith:reference/forest")) {
            return "temperate_forest";
        }
        if (has(tags, "terralith:reference/plains", "terralith:reference/temperature/temperate") || biomeId.contains("plains") || biomeId.contains("meadow")) {
            return "temperate_open";
        }
        return "unclassified";
    }

    private static boolean has(List<String> tags, String... wanted) {
        for (String tag : wanted) if (tags.contains(tag)) return true;
        return false;
    }

    private static boolean hasFrozenStory(List<String> tags) {
        return has(tags, "c:is_icy", "c:is_snowy", "c:is_aquatic_icy", "minecraft:is_snowy", "terralith:reference/temperature/frozen_all", "terralith:reference/temperature/frozen_with_structures");
    }

    private static List<String> climateTags(List<String> tags) {
        return tags.stream()
                .filter(tag -> tag.contains("temperature")
                        || tag.contains("is_cold")
                        || tag.contains("is_hot")
                        || tag.contains("is_wet")
                        || tag.contains("is_dry")
                        || tag.contains("is_snowy")
                        || tag.contains("is_icy")
                        || tag.contains("is_ocean")
                        || tag.contains("is_mountain")
                        || tag.contains("is_forest")
                        || tag.contains("is_taiga")
                        || tag.contains("is_jungle")
                        || tag.contains("is_savanna")
                        || tag.contains("is_badlands")
                        || tag.contains("is_beach")
                        || tag.contains("is_river")
                        || tag.contains("cave")
                        || tag.contains("volcanic"))
                .distinct()
                .sorted()
                .toList();
    }

    private static Map<String, List<String>> biomeTags(MinecraftServer server) {
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

    private static int distinctBiomes(List<ClimateAuditRow> rows) {
        return (int) rows.stream().map(ClimateAuditRow::biome).distinct().count();
    }

    private static int distinctSpawnBiomes(List<SpawnMatrixRow> rows) {
        return (int) rows.stream().map(SpawnMatrixRow::biome).distinct().count();
    }

    private static int flagRowCount(List<ClimateAuditRow> rows, Predicate<String> predicate) {
        return (int) rows.stream()
                .filter(row -> row.flags().stream().anyMatch(predicate))
                .count();
    }

    private static Map<String, Long> flagCounts(List<ClimateAuditRow> rows, Predicate<String> predicate) {
        Map<String, Long> counts = new TreeMap<>();
        for (ClimateAuditRow row : rows) {
            for (String flag : row.flags()) {
                if (predicate.test(flag)) counts.merge(flag, 1L, Long::sum);
            }
        }
        return counts;
    }

    private static boolean isPrimaryFlag(String flag) {
        return !isReferenceFlag(flag) && !isHypotheticalLatitudeFlag(flag);
    }

    private static boolean isReferenceFlag(String flag) {
        return flag.startsWith("cold_sweat_config_");
    }

    private static boolean isHypotheticalLatitudeFlag(String flag) {
        return "warm_temperate_winter".equals(flag) || "hot_cold_archetype_summer".equals(flag);
    }

    private static <T> Map<String, Long> countBy(List<T> rows, Function<T, String> classifier) {
        return rows.stream().collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.counting()));
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static String toMarkdown(ClimateAuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Arctic Nights Climate Audit\n\n");
        sb.append("- Generated: ").append(report.generatedAt()).append('\n');
        sb.append("- Duration: ").append(report.durationMillis()).append(" ms\n");
        sb.append("- Biomes: ").append(report.biomeCount()).append('\n');
        sb.append("- Rows: ").append(report.rowCount()).append('\n');
        sb.append("- Runtime/Classification Flagged Rows: ").append(report.flaggedRowCount()).append('\n');
        sb.append("- Cold Sweat Static Reference Warning Rows: ").append(report.referenceWarningRowCount()).append('\n');
        sb.append("- Hypothetical Latitude Warning Rows: ").append(report.hypotheticalWarningRowCount()).append("\n\n");
        appendSummary(sb, "Rows By Phase", report.rowsByPhase());
        appendSummary(sb, "Rows By Archetype", report.rowsByArchetype());
        appendSummary(sb, "Runtime And Classification Flags", report.flags());
        appendSummary(sb, "Cold Sweat Static Reference Warnings", report.referenceWarnings());
        appendSummary(sb, "Hypothetical Latitude Warnings", report.hypotheticalWarnings());
        sb.append("Cold Sweat config columns are the static config values before the Arctic Nights runtime outdoor-climate modifier. Use `Est C` for the authoritative Arctic Nights outdoor temperature used by snow, spawn ecology, and the live Cold Sweat modifier.\n");
        sb.append("Static Cold Sweat reference warnings and hypothetical latitude warnings are separated from runtime flags so the headline stays focused on behavior the player can actually experience.\n");
        sb.append("Samples cover representative latitudes, day/night positions, clear/rain/thunder weather, surface/mountain/cave positions, and seasonal anchors. Biome rows are hypothetical at each latitude: they test climate behavior if that biome exists there, not whether worldgen actually places that biome there.\n\n");
        sb.append("## Runtime And Classification Flagged Rows\n\n");
        if (report.flaggedRowCount() == 0) {
            sb.append("_None._\n");
            return sb.toString();
        }
        sb.append("| Biome | Phase | Tier | Archetype | Season | Weather | Time | Latitude | Sample | Z | MC Temp | Est C | CS Config Night | CS Config Noon | CS Config Mid | Precip | Snow | Undead | Creeper | Flags |\n");
        sb.append("|---|---:|---|---|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|---|---:|---:|---|\n");
        for (ClimateAuditRow row : report.rows()) {
            List<String> primaryFlags = row.flags().stream().filter(ClimateAuditReporter::isPrimaryFlag).toList();
            if (primaryFlags.isEmpty()) continue;
            sb.append("| `").append(row.biome()).append("` | ")
                    .append(row.phase() == null ? "" : row.phase()).append(" | ")
                    .append(escape(row.tier())).append(" | ")
                    .append(escape(row.archetype())).append(" | ")
                    .append(escape(row.season())).append(" | ")
                    .append(escape(row.weather())).append(" | ")
                    .append(escape(row.time())).append(" | ")
                    .append(escape(row.latitude())).append(" | ")
                    .append(escape(row.sample())).append(" | ")
                    .append(row.z()).append(" | ")
                    .append(format(row.minecraftTemperature())).append(" | ")
                    .append(format(row.estimatedCelsius())).append(" | ")
                    .append(formatNullable(row.coldSweatNightC())).append(" | ")
                    .append(formatNullable(row.coldSweatNoonC())).append(" | ")
                    .append(formatNullable(row.coldSweatOutdoorC())).append(" | ")
                    .append(row.precipitation()).append(" | ")
                    .append(row.snowBehavior()).append(" | ")
                    .append(format(row.undeadFactor())).append(" | ")
                    .append(format(row.creeperFactor())).append(" | ")
                    .append(escape(primaryFlags.toString())).append(" |\n");
        }
        return sb.toString();
    }

    private static void appendSummary(StringBuilder sb, String title, Map<String, Long> values) {
        sb.append("## ").append(title).append("\n\n");
        if (values.isEmpty()) {
            sb.append("_None._\n\n");
            return;
        }
        values.forEach((key, value) -> sb.append("- `").append(key).append("`: ").append(value).append('\n'));
        sb.append('\n');
    }

    private static String toCsv(ClimateAuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("biome,phase,tier,archetype,season,weather,time,latitude,sample,x,y,z,exposed,climate_tags,minecraft_temperature,estimated_celsius,cold_sweat_config_night_c,cold_sweat_config_noon_c,cold_sweat_config_outdoor_c,rain_cooling,precipitation,snow_behavior,undead_factor,creeper_factor,flags\n");
        for (ClimateAuditRow row : report.rows()) {
            sb.append(csv(row.biome())).append(',')
                    .append(row.phase() == null ? "" : row.phase()).append(',')
                    .append(csv(row.tier())).append(',')
                    .append(csv(row.archetype())).append(',')
                    .append(csv(row.season())).append(',')
                    .append(csv(row.weather())).append(',')
                    .append(csv(row.time())).append(',')
                    .append(csv(row.latitude())).append(',')
                    .append(csv(row.sample())).append(',')
                    .append(row.x()).append(',')
                    .append(row.y()).append(',')
                    .append(row.z()).append(',')
                    .append(row.exposed()).append(',')
                    .append(csv(row.climateTags().toString())).append(',')
                    .append(format(row.minecraftTemperature())).append(',')
                    .append(format(row.estimatedCelsius())).append(',')
                    .append(formatNullable(row.coldSweatNightC())).append(',')
                    .append(formatNullable(row.coldSweatNoonC())).append(',')
                    .append(formatNullable(row.coldSweatOutdoorC())).append(',')
                    .append(row.rainCooling()).append(',')
                    .append(csv(row.precipitation())).append(',')
                    .append(csv(row.snowBehavior())).append(',')
                    .append(format(row.undeadFactor())).append(',')
                    .append(format(row.creeperFactor())).append(',')
                    .append(csv(row.flags().toString())).append('\n');
        }
        return sb.toString();
    }

    private static String spawnMatrixMarkdown(SpawnMatrixReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Arctic Nights Spawn Matrix\n\n");
        sb.append("- Generated: ").append(report.generatedAt()).append('\n');
        sb.append("- Duration: ").append(report.durationMillis()).append(" ms\n");
        sb.append("- Biomes: ").append(report.biomeCount()).append('\n');
        sb.append("- Rows: ").append(report.rowCount()).append('\n');
        sb.append("- Climate-modified rows: ").append(report.climateModifiedRowCount()).append('\n');
        sb.append("- Pack-gate-modified rows: ").append(report.packGateModifiedRowCount()).append('\n');
        sb.append("- Pack gate source: ").append(escape(report.packGateSource())).append("\n\n");
        appendSummary(sb, "Rows By Phase", report.rowsByPhase());
        appendSummary(sb, "Rows By Archetype", report.rowsByArchetype());
        appendSummary(sb, "Arctic Nights Family Codes", report.rowsByExpectedFamilies());
        appendSummary(sb, "Pack-Effective Family Codes", report.rowsByPackExpectedFamilies());
        appendTopSummary(sb, "Top Pack-Effective Entities", entityCounts(report.rows(), SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Top Phase 0 Pack-Effective Entities", entityCountsForPhase(report.rows(), 0, SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Top Phase 1 Pack-Effective Entities", entityCountsForPhase(report.rows(), 1, SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Top Phase 2 Pack-Effective Entities", entityCountsForPhase(report.rows(), 2, SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Pack-Removed Entities", entityCounts(report.rows(), SpawnMatrixRow::packRemovedMonsters, false), 20);
        sb.append("## Legend\n\n");
        sb.append("- Subseason codes: `ES` early spring, `MS` mid spring, `LS` late spring, `EU` early summer, `MU` mid summer, `LU` late summer, `EA` early autumn, `MA` mid autumn, `LA` late autumn, `EW` early winter, `MW` mid winter, `LW` late winter.\n");
        sb.append("- Family codes: `U` undead, `C` creeper, `S` spider, `O` other unchanged hostile mobs, `-` none.\n");
        sb.append("- The CSV keeps raw Arctic Nights columns and pack-effective columns. The profile below uses pack-effective families after IW Core phase entity gates are applied when available.\n");
        sb.append("- This matrix intersects Arctic Nights climate factors with each biome's actual monster spawn list at representative latitudes. It does not include structure spawns, spawners, cave/depth variants, or world milestone state beyond current phase-gate rules.\n");
        sb.append("- Spawn rows model exposed surface spawning at midnight. Biome profiles below show the `temperate_start` latitude only; use the CSV for full latitude/weather detail.\n\n");
        sb.append("## Biome Profiles\n\n");
        sb.append("| Biome | Phase | Archetype | Clear | Rain | Thunder |\n");
        sb.append("|---|---:|---|---|---|---|\n");
        for (BiomeSpawnProfile profile : biomeSpawnProfiles(report.rows())) {
            sb.append("| `").append(profile.biome()).append("` | ")
                    .append(profile.phase()).append(" | ")
                    .append(escape(profile.archetype())).append(" | ")
                    .append(escape(profile.clearProfile())).append(" | ")
                    .append(escape(profile.rainProfile())).append(" | ")
                    .append(escape(profile.thunderProfile())).append(" |\n");
        }
        return sb.toString();
    }

    private static String spawnMatrixCsv(SpawnMatrixReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("biome,phase,tier,archetype,subseason,weather,latitude,sample,time,x,y,z,minecraft_temperature,estimated_celsius,spawn_temperature,spawn_estimated_celsius,rain_cooling,precipitation,snow_behavior,undead_factor,creeper_factor,spider_factor,base_monsters,expected_monsters,removed_monsters,expected_families,pack_expected_monsters,pack_removed_monsters,pack_expected_families,pack_gate_modified,climate_modified\n");
        for (SpawnMatrixRow row : report.rows()) {
            sb.append(csv(row.biome())).append(',')
                    .append(row.phase()).append(',')
                    .append(csv(row.tier())).append(',')
                    .append(csv(row.archetype())).append(',')
                    .append(csv(row.subseason())).append(',')
                    .append(csv(row.weather())).append(',')
                    .append(csv(row.latitude())).append(',')
                    .append(csv(row.sample())).append(',')
                    .append(csv(row.time())).append(',')
                    .append(row.x()).append(',')
                    .append(row.y()).append(',')
                    .append(row.z()).append(',')
                    .append(format(row.minecraftTemperature())).append(',')
                    .append(format(row.estimatedCelsius())).append(',')
                    .append(format(row.spawnTemperature())).append(',')
                    .append(format(row.spawnEstimatedCelsius())).append(',')
                    .append(row.rainCooling()).append(',')
                    .append(csv(row.precipitation())).append(',')
                    .append(csv(row.snowBehavior())).append(',')
                    .append(format(row.undeadFactor())).append(',')
                    .append(format(row.creeperFactor())).append(',')
                    .append(format(row.spiderFactor())).append(',')
                    .append(csv(row.baseMonsters())).append(',')
                    .append(csv(row.expectedMonsters())).append(',')
                    .append(csv(row.removedMonsters())).append(',')
                    .append(csv(row.expectedFamilies())).append(',')
                    .append(csv(row.packExpectedMonsters())).append(',')
                    .append(csv(row.packRemovedMonsters())).append(',')
                    .append(csv(row.packExpectedFamilies())).append(',')
                    .append(row.packGateModified()).append(',')
                    .append(row.climateModified()).append('\n');
        }
        return sb.toString();
    }

    private static Map<String, Long> entityCounts(List<SpawnMatrixRow> rows, Function<SpawnMatrixRow, String> entries, boolean weightedEntries) {
        Map<String, Long> counts = new HashMap<>();
        for (SpawnMatrixRow row : rows) {
            HashSet<String> rowEntities = new HashSet<>();
            for (String entry : entries.apply(row).split(";")) {
                if (entry.isBlank()) continue;
                String id = weightedEntries ? entry.split("@", 2)[0] : entry;
                rowEntities.add(id);
            }
            rowEntities.forEach(id -> counts.merge(id, 1L, Long::sum));
        }
        return counts;
    }

    private static Map<String, Long> entityCountsForPhase(List<SpawnMatrixRow> rows, int phase, Function<SpawnMatrixRow, String> entries, boolean weightedEntries) {
        return entityCounts(rows.stream().filter(row -> row.phase() == phase).toList(), entries, weightedEntries);
    }

    private static void appendTopSummary(StringBuilder sb, String title, Map<String, Long> values, int limit) {
        sb.append("## ").append(title).append("\n\n");
        if (values.isEmpty()) {
            sb.append("_None._\n\n");
            return;
        }
        values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(entry -> sb.append("- `").append(entry.getKey()).append("`: ").append(entry.getValue()).append('\n'));
        sb.append('\n');
    }

    private static List<BiomeSpawnProfile> biomeSpawnProfiles(List<SpawnMatrixRow> rows) {
        Map<String, List<SpawnMatrixRow>> byBiome = rows.stream().collect(Collectors.groupingBy(SpawnMatrixRow::biome, TreeMap::new, Collectors.toList()));
        List<BiomeSpawnProfile> profiles = new ArrayList<>();
        for (Map.Entry<String, List<SpawnMatrixRow>> entry : byBiome.entrySet()) {
            List<SpawnMatrixRow> biomeRows = entry.getValue();
            SpawnMatrixRow first = biomeRows.getFirst();
            profiles.add(new BiomeSpawnProfile(
                    first.biome(),
                    first.phase(),
                    first.archetype(),
                    weatherProfile(biomeRows, "clear"),
                    weatherProfile(biomeRows, "rain"),
                    weatherProfile(biomeRows, "thunder")
            ));
        }
        return profiles;
    }

    private static String weatherProfile(List<SpawnMatrixRow> rows, String weather) {
        return rows.stream()
                .filter(row -> weather.equals(row.weather()))
                .filter(row -> "temperate_start".equals(row.latitude()))
                .filter(row -> "surface".equals(row.sample()))
                .filter(row -> SPAWN_TIME.name().equals(row.time()))
                .sorted(Comparator.comparingInt(row -> subSeasonOrder(row.subseason())))
                .map(row -> subSeasonCode(row.subseason()) + ":" + familyCode(row.packExpectedFamilies()))
                .collect(Collectors.joining(" "));
    }

    private static int subSeasonOrder(String subseason) {
        return switch (subseason) {
            case "early_spring" -> 0;
            case "mid_spring" -> 1;
            case "late_spring" -> 2;
            case "early_summer" -> 3;
            case "mid_summer" -> 4;
            case "late_summer" -> 5;
            case "early_autumn" -> 6;
            case "mid_autumn" -> 7;
            case "late_autumn" -> 8;
            case "early_winter" -> 9;
            case "mid_winter" -> 10;
            case "late_winter" -> 11;
            default -> 99;
        };
    }

    private static String subSeasonCode(String subseason) {
        return switch (subseason) {
            case "early_spring" -> "ES";
            case "mid_spring" -> "MS";
            case "late_spring" -> "LS";
            case "early_summer" -> "EU";
            case "mid_summer" -> "MU";
            case "late_summer" -> "LU";
            case "early_autumn" -> "EA";
            case "mid_autumn" -> "MA";
            case "late_autumn" -> "LA";
            case "early_winter" -> "EW";
            case "mid_winter" -> "MW";
            case "late_winter" -> "LW";
            default -> subseason;
        };
    }

    private static String familyCode(String families) {
        if ("none".equals(families)) return "-";
        List<String> codes = new ArrayList<>();
        if (families.contains("undead")) codes.add("U");
        if (families.contains("creeper")) codes.add("C");
        if (families.contains("spider")) codes.add("S");
        boolean hasOther = List.of(families.split("\\+")).stream()
                .anyMatch(family -> !family.equals("undead") && !family.equals("creeper") && !family.equals("spider"));
        if (hasOther) codes.add("O");
        return String.join("", codes);
    }

    private static String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String formatNullable(Double value) {
        return value == null ? "" : format(value);
    }

    private record Sample(String name, int y, boolean exposedToSky, float caveFactor) {
        BlockPos pos(LatitudeSample latitude) {
            return new BlockPos(0, y, latitude.z());
        }
    }

    private record SeasonSample(String name, Float seasonFactor, Integer subSeasonOrdinal) {
    }

    private record TimeSample(String name, long dayTime) {
    }

    private record LatitudeSample(String name, int z) {
    }

    private record WeatherSample(String name, ClimateSnapshot.WeatherState weatherState) {
    }

    private record ColdSweatOutdoorConfig(
            Map<String, BiomeTemperatureRange> biomeTemperatures,
            Map<String, Double> seasonOffsetsC,
            double rainOffsetC
    ) {
        static ColdSweatOutdoorConfig load() {
            Path path = FMLPaths.CONFIGDIR.get().resolve("coldsweat").resolve("world.toml");
            if (!Files.exists(path)) return new ColdSweatOutdoorConfig(Map.of(), Map.of(), 0.0D);

            Map<String, BiomeTemperatureRange> biomes = new HashMap<>();
            Map<String, Double> seasons = new HashMap<>();
            double rainOffsetC = 0.0D;
            boolean readingBiomeTemperatures = false;

            try {
                for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String line = rawLine.trim();
                    if (line.startsWith("\"Biome Temperatures\"")) {
                        readingBiomeTemperatures = true;
                        continue;
                    }
                    if (readingBiomeTemperatures && line.startsWith("]")) {
                        readingBiomeTemperatures = false;
                        continue;
                    }
                    if (readingBiomeTemperatures && line.startsWith("[\"")) {
                        parseBiomeTemperature(line).ifPresent(range -> biomes.put(range.biome(), range));
                        continue;
                    }

                    parseSeasonOffset(line, "Spring").ifPresent(value -> seasons.put("spring", value));
                    parseSeasonOffset(line, "Summer").ifPresent(value -> seasons.put("summer", value));
                    parseSeasonOffset(line, "Autumn").ifPresent(value -> seasons.put("autumn", value));
                    parseSeasonOffset(line, "Winter").ifPresent(value -> seasons.put("winter", value));
                    if (line.startsWith("\"Shade Temperature Offset\"")) {
                        List<String> values = tomlArrayValues(line);
                        if (!values.isEmpty()) {
                            String unit = values.size() > 1 ? values.get(1) : "mc";
                            rainOffsetC = temperatureToC(parseDouble(values.getFirst()), unit, false);
                        }
                    }
                }
            } catch (IOException ignored) {
                return new ColdSweatOutdoorConfig(Map.of(), Map.of(), 0.0D);
            }
            return new ColdSweatOutdoorConfig(Map.copyOf(biomes), Map.copyOf(seasons), rainOffsetC);
        }

        OutdoorTemperatureRangeC outdoorC(String biome, String season, boolean rainCooling) {
            BiomeTemperatureRange range = biomeTemperatures.get(biome);
            if (range == null) return null;
            double offset = seasonOffsetsC.getOrDefault(season, 0.0D) + (rainCooling ? rainOffsetC : 0.0D);
            return new OutdoorTemperatureRangeC(range.lowC() + offset, range.highC() + offset);
        }

        private static java.util.Optional<BiomeTemperatureRange> parseBiomeTemperature(String line) {
            List<String> values = tomlArrayValues(line);
            if (values.size() < 3 || "disable".equals(values.get(1))) return java.util.Optional.empty();
            String unit = values.size() > 3 ? values.get(3) : "mc";
            double lowC = temperatureToC(parseDouble(values.get(1)), unit, true);
            double highC = temperatureToC(parseDouble(values.get(2)), unit, true);
            return java.util.Optional.of(new BiomeTemperatureRange(values.getFirst(), lowC, highC));
        }

        private static java.util.Optional<Double> parseSeasonOffset(String line, String season) {
            if (!line.startsWith(season + " = ")) return java.util.Optional.empty();
            List<String> values = tomlArrayValues(line);
            if (values.size() < 2) return java.util.Optional.empty();
            String unit = values.size() > 3 ? values.get(3) : "mc";
            return java.util.Optional.of(temperatureToC(parseDouble(values.get(1)), unit, false));
        }

        private static List<String> tomlArrayValues(String line) {
            int start = line.indexOf('[');
            int end = line.lastIndexOf(']');
            if (start < 0 || end <= start) return List.of();
            String body = line.substring(start + 1, end);
            List<String> values = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '"') {
                    quoted = !quoted;
                    continue;
                }
                if (c == ',' && !quoted) {
                    values.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            values.add(current.toString().trim());
            return values;
        }

        private static double parseDouble(String value) {
            return Double.parseDouble(value.trim());
        }

        private static double temperatureToC(double value, String unit, boolean absolute) {
            return switch (unit.toLowerCase(Locale.ROOT)) {
                case "f" -> absolute ? (value - 32.0D) / 1.8D : value / 1.8D;
                case "c" -> value;
                default -> value * 25.0D;
            };
        }
    }

    private record BiomeTemperatureRange(String biome, double lowC, double highC) {
        double midpointC() {
            return (lowC + highC) / 2.0D;
        }
    }

    private record OutdoorTemperatureRangeC(double nightC, double noonC) {
        double midpointC() {
            return (nightC + noonC) / 2.0D;
        }
    }

    private record SourceOrderConfig(List<SourceOrderTier> tiers) {
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
                                JsonObject object = element.getAsJsonObject();
                                loaded.add(new SourceOrderTier(
                                        string(object, "id"),
                                        integer(object, "phase"),
                                        strings(object, "biomes"),
                                        string(object, "notes")
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

    private record SourceOrderTier(String id, int phase, List<String> biomes, String notes) {
        boolean matchesBiome(String biome, List<String> tags) {
            for (String matcher : biomes) {
                if (matcher.startsWith("#") && tags.contains(matcher.substring(1))) return true;
                if (matcher.endsWith(":*") && biome.startsWith(matcher.substring(0, matcher.length() - 1))) return true;
                if (matcher.equals(biome)) return true;
            }
            return false;
        }
    }

    private record PhaseEntityGateConfig(boolean iwCoreLoaded, int ruleCount, List<PhaseEntityGateRule> rules) {
        static PhaseEntityGateConfig load(MinecraftServer server) {
            boolean iwCoreLoaded = ModList.get().isLoaded("immersivewilderness");
            if (!iwCoreLoaded) return new PhaseEntityGateConfig(false, 0, List.of());

            List<PhaseEntityGateRule> loaded = new ArrayList<>();
            var resources = server.getResourceManager().listResources("phase_gates", location -> location.getPath().endsWith(".json"));
            resources.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .forEach(entry -> {
                        try (Reader reader = entry.getValue().openAsReader()) {
                            JsonElement parsed = JsonParser.parseReader(reader);
                            if (!parsed.isJsonObject()) return;
                            JsonArray rules = parsed.getAsJsonObject().getAsJsonArray("rules");
                            if (rules == null) return;
                            for (JsonElement ruleElement : rules) {
                                if (!ruleElement.isJsonObject()) continue;
                                PhaseEntityGateRule rule = PhaseEntityGateRule.from(ruleElement.getAsJsonObject());
                                if (rule.blocksAnything()) loaded.add(rule);
                            }
                        } catch (IOException | JsonParseException | IllegalStateException e) {
                            ArcticNights.LOGGER.warn("Unable to load IW Core phase entity gates for climate spawn matrix from {}", entry.getKey(), e);
                        }
                    });
            return new PhaseEntityGateConfig(true, loaded.size(), List.copyOf(loaded));
        }

        boolean blocks(String entityId, int phase) {
            for (PhaseEntityGateRule rule : rules) {
                if (rule.activeAt(phase) && rule.blocks(entityId)) return true;
            }
            return false;
        }

        String summary() {
            if (!iwCoreLoaded) return "IW Core not loaded";
            if (ruleCount == 0) return "IW Core loaded, no phase entity gates found";
            return "IW Core loaded, " + ruleCount + " phase entity gate rule(s)";
        }
    }

    private record PhaseEntityGateRule(
            Integer minPhase,
            Integer maxPhase,
            EntityFilter filter
    ) {
        static PhaseEntityGateRule from(JsonObject rule) {
            return new PhaseEntityGateRule(
                    intValue(rule.get("minPhase")),
                    intValue(rule.get("maxPhase")),
                    EntityFilter.from(rule)
            );
        }

        boolean activeAt(int phase) {
            return (minPhase == null || phase >= minPhase) && (maxPhase == null || phase <= maxPhase);
        }

        boolean blocksAnything() {
            return filter.blocksAnything();
        }

        boolean blocks(String entityId) {
            return filter.blocks(entityId);
        }
    }

    private record EntityFilter(
            List<String> blockEntityNamespaces,
            List<String> blockEntityTypes,
            List<WildcardPattern> blockEntityPatterns,
            List<String> allowEntityTypes
    ) {
        static EntityFilter from(JsonObject rule) {
            return new EntityFilter(
                    strings(rule.get("blockEntityNamespaces")),
                    strings(rule.get("blockEntityTypes")),
                    wildcardPatterns(rule.get("blockEntityPatterns")),
                    strings(rule.get("allowEntityTypes"))
            );
        }

        boolean blocksAnything() {
            return !blockEntityNamespaces.isEmpty() || !blockEntityTypes.isEmpty() || !blockEntityPatterns.isEmpty();
        }

        boolean blocks(String entityId) {
            if (allowEntityTypes.contains(entityId)) return false;
            if (blockEntityTypes.contains(entityId)) return true;
            int separator = entityId.indexOf(':');
            if (separator > 0 && blockEntityNamespaces.contains(entityId.substring(0, separator))) return true;
            for (WildcardPattern pattern : blockEntityPatterns) {
                if (pattern.matches(entityId)) return true;
            }
            return false;
        }
    }

    private static List<WildcardPattern> wildcardPatterns(JsonElement element) {
        return strings(element).stream().map(WildcardPattern::new).toList();
    }

    private static final class WildcardPattern {
        private final String raw;
        private final Pattern pattern;

        WildcardPattern(String raw) {
            this.raw = raw;
            this.pattern = compile(raw);
        }

        boolean matches(String value) {
            if (raw.indexOf('*') < 0) return value.contains(raw);
            return pattern.matcher(value).matches();
        }

        private static Pattern compile(String raw) {
            StringBuilder regex = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '*') regex.append(".*");
                else regex.append(Pattern.quote(String.valueOf(c)));
            }
            try {
                return Pattern.compile(regex.toString());
            } catch (PatternSyntaxException e) {
                ArcticNights.LOGGER.warn("Ignoring invalid IW Core phase entity gate pattern {}", raw, e);
                return Pattern.compile("$a");
            }
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : -1;
    }

    private static Integer intValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt()
                : null;
    }

    private static List<String> strings(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonPrimitive()) result.add(element.getAsString());
        return List.copyOf(result);
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) result.add(child.getAsString());
        }
        return List.copyOf(result);
    }

    public record ClimateAuditResult(ClimateAuditReport report, Path csv, Path markdown) {
    }

    public record ClimateAuditReport(
            String generatedAt,
            long durationMillis,
            int biomeCount,
            int rowCount,
            int flaggedRowCount,
            int referenceWarningRowCount,
            int hypotheticalWarningRowCount,
            Map<String, Long> rowsByPhase,
            Map<String, Long> rowsByArchetype,
            Map<String, Long> flags,
            Map<String, Long> referenceWarnings,
            Map<String, Long> hypotheticalWarnings,
            List<ClimateAuditRow> rows
    ) {
    }

    public record ClimateAuditRow(
            String biome,
            Integer phase,
            String tier,
            String archetype,
            String season,
            String weather,
            String time,
            String latitude,
            String sample,
            int x,
            int y,
            int z,
            boolean exposed,
            List<String> climateTags,
            float minecraftTemperature,
            double estimatedCelsius,
            Double coldSweatNightC,
            Double coldSweatNoonC,
            Double coldSweatOutdoorC,
            boolean rainCooling,
            String precipitation,
            String snowBehavior,
            float undeadFactor,
            float creeperFactor,
            List<String> flags
    ) {
    }

    private record SpawnEntry(
            String id,
            EntityType<?> type,
            int weight,
            int minCount,
            int maxCount,
            float factor
    ) {
        SpawnEntry withFactor(float factor) {
            return new SpawnEntry(id, type, weight, minCount, maxCount, factor);
        }
    }

    private record BiomeSpawnProfile(String biome, int phase, String archetype, String clearProfile, String rainProfile, String thunderProfile) {
    }

    public record SpawnMatrixResult(SpawnMatrixReport report, Path csv, Path markdown) {
    }

    public record SpawnMatrixReport(
            String generatedAt,
            long durationMillis,
            int biomeCount,
            int rowCount,
            int climateModifiedRowCount,
            int packGateModifiedRowCount,
            String packGateSource,
            Map<String, Long> rowsByPhase,
            Map<String, Long> rowsByArchetype,
            Map<String, Long> rowsByExpectedFamilies,
            Map<String, Long> rowsByPackExpectedFamilies,
            List<SpawnMatrixRow> rows
    ) {
    }

    public record SpawnMatrixRow(
            String biome,
            int phase,
            String tier,
            String archetype,
            String subseason,
            String weather,
            String latitude,
            String sample,
            String time,
            int x,
            int y,
            int z,
            float minecraftTemperature,
            double estimatedCelsius,
            float spawnTemperature,
            double spawnEstimatedCelsius,
            boolean rainCooling,
            String precipitation,
            String snowBehavior,
            float undeadFactor,
            float creeperFactor,
            float spiderFactor,
            String baseMonsters,
            String expectedMonsters,
            String removedMonsters,
            String expectedFamilies,
            String packExpectedMonsters,
            String packRemovedMonsters,
            String packExpectedFamilies,
            boolean packGateModified,
            boolean climateModified
    ) {
    }
}

package com.judiraal.arcticnights.util;

import com.judiraal.arcticnights.ArcticNights;
import com.judiraal.arcticnights.ArcticNightsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ClimateAuditReporter {
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
        Map<String, List<String>> tagsByBiome = BiomeTagIndex.byBiome(server);
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
                    String archetype = BiomeArchetypes.classify(biomeId, tags);
                    List<Sample> samples = relevantSamples(archetype);
                    for (SeasonSample season : seasonSamples()) {
                        for (WeatherSample weather : WEATHER_SAMPLES) {
                            for (TimeSample time : TIME_SAMPLES) {
                                for (LatitudeSample latitude : latitudeSamples()) {
                                    for (Sample sample : samples) {
                                        BlockPos pos = sample.pos(latitude);
                                        ClimateSnapshot snapshot = ClimateService.auditSnapshot(level, holder, pos, sample.exposedToSky(), season.seasonFactor(), weather.weatherState(), time.dayTime());
                                        rows.add(row(biomeId, tags, holder, tier, archetype, season, weather, time, latitude, sample, snapshot, coldSweat));
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
                flagRowCount(rows, ClimateAuditFlags::isPrimaryFlag),
                flagRowCount(rows, ClimateAuditFlags::isReferenceFlag),
                flagRowCount(rows, ClimateAuditFlags::isHypotheticalLatitudeFlag),
                countBy(rows, row -> row.phase() == null ? "unassigned" : "phase_" + row.phase()),
                countBy(rows, ClimateAuditRow::archetype),
                flagCounts(rows, ClimateAuditFlags::isPrimaryFlag),
                flagCounts(rows, ClimateAuditFlags::isReferenceFlag),
                flagCounts(rows, ClimateAuditFlags::isHypotheticalLatitudeFlag),
                rows
        );

        Files.createDirectories(outputDir);
        Path csv = outputDir.resolve("climate-audit.csv");
        Path markdown = outputDir.resolve("climate-audit.md");
        Files.writeString(csv, ClimateAuditReportFormatter.toCsv(report), StandardCharsets.UTF_8);
        Files.writeString(markdown, ClimateAuditReportFormatter.toMarkdown(report), StandardCharsets.UTF_8);
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
        Map<String, List<String>> tagsByBiome = BiomeTagIndex.byBiome(server);
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
                    String archetype = BiomeArchetypes.classify(biomeId, tags);
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
                countBy(rows, row -> SpawnMatrixReportFormatter.familyCode(row.expectedFamilies())),
                countBy(rows, row -> SpawnMatrixReportFormatter.familyCode(row.packExpectedFamilies())),
                rows
        );

        Files.createDirectories(outputDir);
        Path csv = outputDir.resolve("spawn-matrix.csv");
        Path markdown = outputDir.resolve("spawn-matrix.md");
        Files.writeString(csv, SpawnMatrixReportFormatter.toCsv(report), StandardCharsets.UTF_8);
        Files.writeString(markdown, SpawnMatrixReportFormatter.toMarkdown(report), StandardCharsets.UTF_8);
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

    private static ClimateAuditRow row(String biomeId, List<String> tags, Holder<Biome> biome, SourceOrderTier tier, String archetype, SeasonSample season, WeatherSample weather, TimeSample time, LatitudeSample latitude, Sample sample, ClimateSnapshot snapshot, ColdSweatOutdoorConfig coldSweat) {
        OutdoorTemperatureRangeC coldSweatOutdoor = coldSweat.outdoorC(biomeId, season.name(), snapshot.rainCooling());
        List<String> flags = ClimateAuditFlags.rowFlags(tags, archetype, season.name(), weather.name(), sample.exposedToSky(), snapshot, coldSweatOutdoor);
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
                BiomeArchetypes.climateTags(tags),
                snapshot.minecraftTemperature(),
                snapshot.estimatedCelsius(),
                coldSweatOutdoor == null ? null : coldSweatOutdoor.nightC(),
                coldSweatOutdoor == null ? null : coldSweatOutdoor.noonC(),
                coldSweatOutdoor == null ? null : coldSweatOutdoor.midpointC(),
                snapshot.rainCooling(),
                snapshot.precipitationKind().name().toLowerCase(),
                snapshot.snowBehavior().name().toLowerCase(),
                SpawnEcology.undeadFactor(biome, spawnTemperature(snapshot), snapshot.minecraftTemperature(), sample.caveFactor(), snapshot.rainCooling()),
                SpawnEcology.creeperFactor(biome, snapshot, sample.caveFactor()),
                flags
        );
    }

    private static SpawnMatrixRow spawnRow(String biomeId, List<String> tags, Holder<Biome> biome, SourceOrderTier tier, String archetype, SeasonSample season, WeatherSample weather, LatitudeSample latitude, Sample sample, TimeSample time, ClimateSnapshot snapshot, PhaseEntityGateConfig phaseEntityGates) {
        float spawnTemperature = spawnTemperature(snapshot);
        float autumnProgressionFactor = autumnProgressionFactor(season.subSeasonOrdinal());
        float undeadFactor = SpawnEcology.undeadFactor(biome, spawnTemperature, snapshot.clearOutdoorMinecraftTemperature(), 0.0F, snapshot.rainCooling());
        float creeperFactor = SpawnEcology.creeperFactor(biome, snapshot, 0.0F);
        float spiderFactor = SpawnEcology.spiderFactor(autumnProgressionFactor, snapshot, 0.0F);
        BlockPos pos = sample.pos(latitude);
        List<SpawnEntry> baseEntries = baseMonsterEntries(biome);
        List<SpawnEntry> expectedEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();
        boolean climateModified = false;
        for (SpawnEntry entry : baseEntries) {
            float factor = SpawnEcology.spawnFactor(entry.type(), biome, spawnTemperature, snapshot, autumnProgressionFactor, 0.0F);
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

    private static float spawnTemperature(ClimateSnapshot snapshot) {
        return snapshot.outdoorMinecraftTemperature();
    }

    private static float autumnProgressionFactor(Integer subSeasonOrdinal) {
        if (subSeasonOrdinal == null) return 0.0F;
        return SpawnEcology.autumnProgressionFactorForSubSeason(subSeasonOrdinal);
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
                        + AuditFormat.format(entry.factor())
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

    private static <T> Map<String, Long> countBy(List<T> rows, Function<T, String> classifier) {
        return rows.stream().collect(Collectors.groupingBy(classifier, TreeMap::new, Collectors.counting()));
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
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

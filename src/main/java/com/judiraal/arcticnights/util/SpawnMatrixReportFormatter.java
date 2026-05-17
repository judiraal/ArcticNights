package com.judiraal.arcticnights.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

final class SpawnMatrixReportFormatter {
    private SpawnMatrixReportFormatter() {
    }

    static String toMarkdown(ClimateAuditReporter.SpawnMatrixReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Arctic Nights Spawn Matrix\n\n");
        sb.append("- Generated: ").append(report.generatedAt()).append('\n');
        sb.append("- Duration: ").append(report.durationMillis()).append(" ms\n");
        sb.append("- Biomes: ").append(report.biomeCount()).append('\n');
        sb.append("- Rows: ").append(report.rowCount()).append('\n');
        sb.append("- Climate-modified rows: ").append(report.climateModifiedRowCount()).append('\n');
        sb.append("- Pack-gate-modified rows: ").append(report.packGateModifiedRowCount()).append('\n');
        sb.append("- Pack gate source: ").append(AuditFormat.escape(report.packGateSource())).append("\n\n");
        AuditFormat.appendSummary(sb, "Rows By Phase", report.rowsByPhase());
        AuditFormat.appendSummary(sb, "Rows By Archetype", report.rowsByArchetype());
        AuditFormat.appendSummary(sb, "Arctic Nights Family Codes", report.rowsByExpectedFamilies());
        AuditFormat.appendSummary(sb, "Pack-Effective Family Codes", report.rowsByPackExpectedFamilies());
        appendTopSummary(sb, "Top Pack-Effective Entities", entityCounts(report.rows(), ClimateAuditReporter.SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Top Phase 0 Pack-Effective Entities", entityCountsForPhase(report.rows(), 0, ClimateAuditReporter.SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Top Phase 1 Pack-Effective Entities", entityCountsForPhase(report.rows(), 1, ClimateAuditReporter.SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Top Phase 2 Pack-Effective Entities", entityCountsForPhase(report.rows(), 2, ClimateAuditReporter.SpawnMatrixRow::packExpectedMonsters, true), 20);
        appendTopSummary(sb, "Pack-Removed Entities", entityCounts(report.rows(), ClimateAuditReporter.SpawnMatrixRow::packRemovedMonsters, false), 20);
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
                    .append(AuditFormat.escape(profile.archetype())).append(" | ")
                    .append(AuditFormat.escape(profile.clearProfile())).append(" | ")
                    .append(AuditFormat.escape(profile.rainProfile())).append(" | ")
                    .append(AuditFormat.escape(profile.thunderProfile())).append(" |\n");
        }
        return sb.toString();
    }

    static String toCsv(ClimateAuditReporter.SpawnMatrixReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("biome,phase,tier,archetype,subseason,weather,latitude,sample,time,x,y,z,minecraft_temperature,estimated_celsius,spawn_temperature,spawn_estimated_celsius,rain_cooling,precipitation,snow_behavior,undead_factor,creeper_factor,spider_factor,base_monsters,expected_monsters,removed_monsters,expected_families,pack_expected_monsters,pack_removed_monsters,pack_expected_families,pack_gate_modified,climate_modified\n");
        for (ClimateAuditReporter.SpawnMatrixRow row : report.rows()) {
            sb.append(AuditFormat.csv(row.biome())).append(',')
                    .append(row.phase()).append(',')
                    .append(AuditFormat.csv(row.tier())).append(',')
                    .append(AuditFormat.csv(row.archetype())).append(',')
                    .append(AuditFormat.csv(row.subseason())).append(',')
                    .append(AuditFormat.csv(row.weather())).append(',')
                    .append(AuditFormat.csv(row.latitude())).append(',')
                    .append(AuditFormat.csv(row.sample())).append(',')
                    .append(AuditFormat.csv(row.time())).append(',')
                    .append(row.x()).append(',')
                    .append(row.y()).append(',')
                    .append(row.z()).append(',')
                    .append(AuditFormat.format(row.minecraftTemperature())).append(',')
                    .append(AuditFormat.format(row.estimatedCelsius())).append(',')
                    .append(AuditFormat.format(row.spawnTemperature())).append(',')
                    .append(AuditFormat.format(row.spawnEstimatedCelsius())).append(',')
                    .append(row.rainCooling()).append(',')
                    .append(AuditFormat.csv(row.precipitation())).append(',')
                    .append(AuditFormat.csv(row.snowBehavior())).append(',')
                    .append(AuditFormat.format(row.undeadFactor())).append(',')
                    .append(AuditFormat.format(row.creeperFactor())).append(',')
                    .append(AuditFormat.format(row.spiderFactor())).append(',')
                    .append(AuditFormat.csv(row.baseMonsters())).append(',')
                    .append(AuditFormat.csv(row.expectedMonsters())).append(',')
                    .append(AuditFormat.csv(row.removedMonsters())).append(',')
                    .append(AuditFormat.csv(row.expectedFamilies())).append(',')
                    .append(AuditFormat.csv(row.packExpectedMonsters())).append(',')
                    .append(AuditFormat.csv(row.packRemovedMonsters())).append(',')
                    .append(AuditFormat.csv(row.packExpectedFamilies())).append(',')
                    .append(row.packGateModified()).append(',')
                    .append(row.climateModified()).append('\n');
        }
        return sb.toString();
    }

    static String familyCode(String families) {
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

    private static Map<String, Long> entityCounts(List<ClimateAuditReporter.SpawnMatrixRow> rows, Function<ClimateAuditReporter.SpawnMatrixRow, String> entries, boolean weightedEntries) {
        Map<String, Long> counts = new HashMap<>();
        for (ClimateAuditReporter.SpawnMatrixRow row : rows) {
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

    private static Map<String, Long> entityCountsForPhase(List<ClimateAuditReporter.SpawnMatrixRow> rows, int phase, Function<ClimateAuditReporter.SpawnMatrixRow, String> entries, boolean weightedEntries) {
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

    private static List<BiomeSpawnProfile> biomeSpawnProfiles(List<ClimateAuditReporter.SpawnMatrixRow> rows) {
        Map<String, List<ClimateAuditReporter.SpawnMatrixRow>> byBiome = rows.stream().collect(Collectors.groupingBy(ClimateAuditReporter.SpawnMatrixRow::biome, TreeMap::new, Collectors.toList()));
        List<BiomeSpawnProfile> profiles = new ArrayList<>();
        for (Map.Entry<String, List<ClimateAuditReporter.SpawnMatrixRow>> entry : byBiome.entrySet()) {
            List<ClimateAuditReporter.SpawnMatrixRow> biomeRows = entry.getValue();
            ClimateAuditReporter.SpawnMatrixRow first = biomeRows.getFirst();
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

    private static String weatherProfile(List<ClimateAuditReporter.SpawnMatrixRow> rows, String weather) {
        return rows.stream()
                .filter(row -> weather.equals(row.weather()))
                .filter(row -> "temperate_start".equals(row.latitude()))
                .filter(row -> "surface".equals(row.sample()))
                .filter(row -> "midnight".equals(row.time()))
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

    private record BiomeSpawnProfile(String biome, int phase, String archetype, String clearProfile, String rainProfile, String thunderProfile) {
    }
}

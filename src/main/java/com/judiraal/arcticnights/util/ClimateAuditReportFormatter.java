package com.judiraal.arcticnights.util;

import java.util.List;

final class ClimateAuditReportFormatter {
    private ClimateAuditReportFormatter() {
    }

    static String toMarkdown(ClimateAuditReporter.ClimateAuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Arctic Nights Climate Audit\n\n");
        sb.append("- Generated: ").append(report.generatedAt()).append('\n');
        sb.append("- Duration: ").append(report.durationMillis()).append(" ms\n");
        sb.append("- Biomes: ").append(report.biomeCount()).append('\n');
        sb.append("- Rows: ").append(report.rowCount()).append('\n');
        sb.append("- Runtime/Classification Flagged Rows: ").append(report.flaggedRowCount()).append('\n');
        sb.append("- Cold Sweat Static Reference Warning Rows: ").append(report.referenceWarningRowCount()).append('\n');
        sb.append("- Hypothetical Latitude Warning Rows: ").append(report.hypotheticalWarningRowCount()).append("\n\n");
        AuditFormat.appendSummary(sb, "Rows By Phase", report.rowsByPhase());
        AuditFormat.appendSummary(sb, "Rows By Archetype", report.rowsByArchetype());
        AuditFormat.appendSummary(sb, "Runtime And Classification Flags", report.flags());
        AuditFormat.appendSummary(sb, "Cold Sweat Static Reference Warnings", report.referenceWarnings());
        AuditFormat.appendSummary(sb, "Hypothetical Latitude Warnings", report.hypotheticalWarnings());
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
        for (ClimateAuditReporter.ClimateAuditRow row : report.rows()) {
            List<String> primaryFlags = row.flags().stream().filter(ClimateAuditFlags::isPrimaryFlag).toList();
            if (primaryFlags.isEmpty()) continue;
            sb.append("| `").append(row.biome()).append("` | ")
                    .append(row.phase() == null ? "" : row.phase()).append(" | ")
                    .append(AuditFormat.escape(row.tier())).append(" | ")
                    .append(AuditFormat.escape(row.archetype())).append(" | ")
                    .append(AuditFormat.escape(row.season())).append(" | ")
                    .append(AuditFormat.escape(row.weather())).append(" | ")
                    .append(AuditFormat.escape(row.time())).append(" | ")
                    .append(AuditFormat.escape(row.latitude())).append(" | ")
                    .append(AuditFormat.escape(row.sample())).append(" | ")
                    .append(row.z()).append(" | ")
                    .append(AuditFormat.format(row.minecraftTemperature())).append(" | ")
                    .append(AuditFormat.format(row.estimatedCelsius())).append(" | ")
                    .append(AuditFormat.formatNullable(row.coldSweatNightC())).append(" | ")
                    .append(AuditFormat.formatNullable(row.coldSweatNoonC())).append(" | ")
                    .append(AuditFormat.formatNullable(row.coldSweatOutdoorC())).append(" | ")
                    .append(row.precipitation()).append(" | ")
                    .append(row.snowBehavior()).append(" | ")
                    .append(AuditFormat.format(row.undeadFactor())).append(" | ")
                    .append(AuditFormat.format(row.creeperFactor())).append(" | ")
                    .append(AuditFormat.escape(primaryFlags.toString())).append(" |\n");
        }
        return sb.toString();
    }

    static String toCsv(ClimateAuditReporter.ClimateAuditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("biome,phase,tier,archetype,season,weather,time,latitude,sample,x,y,z,exposed,climate_tags,minecraft_temperature,estimated_celsius,cold_sweat_config_night_c,cold_sweat_config_noon_c,cold_sweat_config_outdoor_c,rain_cooling,precipitation,snow_behavior,undead_factor,creeper_factor,flags\n");
        for (ClimateAuditReporter.ClimateAuditRow row : report.rows()) {
            sb.append(AuditFormat.csv(row.biome())).append(',')
                    .append(row.phase() == null ? "" : row.phase()).append(',')
                    .append(AuditFormat.csv(row.tier())).append(',')
                    .append(AuditFormat.csv(row.archetype())).append(',')
                    .append(AuditFormat.csv(row.season())).append(',')
                    .append(AuditFormat.csv(row.weather())).append(',')
                    .append(AuditFormat.csv(row.time())).append(',')
                    .append(AuditFormat.csv(row.latitude())).append(',')
                    .append(AuditFormat.csv(row.sample())).append(',')
                    .append(row.x()).append(',')
                    .append(row.y()).append(',')
                    .append(row.z()).append(',')
                    .append(row.exposed()).append(',')
                    .append(AuditFormat.csv(row.climateTags().toString())).append(',')
                    .append(AuditFormat.format(row.minecraftTemperature())).append(',')
                    .append(AuditFormat.format(row.estimatedCelsius())).append(',')
                    .append(AuditFormat.formatNullable(row.coldSweatNightC())).append(',')
                    .append(AuditFormat.formatNullable(row.coldSweatNoonC())).append(',')
                    .append(AuditFormat.formatNullable(row.coldSweatOutdoorC())).append(',')
                    .append(row.rainCooling()).append(',')
                    .append(AuditFormat.csv(row.precipitation())).append(',')
                    .append(AuditFormat.csv(row.snowBehavior())).append(',')
                    .append(AuditFormat.format(row.undeadFactor())).append(',')
                    .append(AuditFormat.format(row.creeperFactor())).append(',')
                    .append(AuditFormat.csv(row.flags().toString())).append('\n');
        }
        return sb.toString();
    }
}

package com.judiraal.arcticnights.util;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record ColdSweatOutdoorConfig(
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

record BiomeTemperatureRange(String biome, double lowC, double highC) {
    double midpointC() {
        return (lowC + highC) / 2.0D;
    }
}

record OutdoorTemperatureRangeC(double nightC, double noonC) {
    double midpointC() {
        return (nightC + noonC) / 2.0D;
    }
}

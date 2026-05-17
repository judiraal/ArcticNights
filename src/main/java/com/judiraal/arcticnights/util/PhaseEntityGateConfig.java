package com.judiraal.arcticnights.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.judiraal.arcticnights.ArcticNights;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

record PhaseEntityGateConfig(boolean iwCoreLoaded, int ruleCount, List<PhaseEntityGateRule> rules) {
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

record PhaseEntityGateRule(
        Integer minPhase,
        Integer maxPhase,
        EntityFilter filter
) {
    static PhaseEntityGateRule from(JsonObject rule) {
        return new PhaseEntityGateRule(
                AuditJson.intValue(rule.get("minPhase")),
                AuditJson.intValue(rule.get("maxPhase")),
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

record EntityFilter(
        List<String> blockEntityNamespaces,
        List<String> blockEntityTypes,
        List<WildcardPattern> blockEntityPatterns,
        List<String> allowEntityTypes
) {
    static EntityFilter from(JsonObject rule) {
        return new EntityFilter(
                AuditJson.strings(rule.get("blockEntityNamespaces")),
                AuditJson.strings(rule.get("blockEntityTypes")),
                wildcardPatterns(rule.get("blockEntityPatterns")),
                AuditJson.strings(rule.get("allowEntityTypes"))
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

    private static List<WildcardPattern> wildcardPatterns(JsonElement element) {
        return AuditJson.strings(element).stream().map(WildcardPattern::new).toList();
    }
}

final class WildcardPattern {
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

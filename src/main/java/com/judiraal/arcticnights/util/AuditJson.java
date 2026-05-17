package com.judiraal.arcticnights.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class AuditJson {
    private AuditJson() {
    }

    static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : -1;
    }

    static Integer intValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsInt()
                : null;
    }

    static List<String> strings(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        if (array == null) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonPrimitive()) result.add(element.getAsString());
        return List.copyOf(result);
    }

    static List<String> strings(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) result.add(child.getAsString());
        }
        return List.copyOf(result);
    }
}

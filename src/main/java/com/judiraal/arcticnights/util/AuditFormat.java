package com.judiraal.arcticnights.util;

import java.util.Map;

final class AuditFormat {
    private AuditFormat() {
    }

    static void appendSummary(StringBuilder sb, String title, Map<String, Long> values) {
        sb.append("## ").append(title).append("\n\n");
        if (values.isEmpty()) {
            sb.append("_None._\n\n");
            return;
        }
        values.forEach((key, value) -> sb.append("- `").append(key).append("`: ").append(value).append('\n'));
        sb.append('\n');
    }

    static String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    static String formatNullable(Double value) {
        return value == null ? "" : format(value);
    }
}

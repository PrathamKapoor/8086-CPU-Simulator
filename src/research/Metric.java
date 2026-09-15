package research;

import debugger.Json;

/**
 * One named measurement with its precise definition attached — the
 * definition travels with the value so a report never separates a number
 * from what it means (Phase 6 Step 5).
 */
public record Metric(String name, String definition, MetricProvenance provenance, double value) {
    public String toJson() {
        return "{\"name\":" + Json.str(name) + ",\"definition\":" + Json.str(definition)
            + ",\"provenance\":\"" + provenance + "\",\"value\":" + jsonNumber(value) + "}";
    }

    private static String jsonNumber(double v) {
        if (Double.isNaN(v)) return "null";
        return v == Math.floor(v) && !Double.isInfinite(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    public String describe() {
        String v = provenance == MetricProvenance.UNAVAILABLE ? "n/a" : jsonNumber(value);
        return name + " = " + v + "  [" + provenance + "] " + definition;
    }
}

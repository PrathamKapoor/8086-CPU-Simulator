package research;

import debugger.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An evidence-oriented summary of a {@link Comparison} (Phase 6 Step 13).
 * Every sentence here is generated from an actual {@link Comparison.MetricDelta}
 * or {@link Comparison.ConfigChange} — there is no free-form prose generation
 * and no claim not backed by a specific, named, numeric delta.
 */
public record EvidenceReport(String headline, List<String> supportingFacts, List<Comparison.ConfigChange> configChanges) {
    private static final String PRIMARY_METRIC = "timing.totalCycles";

    public static EvidenceReport from(Comparison comparison) {
        Comparison.MetricDelta primary = comparison.metricDeltas().stream()
            .filter(d -> d.metric().equals(PRIMARY_METRIC)).findFirst().orElse(null);

        String headline;
        if (primary == null || primary.provenance() == MetricProvenance.UNAVAILABLE) {
            headline = "Total-cycle comparison unavailable for this pair.";
        } else if (primary.absoluteDelta() == 0) {
            headline = "Workload B took the same number of cycles as workload A (" + (long) primary.valueA() + ").";
        } else {
            String direction = primary.absoluteDelta() > 0 ? "more" : "fewer";
            String percent = primary.percentDelta() == null ? "" : String.format(" (%.1f%%)", Math.abs(primary.percentDelta()));
            headline = "Workload B required " + (long) Math.abs(primary.absoluteDelta()) + " " + direction
                + " cycles than workload A" + percent + ".";
        }

        List<String> facts = new ArrayList<>();
        comparison.changedMetrics().stream()
            .filter(d -> !d.metric().equals(PRIMARY_METRIC))
            .sorted(Comparator.comparingDouble((Comparison.MetricDelta d) -> Math.abs(d.absoluteDelta())).reversed())
            .forEach(d -> facts.add(formatDelta(d)));

        return new EvidenceReport(headline, List.copyOf(facts), comparison.configChanges());
    }

    private static String formatDelta(Comparison.MetricDelta d) {
        String sign = d.absoluteDelta() > 0 ? "+" : "";
        String value = d.absoluteDelta() == Math.floor(d.absoluteDelta()) ? String.valueOf((long) d.absoluteDelta()) : String.valueOf(d.absoluteDelta());
        return sign + value + " " + d.metric();
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(headline);
        if (!supportingFacts.isEmpty()) {
            out.add("The measured change came from:");
            for (String f : supportingFacts) out.add("- " + f);
        }
        if (!configChanges.isEmpty()) {
            out.add("Configuration changed:");
            for (var c : configChanges) out.add("- " + c.field() + ": " + c.valueA() + " -> " + c.valueB());
        }
        return out;
    }

    public String toJson() {
        return "{\"headline\":" + Json.str(headline)
            + ",\"supportingFacts\":" + Json.array(supportingFacts, Json::str)
            + ",\"configChanges\":" + Json.array(configChanges, Comparison.ConfigChange::toJson) + "}";
    }
}

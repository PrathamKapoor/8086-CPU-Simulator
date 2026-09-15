package research;

import debugger.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * A deterministic A/B comparison between two runs (Phase 6 Step 7). Reports
 * exactly what changed in configuration and every metric delta — never a
 * manufactured significance claim; these are two deterministic simulator
 * executions, not a statistical sample.
 */
public record Comparison(ExperimentRun runA, ExperimentRun runB, List<ConfigChange> configChanges, List<MetricDelta> metricDeltas) {

    public record ConfigChange(String field, String valueA, String valueB) {
        public String toJson() {
            return "{\"field\":" + Json.str(field) + ",\"valueA\":" + Json.str(valueA) + ",\"valueB\":" + Json.str(valueB) + "}";
        }
    }

    public record MetricDelta(String metric, MetricProvenance provenance, double valueA, double valueB,
                              double absoluteDelta, Double percentDelta) {
        public String toJson() {
            return "{\"metric\":" + Json.str(metric) + ",\"provenance\":\"" + provenance + "\",\"valueA\":" + num(valueA)
                + ",\"valueB\":" + num(valueB) + ",\"absoluteDelta\":" + num(absoluteDelta)
                + ",\"percentDelta\":" + (percentDelta == null ? "null" : num(percentDelta)) + "}";
        }
        private static String num(double v) { return Double.isNaN(v) ? "null" : String.valueOf(v); }
    }

    public static Comparison of(ExperimentRun a, ExperimentRun b) {
        List<ConfigChange> configChanges = new ArrayList<>();
        addIfDifferent(configChanges, "timingModel", a.experiment().configuration().timingModel().toString(), b.experiment().configuration().timingModel().toString());
        addIfDifferent(configChanges, "tracingEnabled", String.valueOf(a.experiment().configuration().tracingEnabled()), String.valueOf(b.experiment().configuration().tracingEnabled()));
        addIfDifferent(configChanges, "debuggerEnabled", String.valueOf(a.experiment().configuration().debuggerEnabled()), String.valueOf(b.experiment().configuration().debuggerEnabled()));
        addIfDifferent(configChanges, "workloadKind", a.experiment().workload().kind().toString(), b.experiment().workload().kind().toString());
        addIfDifferent(configChanges, "workloadContent", a.experiment().workload().contentFingerprint(), b.experiment().workload().contentFingerprint());

        List<MetricDelta> deltas = new ArrayList<>();
        for (Metric ma : a.metrics().all()) {
            Metric mb = b.metrics().get(ma.name());
            if (mb == null) continue;
            if (ma.provenance() == MetricProvenance.UNAVAILABLE || mb.provenance() == MetricProvenance.UNAVAILABLE) {
                deltas.add(new MetricDelta(ma.name(), MetricProvenance.UNAVAILABLE, Double.NaN, Double.NaN, Double.NaN, null));
                continue;
            }
            double delta = mb.value() - ma.value();
            Double percent = ma.value() == 0 ? null : (delta / ma.value()) * 100.0;
            deltas.add(new MetricDelta(ma.name(), ma.provenance(), ma.value(), mb.value(), delta, percent));
        }
        return new Comparison(a, b, List.copyOf(configChanges), List.copyOf(deltas));
    }

    private static void addIfDifferent(List<ConfigChange> list, String field, String a, String b) {
        if (!a.equals(b)) list.add(new ConfigChange(field, a, b));
    }

    /** Only the metrics that actually changed — the signal, not the whole table. */
    public List<MetricDelta> changedMetrics() {
        return metricDeltas.stream().filter(d -> d.provenance() != MetricProvenance.UNAVAILABLE && d.absoluteDelta() != 0).toList();
    }

    public String toJson() {
        return "{\"runA\":" + Json.str(runA.experiment().id()) + ",\"runB\":" + Json.str(runB.experiment().id())
            + ",\"configChanges\":" + Json.array(configChanges, ConfigChange::toJson)
            + ",\"metricDeltas\":" + Json.array(metricDeltas, MetricDelta::toJson) + "}";
    }
}

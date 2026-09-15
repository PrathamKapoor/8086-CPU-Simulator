package research;

import debugger.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of running one {@link Experiment} N times (Phase 6 Step 8).
 * This simulator has no randomness anywhere in its execution path (see the
 * audit note), so a deterministic experiment run N times must produce N
 * byte-for-byte identical {@link ExperimentRun#resultHash()} values — this
 * type both verifies that and reports min/max/mean/median/spread per metric
 * for completeness, labeled clearly as simulator-cycle counts, not physical
 * hardware timing measurements.
 */
public final class RepetitionResult {
    private final Experiment experiment;
    private final List<ExperimentRun> runs;
    private final boolean allIdentical;
    private final Map<String, Stats> statsByMetric;

    private RepetitionResult(Experiment experiment, List<ExperimentRun> runs, boolean allIdentical, Map<String, Stats> statsByMetric) {
        this.experiment = experiment;
        this.runs = List.copyOf(runs);
        this.allIdentical = allIdentical;
        this.statsByMetric = Map.copyOf(statsByMetric);
    }

    public static RepetitionResult run(Experiment experiment, int times) {
        if (times <= 0) throw new IllegalArgumentException("times must be positive");
        List<ExperimentRun> runs = new ArrayList<>(times);
        for (int i = 0; i < times; i++) runs.add(ExperimentRunner.run(experiment));

        String firstHash = runs.get(0).resultHash();
        boolean identical = runs.stream().allMatch(r -> r.resultHash().equals(firstHash));

        Map<String, Stats> stats = new LinkedHashMap<>();
        for (Metric m : runs.get(0).metrics().all()) {
            if (m.provenance() == MetricProvenance.UNAVAILABLE) continue;
            List<Double> values = runs.stream().map(r -> r.metrics().get(m.name()).value()).toList();
            stats.put(m.name(), Stats.of(values));
        }
        return new RepetitionResult(experiment, runs, identical, stats);
    }

    public Experiment experiment() { return experiment; }
    public List<ExperimentRun> runs() { return runs; }
    public boolean allIdentical() { return allIdentical; }
    public Map<String, Stats> statsByMetric() { return statsByMetric; }

    public String toJson() {
        StringBuilder statsJson = new StringBuilder("{");
        boolean first = true;
        for (var e : statsByMetric.entrySet()) {
            if (!first) statsJson.append(',');
            first = false;
            statsJson.append(Json.str(e.getKey())).append(':').append(e.getValue().toJson());
        }
        statsJson.append('}');
        return "{\"experimentId\":" + Json.str(experiment.id())
            + ",\"repetitions\":" + runs.size()
            + ",\"allIdentical\":" + allIdentical
            + ",\"resultHashes\":" + Json.array(runs.stream().map(ExperimentRun::resultHash).toList(), Json::str)
            + ",\"statsByMetric\":" + statsJson + "}";
    }
}

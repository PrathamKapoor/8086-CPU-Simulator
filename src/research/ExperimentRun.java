package research;

import debugger.Json;

import java.time.Instant;

/**
 * The complete, deterministic result of executing one {@link Experiment}
 * once. {@code executedAt} is informational metadata only (Step 2's
 * "timestamp metadata where useful") — it is deliberately excluded from
 * {@link #resultHash} (see {@link ResultHasher}).
 */
public record ExperimentRun(
    Experiment experiment,
    ArchitecturalState finalState,
    MetricSet metrics,
    boolean passed,
    String resultHash,
    Instant executedAt,
    String simulatorVersion
) {
    public String toJson() {
        return "{\"experimentId\":" + Json.str(experiment.id())
            + ",\"description\":" + Json.str(experiment.description())
            + ",\"configuration\":" + Json.str(experiment.configuration().describe())
            + ",\"finalState\":" + finalState.toJson()
            + ",\"metrics\":" + metrics.toJson()
            + ",\"passed\":" + passed
            + ",\"resultHash\":" + Json.str(resultHash)
            + ",\"executedAt\":" + Json.str(executedAt.toString())
            + ",\"simulatorVersion\":" + Json.str(simulatorVersion) + "}";
    }
}

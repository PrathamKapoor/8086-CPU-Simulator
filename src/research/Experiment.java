package research;

/**
 * A stable, named, reproducible experiment declaration — the thing you can
 * point at by ID, re-run, export, and compare against another. Pure data;
 * see {@link ExperimentRunner} for execution.
 */
public record Experiment(String id, String description, Workload workload, ExperimentConfiguration configuration) {
    public Experiment {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("experiment id must not be blank");
        if (workload == null) throw new IllegalArgumentException("workload must not be null");
        if (configuration == null) throw new IllegalArgumentException("configuration must not be null");
    }

    public Experiment withConfiguration(ExperimentConfiguration newConfiguration) {
        return new Experiment(id, description, workload, newConfiguration);
    }
}

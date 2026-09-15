package research;

import debugger.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * The structured result of running every combination of a workload axis and
 * a configuration axis (Phase 6 Step 15) — e.g. 3 workloads x 3 timing
 * configurations = 9 runs. No GUI is required to produce or consume this;
 * {@code simulator.ResearchCli}'s {@code experiment batch} command prints it
 * as a table, and {@code toJson()} gives the same data as a JSON matrix.
 */
public record BatchMatrix(List<String> workloadAxisLabels, List<String> configurationAxisLabels, List<Cell> cells) {
    public record Cell(String workloadLabel, String configurationLabel, ExperimentRun run) {
        public String toJson() {
            return "{\"workload\":" + Json.str(workloadLabel) + ",\"configuration\":" + Json.str(configurationLabel)
                + ",\"run\":" + run.toJson() + "}";
        }
    }

    public static BatchMatrix run(List<Workload> workloads, List<Labeled<ExperimentConfiguration>> configurations) {
        List<String> workloadLabels = workloads.stream().map(Workload::name).toList();
        List<String> configLabels = configurations.stream().map(Labeled::label).toList();
        List<Cell> cells = new ArrayList<>();
        for (Workload workload : workloads) {
            for (Labeled<ExperimentConfiguration> config : configurations) {
                Experiment experiment = new Experiment(workload.name() + "@" + config.label(), workload.description(), workload, config.value());
                cells.add(new Cell(workload.name(), config.label(), ExperimentRunner.run(experiment)));
            }
        }
        return new BatchMatrix(workloadLabels, configLabels, List.copyOf(cells));
    }

    public record Labeled<T>(String label, T value) { }

    /** One metric's value across the whole matrix, as workload -> configuration -> value, for a table view. */
    public double[][] metricGrid(String metricName) {
        double[][] grid = new double[workloadAxisLabels.size()][configurationAxisLabels.size()];
        for (Cell cell : cells) {
            int row = workloadAxisLabels.indexOf(cell.workloadLabel());
            int col = configurationAxisLabels.indexOf(cell.configurationLabel());
            Metric m = cell.run().metrics().get(metricName);
            grid[row][col] = m == null || m.provenance() == MetricProvenance.UNAVAILABLE ? Double.NaN : m.value();
        }
        return grid;
    }

    public String toJson() {
        return "{\"workloadAxis\":" + Json.array(workloadAxisLabels, Json::str)
            + ",\"configurationAxis\":" + Json.array(configurationAxisLabels, Json::str)
            + ",\"cells\":" + Json.array(cells, Cell::toJson) + "}";
    }
}

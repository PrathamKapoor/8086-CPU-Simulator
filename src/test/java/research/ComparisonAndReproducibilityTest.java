package research;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonAndReproducibilityTest {
    private Experiment takenBranch() { return ExperimentCatalog.get("taken-branch"); }
    private Experiment notTakenBranch() { return ExperimentCatalog.get("not-taken-branch"); }

    @Test void comparisonIdentifiesChangedConfigurationAndMetricDeltas() {
        ExperimentRun a = ExperimentRunner.run(takenBranch());
        ExperimentRun b = ExperimentRunner.run(notTakenBranch());
        Comparison cmp = Comparison.of(a, b);

        assertTrue(cmp.configChanges().stream().anyMatch(c -> c.field().equals("workloadContent")));
        Comparison.MetricDelta takenDelta = cmp.metricDeltas().stream()
            .filter(d -> d.metric().equals("controlFlow.takenBranches")).findFirst().orElseThrow();
        assertEquals(1.0, takenDelta.valueA());
        assertEquals(0.0, takenDelta.valueB());
        assertEquals(-1.0, takenDelta.absoluteDelta());
    }

    @Test void evidenceReportIsBackedByActualDeltasNotProse() {
        ExperimentRun a = ExperimentRunner.run(takenBranch());
        ExperimentRun b = ExperimentRunner.run(notTakenBranch());
        Comparison cmp = Comparison.of(a, b);
        EvidenceReport report = EvidenceReport.from(cmp);

        assertNotNull(report.headline());
        // Every supporting fact must reference a real metric name that exists in the comparison.
        for (String fact : report.supportingFacts()) {
            String metricName = fact.replaceFirst("^[+-]?[0-9.]+ ", "");
            assertTrue(cmp.metricDeltas().stream().anyMatch(d -> d.metric().equals(metricName)),
                "fact references unknown metric: " + fact);
        }
    }

    @Test void identicalRunsProduceAnEmptyChangedMetricsList() {
        ExperimentRun a = ExperimentRunner.run(takenBranch());
        ExperimentRun b = ExperimentRunner.run(takenBranch());
        Comparison cmp = Comparison.of(a, b);
        assertTrue(cmp.changedMetrics().isEmpty());
        assertTrue(cmp.configChanges().isEmpty());
    }

    @Test void repetitionOfADeterministicExperimentProducesIdenticalHashesAndZeroSpread() {
        RepetitionResult result = RepetitionResult.run(ExperimentCatalog.get("loop-workload"), 5);
        assertTrue(result.allIdentical());
        assertEquals(5, result.runs().size());
        Stats cycleStats = result.statsByMetric().get("timing.totalCycles");
        assertEquals(0.0, cycleStats.spread());
        assertEquals(cycleStats.min(), cycleStats.max());
    }

    @Test void artifactExportImportRoundTripsAndVerifiesReproducible() {
        ExperimentArtifact original = ExperimentArtifact.capture(ExperimentCatalog.get("memory-heavy"));
        String json = original.toJson();
        ExperimentArtifact restored = ExperimentArtifact.fromJson(json);

        assertEquals(original.experiment().id(), restored.experiment().id());
        assertEquals(original.run().resultHash(), restored.run().resultHash());
        assertTrue(restored.verifyReproducible());
    }

    @Test void artifactRoundTripsAMachineCodeWorkload() {
        ExperimentArtifact original = ExperimentArtifact.capture(ExperimentCatalog.get("machine-code-representation"));
        ExperimentArtifact restored = ExperimentArtifact.fromJson(original.toJson());
        assertEquals(Workload.Kind.MACHINE_CODE, restored.experiment().workload().kind());
        assertTrue(restored.verifyReproducible());
    }

    @Test void batchMatrixProducesTheExpectedCartesianProductSize() {
        var workloads = List.of(
            Workload.fromSource("w1", "d", "MOV AX, 1\nHLT\n"),
            Workload.fromSource("w2", "d", "MOV AX, 2\nHLT\n"),
            Workload.fromSource("w3", "d", "MOV AX, 3\nHLT\n"));
        var configs = List.of(
            new BatchMatrix.Labeled<>("functional", ExperimentConfiguration.timed().withTimingModel(simulator.profiler.TimingModel.FUNCTIONAL)),
            new BatchMatrix.Labeled<>("simplified", ExperimentConfiguration.timed()),
            new BatchMatrix.Labeled<>("traced", ExperimentConfiguration.timed().withTracing()));

        BatchMatrix matrix = BatchMatrix.run(workloads, configs);
        assertEquals(9, matrix.cells().size());
        double[][] grid = matrix.metricGrid("execution.instructionsRetired");
        assertEquals(3, grid.length);
        assertEquals(3, grid[0].length);
        for (double[] row : grid) for (double v : row) assertEquals(2.0, v); // MOV + HLT = 2 instructions, regardless of config
    }
}

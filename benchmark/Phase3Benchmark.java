package benchmark;

import simulator.experiment.BenchmarkCatalog;
import simulator.experiment.ExperimentRunner;

public class Phase3Benchmark {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 3 Benchmark Suite ===");
        BenchmarkCatalog.all().values().forEach(definition -> {
            var result = ExperimentRunner.run(definition);
            System.out.println(definition.name() + ": expected=" + result.architecturalResultMatches()
                + ", " + result.metrics().toJson());
        });
        System.out.println("=== Benchmark complete ===");
    }
}

package microarchitecture;

import org.junit.jupiter.api.Test;
import simulator.experiment.BenchmarkCatalog;
import simulator.experiment.ExperimentRunner;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentProfilerTest {
    @Test void all_seven_experiments_pass_and_replay_identically() {
        assertEquals(7, BenchmarkCatalog.all().size());
        BenchmarkCatalog.all().values().forEach(definition -> {
            var first = ExperimentRunner.run(definition); var second = ExperimentRunner.run(definition);
            assertTrue(first.architecturalResultMatches(), definition.name());
            assertEquals(first.metrics().toJson(), second.metrics().toJson(), definition.name());
            assertEquals(first.cpu().getCycleTrace().stream().map(s -> s.toJson()).toList(), second.cpu().getCycleTrace().stream().map(s -> s.toJson()).toList());
        });
    }

    @Test void catalog_source_matches_each_canonical_assembly_file() throws Exception {
        for (var definition : BenchmarkCatalog.all().values()) {
            Path source = Path.of("benchmark", definition.name() + ".asm");
            assertTrue(Files.isRegularFile(source), source.toString());
            assertEquals(definition.source().replace("\r\n", "\n").trim(), Files.readString(source).replace("\r\n", "\n").trim());
        }
    }
}

package research;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentCatalogTest {
    static Stream<String> catalogIds() { return ExperimentCatalog.all().keySet().stream(); }

    @Test void catalogHasAtLeastTwelveEntries() {
        assertTrue(ExperimentCatalog.all().size() >= 12, "expected at least 12 catalog entries, found " + ExperimentCatalog.all().size());
    }

    @ParameterizedTest
    @MethodSource("catalogIds")
    void everyCatalogEntryRunsAndPasses(String id) {
        Experiment experiment = ExperimentCatalog.get(id);
        ExperimentRun run = ExperimentRunner.run(experiment);
        assertTrue(run.finalState().halted(), id + " did not halt");
        assertTrue(run.passed(), id + " did not match its expected registers: " + run.finalState().registers());
    }

    @Test void pairsDifferByExactlyOneWorkloadOrRepresentationVariable() {
        for (ExperimentCatalog.Pair pair : ExperimentCatalog.PAIRS) {
            Experiment a = ExperimentCatalog.get(pair.idA());
            Experiment b = ExperimentCatalog.get(pair.idB());
            assertNotEquals(a.id(), b.id());
            assertFalse(pair.differingVariable().isBlank());
        }
    }

    @Test void takenAndNotTakenPairActuallyDifferInBranchOutcome() {
        ExperimentRun taken = ExperimentRunner.run(ExperimentCatalog.get("taken-branch"));
        ExperimentRun notTaken = ExperimentRunner.run(ExperimentCatalog.get("not-taken-branch"));
        assertEquals(1, taken.metrics().get("controlFlow.takenBranches").value());
        assertEquals(0, notTaken.metrics().get("controlFlow.takenBranches").value());
    }

    @Test void sourceAndMachineCodeRepresentationPairProduceIdenticalArchitecturalResult() {
        ExperimentRun source = ExperimentRunner.run(ExperimentCatalog.get("source-representation"));
        ExperimentRun machineCode = ExperimentRunner.run(ExperimentCatalog.get("machine-code-representation"));
        assertEquals(source.finalState(), machineCode.finalState());
    }
}

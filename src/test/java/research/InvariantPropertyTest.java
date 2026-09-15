package research;

import cpu.CPU;
import cpu.biu.PrefetchQueue;
import cpu.microarchitecture.CycleSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Step 19: properties that must hold for every catalog experiment,
 * not just the ones pinned as golden values. These are structural
 * invariants of the measurement model itself (a count can't be negative,
 * a flush can't happen without a real control transfer, the queue can't
 * exceed its compile-time capacity) rather than assertions about any one
 * workload's specific behavior.
 */
class InvariantPropertyTest {
    static Stream<String> allCatalogIds() { return ExperimentCatalog.all().keySet().stream(); }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void countsAreNeverNegative(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        for (Metric m : run.metrics().all()) {
            if (m.provenance() == MetricProvenance.UNAVAILABLE) continue;
            assertTrue(m.value() >= 0, id + ": metric " + m.name() + " is negative: " + m.value());
        }
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void cyclesAreAtLeastAsManyAsInstructionsRetired(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        double instructions = run.metrics().get("execution.instructionsRetired").value();
        double cycles = run.metrics().get("execution.totalCycles").value();
        assertTrue(cycles >= instructions, id + ": totalCycles must be >= instructionsRetired");
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void microOpsExecutedIsAtLeastInstructionsRetired(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        double instructions = run.metrics().get("execution.instructionsRetired").value();
        double microOps = run.metrics().get("execution.microOpsExecuted").value();
        assertTrue(microOps >= instructions, id + ": every retired instruction executes at least one micro-op");
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void cpiIsOnlyDefinedWhenInstructionsRetiredIsPositive(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        double instructions = run.metrics().get("execution.instructionsRetired").value();
        Metric cpi = run.metrics().get("execution.cpi");
        assertTrue(instructions > 0, id + ": every catalog workload must retire at least one instruction");
        assertEquals(MetricProvenance.DERIVED, cpi.provenance());
        assertFalse(Double.isNaN(cpi.value()));
        assertFalse(Double.isInfinite(cpi.value()));
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void flushedBytesAreZeroWheneverThereAreNoFlushes(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        double flushes = run.metrics().get("controlFlow.queueFlushes").value();
        double flushedBytes = run.metrics().get("controlFlow.flushedBytes").value();
        if (flushes == 0) assertEquals(0, flushedBytes, id + ": no flush events but flushedBytes > 0");
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void everyHaltedRunProducesADeterministicNonEmptyHash(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        assertTrue(run.finalState().halted(), id + ": every catalog workload must halt");
        assertNotNull(run.resultHash());
        assertEquals(64, run.resultHash().length(), id + ": SHA-256 hex digest must be 64 characters");
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void identicalArtifactsProduceIdenticalResultHashes(String id) {
        ExperimentArtifact a = ExperimentArtifact.capture(ExperimentCatalog.get(id));
        ExperimentArtifact b = ExperimentArtifact.fromJson(a.toJson());
        assertEquals(a.run().resultHash(), b.run().resultHash(), id + ": round-tripping through JSON must not change the result");
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void repeatedRunsOfTheSameExperimentAreHashIdentical(String id) {
        RepetitionResult result = RepetitionResult.run(ExperimentCatalog.get(id), 3);
        assertTrue(result.allIdentical(), id + ": three repetitions of the same deterministic experiment must hash identically");
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void queueOccupancyNeverExceedsCompileTimeCapacity(String id) {
        Experiment e = ExperimentCatalog.get(id);
        CPU cpu = new CPU();
        cpu.setTimingModel(e.configuration().timingModel());
        if (e.workload().kind() == Workload.Kind.SOURCE) {
            cpu.loadProgram(new instruction.InstructionParser().parseProgram(e.workload().sourceAssembly()));
        } else {
            cpu.loadMachineCode(e.workload().machineCodeBytes());
        }
        int guard = 0;
        while (!cpu.isHalted() && guard++ < 100_000) cpu.step();
        for (CycleSnapshot snap : cpu.getCycleTrace()) {
            assertTrue(snap.queueOccupancy() <= PrefetchQueue.CAPACITY,
                id + ": queue occupancy " + snap.queueOccupancy() + " exceeds capacity " + PrefetchQueue.CAPACITY);
        }
    }

    @ParameterizedTest
    @MethodSource("allCatalogIds")
    void expectedRegistersDeclaredByTheCatalogAreActuallySatisfied(String id) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        Experiment e = ExperimentCatalog.get(id);
        e.configuration().expectedRegisters().forEach((reg, expected) ->
            assertEquals(expected, run.finalState().registers().get(reg),
                id + ": expected register " + reg + " to be " + expected));
        assertTrue(run.passed(), id + ": a catalog entry with satisfied expectations must report passed=true");
    }
}

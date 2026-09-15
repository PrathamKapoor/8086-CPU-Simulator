package research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Step 18: golden experiments. Each test pins the EXACT measured
 * values -- retired-instruction count, micro-op count, cycles, queue
 * events, flush count, memory operations, timing metrics, and the
 * deterministic result hash -- for a small, stable set of catalog
 * workloads. These numbers were captured directly from
 * {@link ExperimentRunner#run} against the current frozen Phase 3 timing
 * model and Phase 5 debugger; a change here means either a deliberate,
 * documented behavior change or a real regression, never noise.
 */
class GoldenExperimentsTest {
    private static double metric(ExperimentRun run, String name) {
        return run.metrics().get(name).value();
    }

    @Test void sequentialAluGoldenValues() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("sequential-alu"));
        assertTrue(run.passed());
        assertEquals(7, metric(run, "execution.instructionsRetired"));
        assertEquals(36, metric(run, "execution.microOpsExecuted"));
        assertEquals(36, metric(run, "execution.totalCycles"));
        assertEquals(1, metric(run, "biu.queueStarvationCycles"));
        assertEquals(0, metric(run, "controlFlow.queueFlushes"));
        assertEquals(7, metric(run, "memory.reads"));
        assertEquals(0, metric(run, "memory.writes"));
        assertEquals("3aa60521106b14b2c37a614540eb801a2c2396bbc5476bb450243b1f268181de", run.resultHash());
    }

    @Test void loopWorkloadGoldenValues() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("loop-workload"));
        assertTrue(run.passed());
        assertEquals(13, metric(run, "execution.instructionsRetired"));
        assertEquals(70, metric(run, "execution.microOpsExecuted"));
        assertEquals(70, metric(run, "execution.totalCycles"));
        assertEquals(4, metric(run, "controlFlow.queueFlushes"));
        assertEquals(4, metric(run, "controlFlow.takenBranches"));
        assertEquals(1, metric(run, "controlFlow.notTakenBranches"));
        assertEquals("2dde4102ec2103915f15f6ff0c2c70c8e5b3dbe71b0191bbf42b410c826fed14", run.resultHash());
    }

    @Test void memoryHeavyGoldenValues() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("memory-heavy"));
        assertTrue(run.passed());
        assertEquals(6, metric(run, "execution.instructionsRetired"));
        assertEquals(40, metric(run, "execution.microOpsExecuted"));
        assertEquals(40, metric(run, "execution.totalCycles"));
        assertEquals(8, metric(run, "memory.reads"));
        assertEquals(3, metric(run, "memory.writes"));
        assertEquals(11, metric(run, "memory.cellsTransferred"));
        assertEquals("9bb14d04270a3d683996a3e17885928f683987d50cd0068bca322e88dec40c92", run.resultHash());
    }

    @Test void controlTransferFlushGoldenValues() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("control-transfer-flush"));
        assertTrue(run.passed());
        assertEquals(6, metric(run, "execution.instructionsRetired"));
        assertEquals(35, metric(run, "execution.totalCycles"));
        assertEquals(4, metric(run, "controlFlow.unconditionalTransfers"));
        assertEquals(4, metric(run, "controlFlow.queueFlushes"));
        assertEquals(15, metric(run, "controlFlow.flushedBytes"));
        assertEquals("fd6d4e4d84aaacbb204c1ef73f183704e8d1d0048715cd3afbd416c315f81e35", run.resultHash());
    }

    @Test void queueStarvationGoldenValuesShowElevatedStarvation() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("queue-starvation"));
        assertTrue(run.passed());
        assertEquals(18, metric(run, "execution.instructionsRetired"));
        assertEquals(98, metric(run, "execution.totalCycles"));
        assertEquals(8, metric(run, "biu.queueStarvationCycles"));
        assertEquals(8, metric(run, "eu.stallCycles"));
        assertEquals(7, metric(run, "controlFlow.queueFlushes"));
        // The whole point of this workload: it must starve far more than an
        // ordinary straight-line baseline (sequential-alu starves for 1 cycle).
        ExperimentRun baseline = ExperimentRunner.run(ExperimentCatalog.get("sequential-alu"));
        assertTrue(metric(run, "biu.queueStarvationCycles") > metric(baseline, "biu.queueStarvationCycles") * 4);
        assertEquals("e165ebf297981c8cde666b30145d2add96a8e05af123db0b807aec84d25f7d38", run.resultHash());
    }

    @Test void busContentionGoldenValuesShowElevatedContention() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("bus-contention"));
        assertTrue(run.passed());
        assertEquals(13, metric(run, "execution.instructionsRetired"));
        assertEquals(88, metric(run, "execution.totalCycles"));
        assertEquals(12, metric(run, "bus.contentionCycles"));
        assertEquals(24, metric(run, "memory.cellsTransferred"));
        // The whole point of this workload: contention must be far above an
        // ordinary straight-line baseline (sequential-alu contends for 2 cycles).
        ExperimentRun baseline = ExperimentRunner.run(ExperimentCatalog.get("sequential-alu"));
        assertTrue(metric(run, "bus.contentionCycles") > metric(baseline, "bus.contentionCycles") * 4);
        assertEquals("f23f36f9441d7d8d0e12bbb9b1d29bb9ab42bf2c48fbffc0f901a9923e394a89", run.resultHash());
    }

    @Test void repWorkloadGoldenValues() {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get("rep-workload"));
        assertTrue(run.passed());
        assertEquals(6, metric(run, "execution.instructionsRetired"));
        assertEquals(31, metric(run, "execution.totalCycles"));
        assertEquals("067554aab5be72661eab3644c1deb21ae3768fb1e16028368d9a9e5d652a63d2", run.resultHash());
    }

    @Test void goldenRunsAreByteForByteReproducibleAcrossRepeatedInvocations() {
        for (String id : new String[] {"sequential-alu", "loop-workload", "memory-heavy",
                "control-transfer-flush", "queue-starvation", "bus-contention", "rep-workload"}) {
            String first = ExperimentRunner.run(ExperimentCatalog.get(id)).resultHash();
            String second = ExperimentRunner.run(ExperimentCatalog.get(id)).resultHash();
            assertEquals(first, second, "result hash must be identical across repeated runs of " + id);
        }
    }
}

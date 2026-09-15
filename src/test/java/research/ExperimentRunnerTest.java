package research;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentRunnerTest {
    @Test void basicRunProducesPassingResultAndCorrectMetrics() {
        Experiment exp = new Experiment("seq-add", "sequential ALU",
            Workload.fromSource("seq-add", "adds two registers", "MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nHLT\n"),
            ExperimentConfiguration.timed(Map.of("AX", 3)));
        ExperimentRun run = ExperimentRunner.run(exp);

        assertTrue(run.passed());
        assertEquals(3, run.finalState().registers().get("AX"));
        assertTrue(run.finalState().halted());
        assertEquals(4, run.metrics().get("execution.instructionsRetired").value());
        assertEquals(MetricProvenance.MEASURED, run.metrics().get("execution.instructionsRetired").provenance());
        assertEquals(MetricProvenance.DERIVED, run.metrics().get("execution.cpi").provenance());
        assertTrue(run.metrics().get("execution.cpi").value() > 0);
    }

    @Test void failingExpectedRegisterMarksRunAsNotPassed() {
        Experiment exp = new Experiment("wrong-expectation", "deliberately wrong",
            Workload.fromSource("w", "d", "MOV AX, 0005H\nHLT\n"),
            ExperimentConfiguration.timed(Map.of("AX", 999)));
        ExperimentRun run = ExperimentRunner.run(exp);
        assertFalse(run.passed());
        assertEquals(5, run.finalState().registers().get("AX"));
    }

    @Test void controlFlowMetricsAreUnavailableWithoutTracing() {
        Experiment exp = new Experiment("no-trace", "d",
            Workload.fromSource("w", "d", "MOV CX, 0002H\nback: DEC CX\nJNZ back\nHLT\n"),
            ExperimentConfiguration.timed());
        ExperimentRun run = ExperimentRunner.run(exp);
        assertEquals(MetricProvenance.UNAVAILABLE, run.metrics().get("controlFlow.takenBranches").provenance());
        assertTrue(Double.isNaN(run.metrics().get("controlFlow.takenBranches").value()));
        // BIU/EU/bus/timing metrics remain available regardless (pass 1 always runs).
        assertEquals(MetricProvenance.MEASURED, run.metrics().get("biu.bytesFetched").provenance());
    }

    @Test void branchMetricsDistinguishTakenFromNotTakenAcrossLoopIterations() {
        // JNZ "back" is taken twice (CX: 3->2, 2->1) and not-taken once (CX: 1->0, falls through).
        Experiment exp = new Experiment("loop-branches", "d",
            Workload.fromSource("w", "d", "MOV CX, 0003H\nback: DEC CX\nJNZ back\nHLT\n"),
            ExperimentConfiguration.timed().withTracing());
        ExperimentRun run = ExperimentRunner.run(exp);
        assertEquals(3, run.metrics().get("controlFlow.conditionalBranchesRetired").value());
        assertEquals(2, run.metrics().get("controlFlow.takenBranches").value());
        assertEquals(1, run.metrics().get("controlFlow.notTakenBranches").value());
    }

    @Test void debuggerMetricsCaptureBreakpointHitsWhenEnabled() {
        Experiment exp = new Experiment("bp-count", "d",
            Workload.fromSource("w", "d", "MOV AX, 1\nMOV AX, 2\nMOV AX, 3\nHLT\n"),
            new ExperimentConfiguration(simulator.profiler.TimingModel.SIMPLIFIED_8086,
                ExperimentConfiguration.DEFAULT_MICRO_OP_LIMIT, false, true, java.util.List.of(1), Map.of()));
        ExperimentRun run = ExperimentRunner.run(exp);
        assertEquals(1, run.metrics().get("debugger.breakpointHits").value());
    }

    @Test void machineCodeWorkloadExecutesThroughTheSameRunner() {
        byte[] bytes = { (byte) 0xB8, 0x05, 0x00, (byte) 0xF4 }; // MOV AX, 5 ; HLT
        Experiment exp = new Experiment("mc", "d", Workload.fromMachineCode("mc", "d", bytes),
            ExperimentConfiguration.timed(Map.of("AX", 5)));
        ExperimentRun run = ExperimentRunner.run(exp);
        assertTrue(run.passed());
    }

    @Test void sameExperimentTwiceProducesIdenticalHash() {
        Experiment exp = new Experiment("det", "d",
            Workload.fromSource("w", "d", "MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nHLT\n"),
            ExperimentConfiguration.timed());
        ExperimentRun run1 = ExperimentRunner.run(exp);
        ExperimentRun run2 = ExperimentRunner.run(exp);
        assertEquals(run1.resultHash(), run2.resultHash());
        assertNotEquals(run1.executedAt(), run2.executedAt(), "sanity: timestamps differ but hash must not");
    }

    @Test void differentExperimentsProduceDifferentHashes() {
        ExperimentRun a = ExperimentRunner.run(new Experiment("a", "d", Workload.fromSource("w", "d", "MOV AX, 1\nHLT\n"), ExperimentConfiguration.timed()));
        ExperimentRun b = ExperimentRunner.run(new Experiment("b", "d", Workload.fromSource("w", "d", "MOV AX, 2\nHLT\n"), ExperimentConfiguration.timed()));
        assertNotEquals(a.resultHash(), b.resultHash());
    }
}

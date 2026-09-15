package research;

import cpu.CPU;
import debugger.DebugSession;
import debugger.TraceEvent;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;
import simulator.profiler.PerformanceProfiler;
import simulator.profiler.TimingModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Step 20: CPU execution, the Phase 5 debugger trace, the Phase 3
 * timing trace, and Phase 6 experiment metrics must never silently disagree
 * about the same deterministic workload. Each test here independently
 * derives the same fact two different ways and asserts they match.
 */
class CrossLayerConsistencyTest {
    private static final String WORKLOAD =
        "MOV BX, 0020H\nMOV WORD [BX], 1234H\nMOV CX, 0003H\nback: DEC CX\nJNZ back\nMOV AX, [BX]\nHLT\n";

    @Test void instructionCountFromCpuAgreesWithDebuggerRetirementEvents() {
        CPU cpu = new CPU();
        cpu.loadProgram(new InstructionParser().parseProgram(WORKLOAD));
        int cpuGuard = 0;
        while (!cpu.isHalted() && cpuGuard++ < 100_000) cpu.step();

        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(WORKLOAD));
        session.setTracing(true);
        session.run(100_000);

        long retiredEvents = session.trace().stream().filter(e -> e instanceof TraceEvent.InstructionRetired).count();
        assertEquals(session.instructionsRetired(), retiredEvents);
        assertTrue(session.cpu().isHalted());
        assertEquals(cpu.getRegister("AX").output(), session.cpu().getRegister("AX").output());
    }

    @Test void memoryWritesFromExperimentMetricsAgreeWithDebuggerMemoryWriteEvents() {
        Experiment experiment = new Experiment("cross-layer-mem", "d", Workload.fromSource("w", "d", WORKLOAD), ExperimentConfiguration.timed().withTracing());
        ExperimentRun run = ExperimentRunner.run(experiment);
        long measuredWrites = (long) run.metrics().get("memory.writes").value();

        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(WORKLOAD));
        session.setTracing(true);
        session.run(100_000);
        long debuggerWriteEvents = session.trace().stream().filter(e -> e instanceof TraceEvent.MemoryWriteEvent).count();

        assertEquals(measuredWrites, debuggerWriteEvents);
    }

    @Test void controlTransfersFromExperimentMetricsAgreeWithDebuggerControlTransferEvents() {
        Experiment experiment = new Experiment("cross-layer-flush", "d", Workload.fromSource("w", "d", WORKLOAD), ExperimentConfiguration.timed().withTracing());
        ExperimentRun run = ExperimentRunner.run(experiment);
        long measuredFlushes = (long) run.metrics().get("controlFlow.queueFlushes").value();

        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(WORKLOAD));
        session.setTracing(true);
        session.run(100_000);
        long debuggerControlTransfers = session.trace().stream().filter(e -> e instanceof TraceEvent.ControlTransfer).count();

        // Every actual control transfer flushes the queue exactly once in this
        // architecture (CPU.stepTimed emits QUEUE_FLUSH precisely when a
        // retiring instruction's IP != previousIndex+1, the same condition
        // DebugSession emits ControlTransfer under) -- so the two counts from
        // two completely independent observability layers must be equal.
        assertEquals(measuredFlushes, debuggerControlTransfers);
        assertTrue(measuredFlushes > 0, "this workload must actually branch for the check to be meaningful");
    }

    @Test void cycleCountFromExperimentMetricsAgreesWithTheTimingModel() {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        cpu.loadProgram(new InstructionParser().parseProgram(WORKLOAD));
        int guard = 0;
        while (!cpu.isHalted() && guard++ < 100_000) cpu.step();
        long directCycles = new PerformanceProfiler(cpu).metrics().totalCycles();

        Experiment experiment = new Experiment("cross-layer-cycles", "d", Workload.fromSource("w", "d", WORKLOAD), ExperimentConfiguration.timed());
        ExperimentRun run = ExperimentRunner.run(experiment);
        long measuredCycles = (long) run.metrics().get("timing.totalCycles").value();

        assertEquals(directCycles, measuredCycles);
    }

    @Test void timelineFlushCyclesAgreeWithDebuggerControlTransferCount() {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        cpu.loadProgram(new InstructionParser().parseProgram(WORKLOAD));
        int guard = 0;
        while (!cpu.isHalted() && guard++ < 100_000) cpu.step();
        Timeline timeline = Timeline.from(cpu);

        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(WORKLOAD));
        session.setTracing(true);
        session.run(100_000);
        long debuggerControlTransfers = session.trace().stream().filter(e -> e instanceof TraceEvent.ControlTransfer).count();

        assertEquals((long) timeline.flushCycles().size(), debuggerControlTransfers);
    }

    @Test void finalArchitecturalStateAgreesAcrossPlainCpuDebugSessionAndExperimentRunner() {
        CPU cpu = new CPU();
        cpu.loadProgram(new InstructionParser().parseProgram(WORKLOAD));
        int guard = 0;
        while (!cpu.isHalted() && guard++ < 100_000) cpu.step();

        DebugSession session = DebugSession.forSourceProgram(new InstructionParser().parseProgram(WORKLOAD));
        session.run(100_000);

        ExperimentRun run = ExperimentRunner.run(new Experiment("cross-layer-state", "d", Workload.fromSource("w", "d", WORKLOAD), ExperimentConfiguration.timed()));

        assertEquals(cpu.getRegister("AX").output(), session.cpu().getRegister("AX").output());
        assertEquals(cpu.getRegister("AX").output(), run.finalState().registers().get("AX"));
        assertEquals(cpu.getFlags().output(), session.cpu().getFlags().output());
        assertEquals(cpu.getFlags().output(), run.finalState().flags());
    }
}

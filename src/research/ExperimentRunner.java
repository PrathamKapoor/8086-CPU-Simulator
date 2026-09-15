package research;

import cpu.CPU;
import debugger.DebugSession;
import instruction.InstructionParser;
import simulator.profiler.PerformanceProfiler;

import java.time.Instant;

/**
 * Executes one {@link Experiment} deterministically and in isolation: a
 * fresh {@code CPU} (and, when requested, a fresh {@code DebugSession}) is
 * constructed for every call — no shared/global state crosses runs. See
 * docs/verification/phase-6-research-platform-audit.md for why this may run
 * the workload through two observation passes rather than one.
 */
public final class ExperimentRunner {
    private ExperimentRunner() { }

    public static ExperimentRun run(Experiment experiment) {
        ExperimentConfiguration config = experiment.configuration();

        // Pass 1: authoritative timing/BIU/EU/bus/flush metrics (Phase 3, unmodified).
        CPU cpu = new CPU();
        cpu.setTimingModel(config.timingModel());
        loadWorkload(cpu, experiment.workload());
        runToCompletion(cpu, config.executionMicroOpLimit());
        var perf = new PerformanceProfiler(cpu).metrics();
        BusMetrics bus = BusMetrics.from(cpu, perf.biuStallCycles());
        ArchitecturalState finalState = ArchitecturalState.capture(cpu);

        // Pass 2 (optional, off by default): typed-trace branch/debugger metrics (Phase 5, unmodified).
        BranchMetrics branch = BranchMetrics.UNAVAILABLE;
        DebuggerMetrics dbgMetrics = DebuggerMetrics.UNAVAILABLE;
        if (config.tracingEnabled() || config.debuggerEnabled()) {
            DebugSession session = loadWorkloadIntoSession(experiment.workload());
            if (config.tracingEnabled()) session.setTracing(true);
            for (int index : config.breakpointInstructionIndices()) session.addInstructionBreakpoint(index, null);
            runSessionToCompletion(session, config.executionMicroOpLimit());
            if (config.tracingEnabled()) branch = BranchMetrics.from(session);
            if (config.debuggerEnabled()) dbgMetrics = DebuggerMetrics.from(session);
            else if (config.tracingEnabled()) dbgMetrics = new DebuggerMetrics(session.trace().size(), 0, 0, true);
        }

        MetricSet metrics = MetricSet.build(perf, bus, branch, dbgMetrics);
        boolean passed = finalState.halted() && expectedRegistersMatch(finalState, config);
        String hash = ResultHasher.hash(experiment, finalState, metrics);
        return new ExperimentRun(experiment, finalState, metrics, passed, hash, Instant.now(), SimulatorMetadata.commitOrVersion());
    }

    private static boolean expectedRegistersMatch(ArchitecturalState state, ExperimentConfiguration config) {
        for (var entry : config.expectedRegisters().entrySet()) {
            Integer actual = state.registers().get(entry.getKey().toUpperCase());
            if (actual == null || (actual & 0xFFFF) != (entry.getValue() & 0xFFFF)) return false;
        }
        return true;
    }

    private static void loadWorkload(CPU cpu, Workload workload) {
        if (workload.kind() == Workload.Kind.SOURCE) {
            cpu.loadProgram(new InstructionParser().parseProgram(workload.sourceAssembly()));
        } else {
            cpu.loadMachineCode(workload.machineCodeBytes());
        }
    }

    private static DebugSession loadWorkloadIntoSession(Workload workload) {
        return workload.kind() == Workload.Kind.SOURCE
            ? DebugSession.forSourceProgram(new InstructionParser().parseProgram(workload.sourceAssembly()))
            : DebugSession.forMachineCode(workload.machineCodeBytes());
    }

    private static void runToCompletion(CPU cpu, long microOpLimit) {
        long steps = 0;
        while (!cpu.isHalted() && steps++ < microOpLimit) cpu.step();
    }

    private static void runSessionToCompletion(DebugSession session, long microOpLimit) {
        session.run(microOpLimit);
    }
}

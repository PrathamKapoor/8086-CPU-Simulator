package research;

import instruction.InstructionParser;
import org.junit.jupiter.api.Test;
import simulator.profiler.TimingModel;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 Step 21: measure the research platform's own overhead relative to
 * plain CPU execution, following {@code debugger.DebuggerPerformanceTest}'s
 * precedent. Four things are compared: raw CPU execution, ExperimentRunner
 * with tracing/debugger off (its single-pass default), ExperimentRunner
 * with tracing enabled (its optional second pass through a fresh
 * DebugSession), and ExperimentRunner with the debugger fully enabled. This
 * is a coarse, deterministic sanity bound -- it exists to catch a
 * regression that makes the platform accidentally expensive, not to chase
 * absolute numbers.
 */
class ResearchPerformanceTest {
    private static final int ITERATIONS = 200;
    private static final String LOOP_SOURCE = "MOV CX, 0032H\nback: INC AX\nADD BX, AX\nLOOP back\nHLT\n";

    private Experiment untracedExperiment() {
        return new Experiment("perf-untraced", "d", Workload.fromSource("w", "d", LOOP_SOURCE), ExperimentConfiguration.timed());
    }

    private Experiment tracedExperiment() {
        return new Experiment("perf-traced", "d", Workload.fromSource("w", "d", LOOP_SOURCE), ExperimentConfiguration.timed().withTracing());
    }

    private Experiment debuggerExperiment() {
        ExperimentConfiguration config = new ExperimentConfiguration(TimingModel.SIMPLIFIED_8086,
            ExperimentConfiguration.DEFAULT_MICRO_OP_LIMIT, true, true, java.util.List.of(), java.util.Map.of());
        return new Experiment("perf-debugger", "d", Workload.fromSource("w", "d", LOOP_SOURCE), config);
    }

    @Test void experimentRunnerOverheadIsBoundedRelativeToRawCpu() {
        long rawNanos = time(() -> {
            cpu.CPU cpu = new cpu.CPU();
            cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
            cpu.loadProgram(new InstructionParser().parseProgram(LOOP_SOURCE));
            int guard = 0;
            while (!cpu.isHalted() && guard++ < 100_000) cpu.step();
        });
        long untracedNanos = time(() -> ExperimentRunner.run(untracedExperiment()));
        long tracedNanos = time(() -> ExperimentRunner.run(tracedExperiment()));
        long debuggerNanos = time(() -> ExperimentRunner.run(debuggerExperiment()));

        System.out.println("[perf] raw CPU:                 " + ms(rawNanos));
        System.out.println("[perf] ExperimentRunner (plain): " + ms(untracedNanos) + " (single pass, no trace/debugger)");
        System.out.println("[perf] ExperimentRunner (traced):" + ms(tracedNanos) + " (two passes: plain + tracing DebugSession)");
        System.out.println("[perf] ExperimentRunner (debug): " + ms(debuggerNanos) + " (two passes: plain + full debugger DebugSession)");
        System.out.println("[perf] plain/raw ratio:          " + ratio(untracedNanos, rawNanos));
        System.out.println("[perf] traced/plain ratio:       " + ratio(tracedNanos, untracedNanos));
        System.out.println("[perf] debug/plain ratio:        " + ratio(debuggerNanos, untracedNanos));

        // The untraced single pass is exactly one CPU run plus deterministic,
        // allocation-light metric assembly -- it must stay within a modest
        // constant factor of raw execution.
        assertTrue(untracedNanos < rawNanos * 10L + 100_000_000L,
            "ExperimentRunner's untraced pass overhead too high: raw=" + rawNanos + "ns plain=" + untracedNanos + "ns");
        // Tracing/debugger mode intentionally runs a second, heavier pass
        // through a fresh DebugSession -- roughly double the untraced cost,
        // bounded generously so this stays a regression guard, not a
        // micro-benchmark assertion.
        assertTrue(tracedNanos < untracedNanos * 20L + 200_000_000L,
            "ExperimentRunner's traced second pass overhead too high: plain=" + untracedNanos + "ns traced=" + tracedNanos + "ns");
        assertTrue(debuggerNanos < untracedNanos * 20L + 200_000_000L,
            "ExperimentRunner's debugger second pass overhead too high: plain=" + untracedNanos + "ns debug=" + debuggerNanos + "ns");
    }

    private long time(Runnable action) {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) action.run();
        return System.nanoTime() - start;
    }

    private static String ms(long nanos) { return (nanos / 1_000_000.0) + " ms"; }
    private static String ratio(long a, long b) { return String.format("%.2f", a / (double) b); }
}

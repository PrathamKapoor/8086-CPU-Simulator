package debugger;

import instruction.Instruction;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 5 Step 21: the debugger must be dormant (no meaningful overhead)
 * when no breakpoints/watchpoints/trace/snapshots are in use, since that is
 * how any interactive session spends most of its time between stops. This
 * is a coarse, deterministic sanity bound, not a micro-benchmark harness --
 * it exists to catch a regression that makes the dormant path accidentally
 * expensive (e.g. an unconditional allocation or scan per micro-op), not to
 * chase absolute numbers.
 */
class DebuggerPerformanceTest {
    private static final int ITERATIONS = 400;

    private List<Instruction> loopProgram(int cxLimit) {
        return new InstructionParser().parseProgram(
            "MOV CX, " + String.format("%04XH", cxLimit) + "\nback: INC AX\nADD BX, AX\nLOOP back\nHLT\n");
    }

    @Test void debuggerOffOverheadIsSmallRelativeToRawCpu() {
        // "Debugger off" here means a session with zero breakpoints, zero
        // watchpoints, and tracing disabled -- run() must not be paying for
        // capabilities nobody asked for.
        long rawNanos = timeRawCpu();
        long dormantNanos = timeDormantSession();
        long tracedNanos = timeTracedSession();

        System.out.println("[perf] raw CPU:        " + (rawNanos / 1_000_000.0) + " ms");
        System.out.println("[perf] debugger OFF:   " + (dormantNanos / 1_000_000.0) + " ms (dormant: no bp/watch/trace)");
        System.out.println("[perf] debugger ON:    " + (tracedNanos / 1_000_000.0) + " ms (tracing enabled)");
        System.out.println("[perf] OFF/raw ratio:  " + String.format("%.2f", dormantNanos / (double) rawNanos));
        System.out.println("[perf] ON/OFF ratio:   " + String.format("%.2f", tracedNanos / (double) dormantNanos));

        // Generous bound: DebugSession's dormant per-micro-op bookkeeping
        // (a couple of empty-collection checks) should not multiply raw
        // execution time by more than an order of magnitude.
        assertTrue(dormantNanos < rawNanos * 10L + 50_000_000L,
            "dormant debugger overhead too high: raw=" + rawNanos + "ns dormant=" + dormantNanos + "ns");
        // Tracing is an intentionally heavier, opted-in mode; it must still
        // complete in bounded time for a moderate workload.
        assertTrue(tracedNanos < dormantNanos * 50L + 200_000_000L,
            "traced overhead too high relative to dormant: dormant=" + dormantNanos + "ns traced=" + tracedNanos + "ns");
    }

    private long timeRawCpu() {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            cpu.CPU cpu = new cpu.CPU();
            cpu.loadProgram(new ArrayList<>(loopProgram(50)));
            cpu.run();
        }
        return System.nanoTime() - start;
    }

    private long timeDormantSession() {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            DebugSession s = DebugSession.forSourceProgram(loopProgram(50));
            s.run();
        }
        return System.nanoTime() - start;
    }

    private long timeTracedSession() {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            DebugSession s = DebugSession.forSourceProgram(loopProgram(50));
            s.setTracing(true);
            s.run();
        }
        return System.nanoTime() - start;
    }

    @Test void manyDisabledWatchpointsDoNotSlowDownExecution() {
        DebugSession baseline = DebugSession.forSourceProgram(loopProgram(200));
        long start1 = System.nanoTime();
        baseline.run();
        long baselineNanos = System.nanoTime() - start1;

        DebugSession withDisabledWatches = DebugSession.forSourceProgram(loopProgram(200));
        for (int i = 0; i < 50; i++) {
            int id = withDisabledWatches.addMemoryWatch(0x1000 + i, Watchpoint.Access.WRITE);
            withDisabledWatches.watchpoints().stream().filter(w -> w.id() == id).findFirst().get().setEnabled(false);
        }
        long start2 = System.nanoTime();
        withDisabledWatches.run();
        long disabledWatchNanos = System.nanoTime() - start2;

        System.out.println("[perf] baseline: " + (baselineNanos / 1_000_000.0) + " ms, with 50 disabled watches: "
            + (disabledWatchNanos / 1_000_000.0) + " ms");
        assertTrue(disabledWatchNanos < baselineNanos * 20L + 100_000_000L,
            "disabled watchpoints should not meaningfully slow execution");
    }
}

package research;

import cpu.CPU;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;
import simulator.profiler.PerformanceProfiler;
import simulator.profiler.TimingModel;

import static org.junit.jupiter.api.Assertions.*;

class TimelineTest {
    private CPU runWorkload(String source) {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        cpu.loadProgram(new InstructionParser().parseProgram(source));
        int guard = 0;
        while (!cpu.isHalted() && guard++ < 100_000) cpu.step();
        return cpu;
    }

    @Test void timelineHasOneEntryPerCycleAndMatchesTotalCycles() {
        CPU cpu = runWorkload("MOV AX, 0001H\nMOV BX, 0002H\nADD AX, BX\nHLT\n");
        Timeline timeline = Timeline.from(cpu);
        var perf = new PerformanceProfiler(cpu).metrics();
        assertEquals(perf.totalCycles(), timeline.entries().size());
    }

    @Test void stalledCycleCountMatchesTheEuStallMetric() {
        // A queue-starvation-shaped workload: several one-byte instructions in a row.
        CPU cpu = runWorkload("MOV AX, 0000H\nINC AX\nINC AX\nINC AX\nINC AX\nINC AX\nHLT\n");
        Timeline timeline = Timeline.from(cpu);
        var perf = new PerformanceProfiler(cpu).metrics();
        long euStalledInTimeline = timeline.entries().stream().filter(Timeline.Entry::euStalled).count();
        assertEquals(perf.euStallCycles(), euStalledInTimeline);
    }

    @Test void flushCycleCountMatchesControlTransferFlushesMetric() {
        CPU cpu = runWorkload("JMP l1\nl1: JMP l2\nl2: MOV AX, 5\nHLT\n");
        Timeline timeline = Timeline.from(cpu);
        var perf = new PerformanceProfiler(cpu).metrics();
        assertEquals(perf.controlTransferFlushes(), timeline.flushCycles().size());
    }

    @Test void explainCycleCountIsConsistentWithItsOwnTallies() {
        CPU cpu = runWorkload("MOV CX, 0003H\nback: DEC CX\nJNZ back\nHLT\n");
        Timeline timeline = Timeline.from(cpu);
        String explanation = timeline.explainCycleCount();
        assertTrue(explanation.contains("total=" + timeline.entries().size()));
        assertTrue(explanation.startsWith("total="));
    }
}

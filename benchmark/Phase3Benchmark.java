package benchmark;

import cpu.CPU;
import instruction.InstructionParser;
import simulator.profiler.PerformanceProfiler;
import simulator.profiler.TimingModel;
import java.nio.file.*;
import java.util.*;

public class Phase3Benchmark {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 3 Benchmark Suite ===");
        String[] workloads = {"sequential.asm", "branch_heavy.asm", "loop_heavy.asm", "string_workload.asm"};
        for (String workload : workloads) {
            Path file = Path.of("benchmark", workload);
            if (!Files.exists(file)) {
                System.out.println(workload + ": SKIPPED (file missing)");
                continue;
            }
            String text = Files.readString(file);
            InstructionParser parser = new InstructionParser();
            java.util.List<instruction.Instruction> instructions = parser.parseProgram(text);
            CPU cpu = new CPU();
            cpu.loadProgram(instructions);
            int cycles = 0;
            while (!cpu.isHalted() && cycles++ < 500) {
                cpu.step();
            }
            PerformanceProfiler profiler = new PerformanceProfiler(cpu);
            System.out.println(workload + ": cycles=" + cpu.getTotalCyclesRun()
                + ", stalls=" + cpu.getStallCycles()
                + ", flushes=" + cpu.getQueueFlushes()
                + ", fetched=" + cpu.getBytesFetched()
                + ", consumed=" + cpu.getBytesConsumed()
                + ", overlap=" + cpu.getTotalOverlapCycles());
        }
        System.out.println("=== Benchmark complete ===");
    }
}

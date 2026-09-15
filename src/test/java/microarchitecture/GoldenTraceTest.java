package microarchitecture;

import cpu.CPU;
import cpu.microarchitecture.MicroarchitectureEventType;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;
import simulator.profiler.PerformanceProfiler;
import simulator.profiler.TimingModel;

import static org.junit.jupiter.api.Assertions.*;

class GoldenTraceTest {
    private CPU run(String source) {
        CPU cpu = new CPU(); cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        cpu.loadProgram(new InstructionParser().parseProgram(source));
        for (int guard = 0; !cpu.isHalted() && guard < 500; guard++) cpu.step();
        assertTrue(cpu.isHalted()); return cpu;
    }
    private long events(CPU cpu, MicroarchitectureEventType type) { return cpu.getCycleTrace().stream().flatMap(s -> s.events().stream()).filter(e -> e.type() == type).count(); }

    @Test void sequential_trace_has_no_flush_and_never_exceeds_six_entries() {
        CPU cpu = run("MOV AX,1\nINC AX\nHLT");
        assertEquals(0, events(cpu, MicroarchitectureEventType.QUEUE_FLUSH));
        assertTrue(cpu.getCycleTrace().stream().allMatch(s -> s.queueOccupancy() >= 0 && s.queueOccupancy() <= 6));
        assertEquals(3, new PerformanceProfiler(cpu).metrics().instructionsRetired());
    }

    @Test void taken_branch_flushes_and_refetches_target() {
        CPU cpu = run("JMP 2\nMOV AX,1\nMOV AX,7\nHLT");
        assertEquals(7, cpu.getRegister("AX").output());
        assertTrue(events(cpu, MicroarchitectureEventType.CONTROL_TRANSFER) >= 1);
        assertTrue(events(cpu, MicroarchitectureEventType.QUEUE_FLUSH) >= 1);
    }

    @Test void not_taken_branch_keeps_sequential_stream() {
        CPU cpu = run("MOV AX,0\nCMP AX,1\nJZ 5\nMOV BX,9\nHLT\nMOV BX,1\nHLT");
        assertEquals(9, cpu.getRegister("BX").output());
        assertEquals(0, events(cpu, MicroarchitectureEventType.QUEUE_FLUSH));
    }

    @Test void memory_trace_records_bus_contention_and_starvation() {
        CPU cpu = run("MOV AX,7\nMOV [0x100],AX\nMOV BX,[0x100]\nHLT");
        var metrics = new PerformanceProfiler(cpu).metrics();
        assertTrue(metrics.memoryReads() > 0); assertTrue(metrics.memoryWrites() > 0);
        assertTrue(metrics.biuStallCycles() > 0); assertTrue(metrics.euStallCycles() > 0);
    }

    @Test void call_return_and_taken_loop_produce_real_stream_flushes() {
        CPU call = run("CALL 3\nMOV AX,9\nHLT\nMOV AX,7\nRET");
        assertEquals(9, call.getRegister("AX").output());
        assertTrue(events(call, MicroarchitectureEventType.QUEUE_FLUSH) >= 2);
        CPU loop = run("MOV CX,2\nMOV AX,0\nL: INC AX\nLOOP L\nHLT");
        assertEquals(2, loop.getRegister("AX").output());
        assertEquals(1, events(loop, MicroarchitectureEventType.QUEUE_FLUSH));
    }

    @Test void queue_accounting_never_loses_a_token() {
        CPU cpu = run("MOV AX,1\nMOV BX,2\nADD AX,BX\nHLT");
        var metrics = new PerformanceProfiler(cpu).metrics();
        assertEquals(metrics.bytesFetched(), metrics.bytesConsumed() + metrics.flushedBytes() + cpu.getBiu().queueSize());
        assertTrue(cpu.getCycleTrace().stream().noneMatch(s -> s.busOwner().name().equals("BIU_FETCH") && s.events().stream().anyMatch(e -> e.type() == MicroarchitectureEventType.MEM_READ)));
    }
}

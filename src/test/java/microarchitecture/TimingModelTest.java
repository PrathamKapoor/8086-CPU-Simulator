package microarchitecture;

import cpu.CPU;
import cpu.microarchitecture.MicroarchitectureEventType;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;
import simulator.profiler.TimingModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingModelTest {
    @Test
    void simplified_model_records_queue_starvation_then_deterministic_retirement() {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        cpu.loadProgram(new InstructionParser().parseProgram("NOP\nHLT\n"));

        assertEquals(null, cpu.step(), "first simulated cycle fills an empty queue");
        assertTrue(cpu.getLastCycleSnapshot().events().stream().anyMatch(e -> e.type() == MicroarchitectureEventType.EU_STALL));
        int guard = 0;
        while (!cpu.isHalted() && guard++ < 50) cpu.step();

        assertTrue(cpu.isHalted());
        assertFalse(cpu.getCycleTrace().isEmpty());
        assertEquals(cpu.getTotalCyclesRun(), cpu.getCycleTrace().size());
    }
}

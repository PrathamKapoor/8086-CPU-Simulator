package machinecode;

import cpu.CPU;
import cpu.microarchitecture.MicroarchitectureEventType;
import instruction.InstructionParser;
import org.junit.jupiter.api.Test;
import simulator.profiler.TimingModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineCodeExecutionTest {
    @Test
    void executesDecodedMachineBytesThroughTheExistingExecutionEngine() {
        CPU sourceCpu = new CPU();
        sourceCpu.loadProgram(new InstructionParser().parseProgram("MOV AX, 1234H\nHLT\n"));
        sourceCpu.run();

        CPU machineCpu = new CPU();
        machineCpu.loadMachineCode(new byte[] { (byte) 0xB8, 0x34, 0x12, (byte) 0xF4 });
        machineCpu.run();

        assertEquals(sourceCpu.getRegister("AX").output(), machineCpu.getRegister("AX").output());
        assertTrue(machineCpu.isHalted());
        assertEquals(0xB8, machineCpu.getMemory().readByte(0));
        assertEquals(0xF4, machineCpu.getMemory().readByte(3));
    }

    @Test
    void simplifiedTimingFetchesActualMachineBytesAndConsumesCompleteInstructions() {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        cpu.loadMachineCode(new byte[] { (byte) 0xB8, 0x34, 0x12, (byte) 0xF4 });

        int guard = 0;
        while (!cpu.isHalted() && guard++ < 100) cpu.step();

        assertTrue(cpu.isHalted());
        assertEquals(4, cpu.getBytesConsumed());
        assertTrue(cpu.getCycleTrace().stream().flatMap(s -> s.events().stream())
            .anyMatch(e -> e.type() == MicroarchitectureEventType.FETCH_BYTE && e.value() == 0xB8));
    }

    @Test
    void translatesMachineByteRelativeTargetsBeforeUsingTheExistingControlUnit() {
        CPU cpu = new CPU();
        cpu.loadMachineCode(new byte[] {
            (byte) 0xEB, 0x03,
            (byte) 0xB8, 0x34, 0x12,
            (byte) 0xB8, 0x78, 0x56,
            (byte) 0xF4
        });
        cpu.run();

        assertTrue(cpu.isHalted());
        assertEquals(0x5678, cpu.getRegister("AX").output());
    }
}

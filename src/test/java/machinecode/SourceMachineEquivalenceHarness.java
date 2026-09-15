package machinecode;

import cpu.CPU;
import instruction.InstructionParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared assertion path: both programs must converge through ControlUnit semantics. */
final class SourceMachineEquivalenceHarness {
    private static final String[] REGISTERS = {"AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI", "CS", "DS", "SS", "ES", "IP"};
    private SourceMachineEquivalenceHarness() { }

    static void assertEquivalent(String source) {
        CPU sourceCpu = new CPU();
        sourceCpu.loadProgram(new InstructionParser().parseProgram(source));
        sourceCpu.run();
        CPU machineCpu = new CPU();
        machineCpu.loadMachineCode(new Intel8086Assembler().assemble(new InstructionParser().parseProgram(source)).bytes());
        machineCpu.run();
        for (String name : REGISTERS) assertEquals(sourceCpu.getRegister(name).output(), machineCpu.getRegister(name).output(), name);
        assertEquals(sourceCpu.getFlags().toString(), machineCpu.getFlags().toString(), "FLAGS");
        assertEquals(sourceCpu.isHalted(), machineCpu.isHalted(), "halt state");
    }
}

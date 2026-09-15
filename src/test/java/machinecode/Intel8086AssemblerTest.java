package machinecode;

import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Intel8086AssemblerTest {
    @Test
    void resolvesParserInstructionIndexLabelsToMachineByteOffsets() {
        AssembledProgram program = new Intel8086Assembler().assemble(new InstructionParser().parseProgram("""
            MOV AX, 1
            JMP done
            MOV AX, 2
            done: HLT
            """));

        assertArrayEquals(new byte[] { (byte) 0xB8, 1, 0, (byte) 0xEB, 3, (byte) 0xB8, 2, 0, (byte) 0xF4 }, program.bytes());
        assertEquals(0, program.instructionOffsets().get(0));
        assertEquals(3, program.instructionOffsets().get(1));
        assertEquals(8, program.instructionOffsets().get(3));
    }
}

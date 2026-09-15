package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MemoryImmediateCodecTest {
    @Test
    void encodesWordImmediateAluAgainstAn8086EffectiveAddress() {
        var instruction = new Instruction.Builder(Opcode.ADD).format(InstructionFormat.REG_INDIRECT_IMM)
            .dest("[BX+10]").baseReg("BX").disp(10).imm(5).raw("ADD [BX+10], 5").build();

        assertArrayEquals(new byte[] { (byte) 0x81, 0x47, 0x0A, 0x05, 0x00 },
            new Intel8086Encoder().encode(instruction, 0).bytes());
    }
}

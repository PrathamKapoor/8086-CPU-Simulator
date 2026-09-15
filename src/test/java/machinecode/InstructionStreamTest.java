package machinecode;

import instruction.Opcode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstructionStreamTest {
    @Test
    void decodesBackToBackVariableLengthInstructionsAtExactBoundaries() {
        List<DecodedInstruction> decoded = new Intel8086Decoder().decodeAll(new byte[] {
            (byte) 0xB8, 0x34, 0x12, (byte) 0xEB, 0x01, (byte) 0xF4, (byte) 0xF4
        });

        assertEquals(4, decoded.size());
        assertEquals(Opcode.MOV, decoded.get(0).instruction().getOpcode());
        assertEquals(3, decoded.get(0).nextOffset());
        assertEquals(Opcode.JMP, decoded.get(1).instruction().getOpcode());
        assertEquals(5, decoded.get(1).nextOffset());
        assertEquals(5, decoded.get(2).startOffset());
        assertEquals(6, decoded.get(2).nextOffset());
    }
}

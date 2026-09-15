package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XchgTestCodecTest {
    @Test void xchgAndTestHaveCanonical8086Encodings() {
        Intel8086Encoder encoder = new Intel8086Encoder(); Intel8086Decoder decoder = new Intel8086Decoder();
        Instruction xchg = new Instruction.Builder(Opcode.XCHG).format(InstructionFormat.REG_REG).dest("AX").src("BX").raw("XCHG AX, BX").build();
        assertArrayEquals(new byte[]{(byte) 0x93}, encoder.encode(xchg, 0).bytes());
        assertEquals(Opcode.XCHG, decoder.decode(new byte[]{(byte)0x87, (byte)0xD8}, 0).instruction().getOpcode());
        Instruction test = new Instruction.Builder(Opcode.TEST).format(InstructionFormat.REG_REG).dest("AL").src("CL").raw("TEST AL, CL").build();
        assertArrayEquals(new byte[]{(byte)0x84, (byte)0xC8}, encoder.encode(test, 0).bytes());
        assertEquals(Opcode.TEST, decoder.decode(new byte[]{(byte)0x84, (byte)0xC8}, 0).instruction().getOpcode());
    }
}

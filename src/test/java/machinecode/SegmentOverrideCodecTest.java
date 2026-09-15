package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SegmentOverrideCodecTest {
    @Test void encodesAndDecodesAll8086SegmentPrefixes() {
        Intel8086Encoder encoder = new Intel8086Encoder();
        Intel8086Decoder decoder = new Intel8086Decoder();
        for (String segment : new String[]{"ES", "CS", "SS", "DS"}) {
            Instruction i = new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG_INDIRECT)
                .dest("AX").src("[BX]").baseReg("BX").segOverride(segment).raw("MOV AX, [BX]").build();
            byte[] bytes = encoder.encode(i, 0).bytes();
            assertEquals(segment, decoder.decode(bytes, 0).instruction().getSegmentOverride());
            assertEquals(bytes.length, decoder.decode(bytes, 0).length());
        }
    }

    @Test void rejectsDuplicatePrefixes() {
        assertThrows(DecodeException.class, () -> new Intel8086Decoder().decode(new byte[]{0x26, 0x3e, (byte)0x8b, 0x07}, 0));
        assertThrows(DecodeException.class, () -> new Intel8086Decoder().decode(new byte[]{(byte)0xf3, (byte)0xf2, (byte)0xa4}, 0));
    }
}

package machinecode;

import instruction.Instruction;
import instruction.InstructionFormat;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Deterministic negative corpus for every byte shape currently claimed by the codec. */
class InvalidEncodingCorpusTest {
    private final Intel8086Decoder decoder = new Intel8086Decoder();
    private final Intel8086Encoder encoder = new Intel8086Encoder();

    @Test void truncatedAndUnsupportedStreamsAlwaysRaiseDecodeException() {
        List<byte[]> corpus = List.of(
            new byte[]{},                         // A: empty/truncated opcode
            new byte[]{(byte) 0x8B},              // B: missing ModR/M
            new byte[]{(byte) 0x8B, 0x46},        // C: missing disp8
            new byte[]{(byte) 0xB8, 0x34},        // D: missing imm16
            new byte[]{(byte) 0xE9, 0x34},        // E: missing rel16
            new byte[]{(byte) 0x0F},              // F/M: non-8086 opcode
            new byte[]{(byte) 0xF6, (byte) 0xC8}, // G: unsupported group /1
            new byte[]{(byte) 0xFE, (byte) 0xD0}, // G: invalid group /2
            new byte[]{0x26, 0x3E, (byte)0x90},   // K: duplicate segment prefix
            new byte[]{(byte)0xF3, (byte)0xF2, (byte)0xA4}, // K: duplicate repeat prefix
            new byte[]{0x26}                      // S: trailing incomplete prefix
        );
        for (byte[] bytes : corpus) assertThrows(DecodeException.class, () -> decoder.decode(bytes, 0));
    }

    @Test void invalidSemanticFormsAlwaysRaiseEncodeException() {
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_IMM8).dest("AL").imm(256).build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.JZ_JE).format(InstructionFormat.REL_ONLY).addr(200).build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.XCHG).format(InstructionFormat.REG_REG_INDIRECT).dest("AX").src("[BX]").baseReg("BX").build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG_INDIRECT).dest("AX").src("[BX]").baseReg("BX").segOverride("FS").build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.LEA).format(InstructionFormat.REG_REG_INDIRECT).dest("AX").src("[BX]").baseReg("BX").build(), 0));
    }
}

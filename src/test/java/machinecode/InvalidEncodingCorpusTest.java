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
            new byte[]{(byte) 0xE9, 0x34},        // E: missing rel16 (only 1 of 2 bytes)
            new byte[]{(byte) 0xC2, 0x04},        // D2: missing high byte of RET imm16
            new byte[]{(byte) 0xF7, 0x07},        // D2: TEST WORD [BX], imm16 with the imm16 truncated
            new byte[]{(byte) 0x0F},              // F/M: non-8086 opcode (also POP CS / reserved two-byte escape)
            new byte[]{(byte) 0xEA, 0, 0, 0, 0},  // F: far JMP direct — not modeled (no segmented addressing)
            new byte[]{(byte) 0x9A, 0, 0, 0, 0},  // F: far CALL direct — not modeled
            new byte[]{(byte) 0xCA, 0, 0},        // F: RETF imm16 — not modeled
            new byte[]{(byte) 0xF0},              // F: LOCK — bus arbitration is not modeled
            new byte[]{(byte) 0xD8, 0x00},        // F: ESC — no coprocessor operand is modeled
            new byte[]{(byte) 0xD2, (byte) 0xE0}, // F: SHL AX, CL — count is never resolved from CL at execution time
            new byte[]{(byte) 0xF6, (byte) 0xC8}, // G: unsupported group /1
            new byte[]{(byte) 0xFE, (byte) 0xD0}, // G: invalid group /2 for byte INC/DEC
            new byte[]{(byte) 0xFF, (byte) 0xD0}, // G: group FF /2 (CALL r/m16 indirect) — no runtime address resolution
            new byte[]{(byte) 0xF6, 0x27},        // G: MUL with a memory operand — not resolved by mul_reg/div_reg
            new byte[]{(byte) 0xFE, 0x07},        // G: byte INC with a memory operand
            new byte[]{(byte) 0x8E, (byte) 0xC9}, // G: MOV CS, r16 — CS is not a loadable MOV destination
            new byte[]{0x26, 0x3E, (byte)0x90},   // K: duplicate segment prefix
            new byte[]{(byte)0xF3, (byte)0xF2, (byte)0xA4}, // K: duplicate repeat prefix
            new byte[]{0x26}                      // S: trailing incomplete prefix
        );
        for (byte[] bytes : corpus) assertThrows(DecodeException.class, () -> decoder.decode(bytes, 0));
    }

    @Test void invalidSemanticFormsAlwaysRaiseEncodeException() {
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_IMM8).dest("AL").imm(256).build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.JZ_JE).format(InstructionFormat.REL_ONLY).addr(200).build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.XCHG).format(InstructionFormat.REG_INDIRECT_REG).dest("[BX]").src("AX").baseReg("BX").build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG_INDIRECT).dest("AX").src("[BX]").baseReg("BX").segOverride("FS").build(), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.LEA).format(InstructionFormat.REG_REG).dest("AX").src("BX").build(), 0));
        // invalid register form: mismatched operand widths
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.ADD).format(InstructionFormat.REG_REG).dest("AL").src("BX").build(), 0));
        // invalid addressing form: SI and DI can only ever be index registers, never both operands of one address
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_REG_INDIRECT).dest("AX").src("[SI+DI]").baseReg("SI").indexReg("DI").build(), 0));
        // invalid segment-register form: MOV CS, r16 is not a legal destination
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.MOV).format(InstructionFormat.REG_SEG).dest("CS").src("AX").build(), 0));
        // out-of-range immediate: RET imm16 must fit in 16 bits
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.RET).format(InstructionFormat.IMM_ONLY).imm(0x10000).build(), 0));
        // relative overflow: rel16 CALL target too far from the instruction address
        assertThrows(EncodeException.class, () -> encoder.encode(new Instruction.Builder(Opcode.CALL).format(InstructionFormat.ADDR_ONLY).addr(100000).build(), 0));
    }
}

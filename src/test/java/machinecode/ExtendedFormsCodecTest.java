package machinecode;

import instruction.Instruction;
import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden vectors for every family added past the initial Phase 4 codec:
 * MOV accumulator/segment/immediate-to-memory, TEST, XCHG memory, shift-by-1,
 * MUL/IMUL/DIV/IDIV, LEA/LDS/LES, IN/OUT, PUSH/POP segment and memory, INT3,
 * and RET imm16. Bytes are derived by hand from the Intel 8086 opcode map
 * (see docs/verification/phase-4-machine-code-vectors.md for provenance),
 * not copied from the encoder under test.
 */
class ExtendedFormsCodecTest {
    private final InstructionParser parser = new InstructionParser();
    private final Intel8086Encoder encoder = new Intel8086Encoder();
    private final Intel8086Decoder decoder = new Intel8086Decoder();

    @Test void movAccumulatorDirectMemoryForms() {
        assertArrayEquals(new byte[] { (byte) 0xA0, 0x34, 0x12 }, bytes("MOV AL, [1234H]"));
        assertArrayEquals(new byte[] { (byte) 0xA1, 0x34, 0x12 }, bytes("MOV AX, [1234H]"));
        assertArrayEquals(new byte[] { (byte) 0xA2, 0x34, 0x12 }, bytes("MOV [1234H], AL"));
        assertArrayEquals(new byte[] { (byte) 0xA3, 0x34, 0x12 }, bytes("MOV [1234H], AX"));
    }

    @Test void movAccumulatorFormsAreCanonicalOverModRm() {
        // A general-register ModR/M encoding of the same operation is a legal
        // alternate encoding, but the accumulator/direct-memory short form is
        // canonical whenever the memory operand has no base/index register.
        byte[] modRmForm = { (byte) 0x8B, 0x06, 0x34, 0x12 }; // MOV AX, [1234H] via 8B, direct address
        DecodedInstruction decoded = decoder.decode(modRmForm, 0);
        assertEquals("AX", decoded.instruction().getDestReg());
        byte[] reencoded = encoder.encode(decoded.instruction(), 0).bytes();
        assertArrayEquals(new byte[] { (byte) 0xA1, 0x34, 0x12 }, reencoded);
    }

    @Test void movSegmentRegisterForms() {
        assertArrayEquals(new byte[] { (byte) 0x8E, (byte) 0xD8 }, bytes("MOV DS, AX"));
        assertArrayEquals(new byte[] { (byte) 0x8C, (byte) 0xD8 }, bytes("MOV AX, DS"));

        DecodedInstruction decoded = decoder.decode(new byte[] { (byte) 0x8E, 0x06, 0x34, 0x12 }, 0);
        assertEquals(Opcode.MOV, decoded.instruction().getOpcode());
        assertEquals("ES", decoded.instruction().getDestReg());
        assertEquals(0x1234, decoded.instruction().getDisplacement());
    }

    @Test void movImmediateToMemory() {
        assertArrayEquals(new byte[] { (byte) 0xC7, 0x07, 0x34, 0x12 }, bytes("MOV WORD [BX], 1234H"));
        assertArrayEquals(new byte[] { (byte) 0xC6, 0x07, 0x7F }, bytes("MOV BYTE [BX], 127"));
    }

    @Test void testAccumulatorAndRegisterImmediateForms() {
        assertArrayEquals(new byte[] { (byte) 0xA8, 0x05 }, bytes("TEST AL, 5"));
        assertArrayEquals(new byte[] { (byte) 0xA9, 0x34, 0x12 }, bytes("TEST AX, 1234H"));
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xC1, 0x05, 0x00 }, bytes("TEST CX, 5"));
        assertArrayEquals(new byte[] { (byte) 0xF6, (byte) 0xC3, 0x05 }, bytes("TEST BL, 5"));
    }

    @Test void testMemoryForms() {
        assertArrayEquals(new byte[] { (byte) 0x85, 0x07 }, bytes("TEST [BX], AX"));
        assertArrayEquals(new byte[] { (byte) 0xF7, 0x07, 0x34, 0x12 }, bytes("TEST WORD [BX], 1234H"));
    }

    @Test void xchgMemoryForms() {
        assertArrayEquals(new byte[] { (byte) 0x87, 0x07 }, bytes("XCHG AX, [BX]"));

        // The source parser only accepts 16-bit XCHG operands; byte-width XCHG
        // is nonetheless a legal 8086 encoding the decoder must still support.
        DecodedInstruction decoded = decoder.decode(new byte[] { (byte) 0x86, 0x07 }, 0);
        assertEquals(Opcode.XCHG, decoded.instruction().getOpcode());
        assertEquals("AL", decoded.instruction().getDestReg());
        assertArrayEquals(new byte[] { (byte) 0x86, 0x07 }, encoder.encode(decoded.instruction(), 0).bytes());
    }

    @Test void shiftByOneRegisterForms() {
        assertArrayEquals(new byte[] { (byte) 0xD1, (byte) 0xE0 }, bytes("SHL AX, 1"));
        assertArrayEquals(new byte[] { (byte) 0xD1, (byte) 0xCB }, bytes("ROR BX, 1"));
        assertArrayEquals(new byte[] { (byte) 0xD0, (byte) 0xD9 }, bytes("RCR CL, 1"));
        assertArrayEquals(new byte[] { (byte) 0xD1, (byte) 0xFA }, bytes("SAR DX, 1"));
    }

    @Test void shiftByClIsRejectedByTheEncoder() {
        // shift_reg's count parameter is a literal that never resolves CL at
        // execution time, so the codec intentionally does not encode it.
        Instruction shlByCl = parser.parseLine("SHL AX, CL");
        assertEquals(-1, shlByCl.getImmediate());
        org.junit.jupiter.api.Assertions.assertThrows(EncodeException.class, () -> encoder.encode(shlByCl, 0));
    }

    @Test void mulDivRegisterForms() {
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xE3 }, bytes("MUL BX"));
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xE9 }, bytes("IMUL CX"));
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xF3 }, bytes("DIV BX"));
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xF9 }, bytes("IDIV CX"));
        assertArrayEquals(new byte[] { (byte) 0xF6, (byte) 0xE3 }, bytes("MUL BL"));
    }

    @Test void leaLdsLesMemoryForms() {
        assertArrayEquals(new byte[] { (byte) 0x8D, 0x00 }, bytes("LEA AX, [BX+SI]"));
        assertArrayEquals(new byte[] { (byte) 0xC5, 0x07 }, bytes("LDS AX, [BX]"));
        assertArrayEquals(new byte[] { (byte) 0xC4, 0x1C }, bytes("LES BX, [SI]"));
    }

    @Test void inOutFixedForms() {
        assertArrayEquals(new byte[] { (byte) 0xE4, 0x40 }, bytes("IN AL, 40H"));
        assertArrayEquals(new byte[] { (byte) 0xE5, 0x40 }, bytes("IN AX, 40H"));
        assertArrayEquals(new byte[] { (byte) 0xEC }, bytes("IN AL, DX"));
        assertArrayEquals(new byte[] { (byte) 0xED }, bytes("IN AX, DX"));
        assertArrayEquals(new byte[] { (byte) 0xE6, 0x40 }, bytes("OUT 40H, AL"));
        assertArrayEquals(new byte[] { (byte) 0xE7, 0x40 }, bytes("OUT 40H, AX"));
        assertArrayEquals(new byte[] { (byte) 0xEE }, bytes("OUT DX, AL"));
        assertArrayEquals(new byte[] { (byte) 0xEF }, bytes("OUT DX, AX"));
    }

    @Test void segmentPushPopForms() {
        assertArrayEquals(new byte[] { 0x06 }, bytes("PUSH ES"));
        assertArrayEquals(new byte[] { 0x0E }, bytes("PUSH CS"));
        assertArrayEquals(new byte[] { 0x16 }, bytes("PUSH SS"));
        assertArrayEquals(new byte[] { 0x1E }, bytes("PUSH DS"));
        assertArrayEquals(new byte[] { 0x07 }, bytes("POP ES"));
        assertArrayEquals(new byte[] { 0x17 }, bytes("POP SS"));
        assertArrayEquals(new byte[] { 0x1F }, bytes("POP DS"));
    }

    @Test void popCsRemainsUnsupported() {
        // 0x0F was reused as the two-byte-opcode escape from the 80286 onward;
        // the source parser already forbids "POP CS" for the same reason.
        org.junit.jupiter.api.Assertions.assertThrows(DecodeException.class, () -> decoder.decode(new byte[] { 0x0F }, 0));
    }

    @Test void memoryPushPopForms() {
        assertArrayEquals(new byte[] { (byte) 0xFF, 0x37 }, bytes("PUSH [BX]"));
        assertArrayEquals(new byte[] { (byte) 0x8F, 0x07 }, bytes("POP [BX]"));
    }

    @Test void int3IsCanonicalOverIntThree() {
        assertArrayEquals(new byte[] { (byte) 0xCC }, bytes("INT 3"));
        DecodedInstruction viaCc = decoder.decode(new byte[] { (byte) 0xCC }, 0);
        assertEquals(Opcode.INT, viaCc.instruction().getOpcode());
        assertEquals(3, viaCc.instruction().getImmediate());
        assertEquals(1, viaCc.length());

        // CD 03 is a legal, non-canonical alternate encoding of the same
        // semantic instruction; the encoder re-canonicalizes it to CC.
        DecodedInstruction viaCd = decoder.decode(new byte[] { (byte) 0xCD, 0x03 }, 0);
        assertEquals(Opcode.INT, viaCd.instruction().getOpcode());
        assertArrayEquals(new byte[] { (byte) 0xCC }, encoder.encode(viaCd.instruction(), 0).bytes());
    }

    @Test void retImm16() {
        DecodedInstruction decoded = decoder.decode(new byte[] { (byte) 0xC2, 0x04, 0x00 }, 0);
        assertEquals(Opcode.RET, decoded.instruction().getOpcode());
        assertEquals(4, decoded.instruction().getImmediate());
        assertArrayEquals(new byte[] { (byte) 0xC2, 0x04, 0x00 }, encoder.encode(decoded.instruction(), 0).bytes());
    }

    @Test void memoryUnaryForms() {
        assertArrayEquals(new byte[] { (byte) 0xFF, 0x07 }, bytes("INC WORD [BX]"));
        assertArrayEquals(new byte[] { (byte) 0xFF, 0x0F }, bytes("DEC WORD [BX]"));
        assertArrayEquals(new byte[] { (byte) 0xF7, 0x17 }, bytes("NOT WORD [BX]"));
        assertArrayEquals(new byte[] { (byte) 0xF7, 0x1F }, bytes("NEG WORD [BX]"));
        assertArrayEquals(new byte[] { (byte) 0xF6, 0x17 }, bytes("NOT BYTE [BX]"));
    }

    private byte[] bytes(String source) {
        return encoder.encode(parser.parseLine(source), 0).bytes();
    }
}

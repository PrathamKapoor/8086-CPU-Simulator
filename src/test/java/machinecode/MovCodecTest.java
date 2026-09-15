package machinecode;

import instruction.Instruction;
import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MovCodecTest {
    private final InstructionParser parser = new InstructionParser();
    private final Intel8086Encoder encoder = new Intel8086Encoder();
    private final Intel8086Decoder decoder = new Intel8086Decoder();

    @Test
    void encodesCanonicalMovFormsAndNoOperandInstructions() {
        assertArrayEquals(new byte[] { (byte) 0x89, (byte) 0xD8 }, bytes("MOV AX, BX"));
        assertArrayEquals(new byte[] { (byte) 0xB0, 0x7F }, bytes("MOV AL, 127"));
        assertArrayEquals(new byte[] { (byte) 0xB8, 0x34, 0x12 }, bytes("MOV AX, 1234H"));
        assertArrayEquals(new byte[] { (byte) 0x8B, 0x40, (byte) 0xFE }, bytes("MOV AX, [BX+SI-2]"));
        assertArrayEquals(new byte[] { (byte) 0x90 }, bytes("NOP"));
        assertArrayEquals(new byte[] { (byte) 0xF4 }, bytes("HLT"));
    }

    @Test
    void decodesExactLengthAndSemanticOperands() {
        DecodedInstruction decoded = decoder.decode(new byte[] { (byte) 0x8B, 0x40, (byte) 0xFE, (byte) 0xF4 }, 0);
        Instruction instruction = decoded.instruction();

        assertEquals(3, decoded.length());
        assertEquals(3, decoded.nextOffset());
        assertArrayEquals(new byte[] { (byte) 0x8B, 0x40, (byte) 0xFE }, decoded.rawBytes());
        assertEquals(Opcode.MOV, instruction.getOpcode());
        assertEquals("AX", instruction.getDestReg());
        assertEquals("BX", instruction.getBaseReg());
        assertEquals("SI", instruction.getIndexReg());
        assertEquals(-2, instruction.getDisplacement());
        assertArrayEquals(new byte[] { (byte) 0x8B, 0x40, (byte) 0xFE }, instruction.getEncoded());
        assertEquals("MOV AX, [BX+SI-2]", new CanonicalDisassembler().disassemble(decoded));
    }

    @Test
    void rejectsUnknownAndTruncatedOpcodes() {
        assertThrows(DecodeException.class, () -> decoder.decode(new byte[] { 0x0F }, 0));
        assertThrows(DecodeException.class, () -> decoder.decode(new byte[] { (byte) 0xB8, 0x34 }, 0));
    }

    private byte[] bytes(String source) {
        return encoder.encode(parser.parseLine(source), 0).bytes();
    }
}

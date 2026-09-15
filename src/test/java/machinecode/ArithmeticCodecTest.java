package machinecode;

import instruction.Instruction;
import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArithmeticCodecTest {
    private final InstructionParser parser = new InstructionParser();
    private final Intel8086Encoder encoder = new Intel8086Encoder();
    private final Intel8086Decoder decoder = new Intel8086Decoder();

    @Test
    void encodes8086AluRegisterAndImmediateFamiliesSystematically() {
        assertArrayEquals(new byte[] { 0x01, (byte) 0xD8 }, bytes("ADD AX, BX"));
        assertArrayEquals(new byte[] { 0x11, (byte) 0xD8 }, bytes("ADC AX, BX"));
        assertArrayEquals(new byte[] { 0x29, (byte) 0xD8 }, bytes("SUB AX, BX"));
        assertArrayEquals(new byte[] { 0x19, (byte) 0xD8 }, bytes("SBB AX, BX"));
        assertArrayEquals(new byte[] { 0x21, (byte) 0xD8 }, bytes("AND AX, BX"));
        assertArrayEquals(new byte[] { 0x09, (byte) 0xD8 }, bytes("OR AX, BX"));
        assertArrayEquals(new byte[] { 0x31, (byte) 0xD8 }, bytes("XOR AX, BX"));
        assertArrayEquals(new byte[] { 0x39, (byte) 0xD8 }, bytes("CMP AX, BX"));
        assertArrayEquals(new byte[] { (byte) 0x81, (byte) 0xC0, 0x34, 0x12 }, bytes("ADD AX, 1234H"));
    }

    @Test
    void decodesAluFormsBackIntoTheExistingSemanticInstruction() {
        DecodedInstruction decoded = decoder.decode(new byte[] { (byte) 0x81, (byte) 0xE8, 0x34, 0x12 }, 0);
        Instruction instruction = decoded.instruction();

        assertEquals(Opcode.SUB, instruction.getOpcode());
        assertEquals("AX", instruction.getDestReg());
        assertEquals(0x1234, instruction.getImmediate());
        assertEquals(4, decoded.length());
    }

    private byte[] bytes(String source) {
        return encoder.encode(parser.parseLine(source), 0).bytes();
    }
}

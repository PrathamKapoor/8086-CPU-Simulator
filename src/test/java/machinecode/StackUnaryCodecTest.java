package machinecode;

import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StackUnaryCodecTest {
    private final InstructionParser parser = new InstructionParser();
    private final Intel8086Encoder encoder = new Intel8086Encoder();
    private final Intel8086Decoder decoder = new Intel8086Decoder();

    @Test
    void encodesRegisterStackAndUnaryForms() {
        assertArrayEquals(new byte[] { 0x40 }, encode("INC AX"));
        assertArrayEquals(new byte[] { 0x4B }, encode("DEC BX"));
        assertArrayEquals(new byte[] { 0x51 }, encode("PUSH CX"));
        assertArrayEquals(new byte[] { 0x5F }, encode("POP DI"));
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xD8 }, encode("NEG AX"));
        assertArrayEquals(new byte[] { (byte) 0xF7, (byte) 0xD0 }, encode("NOT AX"));
    }

    @Test
    void decodesRegisterStackAndUnaryForms() {
        assertEquals(Opcode.INC, decoder.decode(new byte[] { 0x46 }, 0).instruction().getOpcode());
        assertEquals("SI", decoder.decode(new byte[] { 0x46 }, 0).instruction().getDestReg());
        assertEquals(Opcode.PUSH, decoder.decode(new byte[] { 0x53 }, 0).instruction().getOpcode());
        assertEquals("BX", decoder.decode(new byte[] { 0x53 }, 0).instruction().getDestReg());
        assertEquals(Opcode.NEG, decoder.decode(new byte[] { (byte) 0xF7, (byte) 0xDB }, 0).instruction().getOpcode());
    }

    private byte[] encode(String text) { return encoder.encode(parser.parseLine(text), 0).bytes(); }
}

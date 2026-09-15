package machinecode;

import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedOpcodeCodecTest {
    private final InstructionParser parser = new InstructionParser();
    private final Intel8086Encoder encoder = new Intel8086Encoder();
    private final Intel8086Decoder decoder = new Intel8086Decoder();

    @Test
    void encodesFixed8086OpcodeFamilies() {
        assertArrayEquals(new byte[] { (byte) 0xF8 }, bytes("CLC"));
        assertArrayEquals(new byte[] { (byte) 0x9C }, bytes("PUSHF"));
        assertArrayEquals(new byte[] { 0x37 }, bytes("AAA"));
        assertArrayEquals(new byte[] { (byte) 0xD4, 0x0A }, bytes("AAM"));
        assertArrayEquals(new byte[] { (byte) 0xCD, 0x21 }, bytes("INT 21H"));
        assertArrayEquals(new byte[] { (byte) 0xCF }, bytes("IRET"));
        assertArrayEquals(new byte[] { (byte) 0xA4 }, bytes("MOVSB"));
        assertArrayEquals(new byte[] { (byte) 0xAF }, bytes("SCASW"));
    }

    @Test
    void decodesFixedOpcodeLengthsAndImmediates() {
        DecodedInstruction aam = decoder.decode(new byte[] { (byte) 0xD4, 0x0A, (byte) 0xF4 }, 0);
        DecodedInstruction interrupt = decoder.decode(new byte[] { (byte) 0xCD, 0x21 }, 0);

        assertEquals(Opcode.AAM, aam.instruction().getOpcode());
        assertEquals(10, aam.instruction().getImmediate());
        assertEquals(2, aam.length());
        assertEquals(Opcode.INT, interrupt.instruction().getOpcode());
        assertEquals(0x21, interrupt.instruction().getImmediate());
        assertEquals("INT 21H", interrupt.instruction().getRawText());
        assertEquals(2, interrupt.length());
    }

    @Test
    void encodesAndDecodesRepeatPrefixesExplicitly() {
        var repeated = parser.parseProgram("REP\nMOVSB\n").get(0);
        assertArrayEquals(new byte[] { (byte) 0xF3, (byte) 0xA4 }, encoder.encode(repeated, 0).bytes());

        DecodedInstruction decoded = decoder.decode(new byte[] { (byte) 0xF3, (byte) 0xA4 }, 0);
        assertEquals(Opcode.MOVSB, decoded.instruction().getOpcode());
        assertEquals(Opcode.REP, decoded.instruction().getPrefix());
        assertEquals(2, decoded.length());
    }

    private byte[] bytes(String source) { return encoder.encode(parser.parseLine(source), 0).bytes(); }
}

package machinecode;

import instruction.Instruction;
import instruction.InstructionParser;
import instruction.Opcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ControlTransferCodecTest {
    private final Intel8086Encoder encoder = new Intel8086Encoder();
    private final Intel8086Decoder decoder = new Intel8086Decoder();
    private final InstructionParser parser = new InstructionParser();

    @Test
    void calculatesRelativeOffsetsFromTheAddressAfterTheInstruction() {
        assertArrayEquals(new byte[] { (byte) 0xEB, 0x03 }, encoder.encode(parser.parseLine("JMP 5"), 0).bytes());
        assertArrayEquals(new byte[] { 0x74, 0x03 }, encoder.encode(parser.parseLine("JZ 5"), 0).bytes());
        assertArrayEquals(new byte[] { (byte) 0xE2, 0x03 }, encoder.encode(parser.parseLine("LOOP 5"), 0).bytes());
        assertArrayEquals(new byte[] { (byte) 0xE8, 0x07, 0x00 }, encoder.encode(parser.parseLine("CALL 10"), 0).bytes());
    }

    @Test
    void rejectsOutOfRangeShortControlTransfers() {
        assertThrows(EncodeException.class, () -> encoder.encode(parser.parseLine("JZ 300"), 0));
        assertThrows(EncodeException.class, () -> encoder.encode(parser.parseLine("LOOP 300"), 0));
    }

    @Test
    void decodesRelativeTargetsUsingStreamPositionAfterTheInstruction() {
        DecodedInstruction jump = decoder.decode(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xEB, (byte) 0xFE }, 10);
        Instruction instruction = jump.instruction();

        assertEquals(Opcode.JMP, instruction.getOpcode());
        assertEquals(10, instruction.getAddress());
        assertEquals(2, jump.length());
        assertEquals(12, jump.nextOffset());
    }
}

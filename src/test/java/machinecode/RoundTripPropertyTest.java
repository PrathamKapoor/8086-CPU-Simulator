package machinecode;

import instruction.InstructionParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RoundTripPropertyTest {
    @Test
    void supportedCanonicalInstructionsHaveByteIdentityAfterDecodeAndReencode() {
        InstructionParser parser = new InstructionParser();
        Intel8086Encoder encoder = new Intel8086Encoder();
        Intel8086Decoder decoder = new Intel8086Decoder();
        String[] corpus = {
            "NOP", "HLT", "MOV AX, BX", "MOV AL, 7", "MOV AX, 1234H",
            "ADD AX, BX", "SUB AX, 1", "XOR AL, AL", "PUSH AX", "POP DI",
            "INC AX", "DEC BX", "NEG AX", "NOT AX", "CLC", "PUSHF", "MOVSB",
            "INT 21H", "JMP 5", "JZ 5", "CALL 10"
        };
        for (String source : corpus) {
            byte[] first = encoder.encode(parser.parseLine(source), 0).bytes();
            var decoded = decoder.decode(first, 0);
            byte[] second = encoder.encode(decoded.instruction(), 0).bytes();
            assertArrayEquals(first, second, source);
        }
    }

    @Test
    void exhaustivelyPreservesAllModRmFieldCombinations() {
        for (int mod = 0; mod < 4; mod++) {
            for (int reg = 0; reg < 8; reg++) {
                for (int rm = 0; rm < 8; rm++) {
                    byte[] bytes = { (byte) ((mod << 6) | (reg << 3) | rm), 0x34, 0x12 };
                    ModRm decoded = ModRm.decode(new ByteCursor(bytes, 0));
                    if (mod == 0 && rm == 6) {
                        assertArrayEquals(new byte[] { bytes[0], 0x34, 0x12 }, decoded.toBytes());
                    } else if (mod == 1) {
                        assertArrayEquals(new byte[] { bytes[0], 0x34 }, decoded.toBytes());
                    } else if (mod == 2) {
                        assertArrayEquals(new byte[] { bytes[0], 0x34, 0x12 }, decoded.toBytes());
                    } else {
                        assertArrayEquals(new byte[] { bytes[0] }, decoded.toBytes());
                    }
                }
            }
        }
    }
}

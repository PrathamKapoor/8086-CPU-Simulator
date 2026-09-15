package machinecode;

import cpu.CPU;
import cpu.microarchitecture.MicroarchitectureEventType;
import instruction.Opcode;
import org.junit.jupiter.api.Test;
import simulator.profiler.TimingModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstructionStreamTest {
    @Test
    void decodesBackToBackVariableLengthInstructionsAtExactBoundaries() {
        List<DecodedInstruction> decoded = new Intel8086Decoder().decodeAll(new byte[] {
            (byte) 0xB8, 0x34, 0x12, (byte) 0xEB, 0x01, (byte) 0xF4, (byte) 0xF4
        });

        assertEquals(4, decoded.size());
        assertEquals(Opcode.MOV, decoded.get(0).instruction().getOpcode());
        assertEquals(3, decoded.get(0).nextOffset());
        assertEquals(Opcode.JMP, decoded.get(1).instruction().getOpcode());
        assertEquals(5, decoded.get(1).nextOffset());
        assertEquals(5, decoded.get(2).startOffset());
        assertEquals(6, decoded.get(2).nextOffset());
    }

    /**
     * One deterministic stream covering every structural shape the decoder
     * must consume exactly: a bare one-byte opcode, an immediate-only form, a
     * ModR/M form with an 8-bit displacement, a ModR/M form with none, a
     * relative branch, a segment-override-prefixed ModR/M form, and a
     * repeat-prefixed string op. No instruction may over-read into the next
     * one's opcode byte, or under-read and leave a partial instruction behind.
     */
    @Test
    void decodesAMixedStreamOfEveryStructuralShapeAtExactOffsets() {
        byte[] stream = {
            /* offset 0, len 1  */ (byte) 0x90,                         // NOP
            /* offset 1, len 3  */ (byte) 0xB8, 0x34, 0x12,             // MOV AX, 1234H (immediate)
            /* offset 4, len 3  */ (byte) 0x8B, 0x40, (byte) 0xFE,      // MOV AX, [BX+SI-2] (ModR/M + disp8)
            /* offset 7, len 2  */ (byte) 0x03, 0x07,                   // ADD AX, [BX] (ModR/M, no disp)
            /* offset 9, len 2  */ (byte) 0xEB, 0x02,                   // JMP SHORT +2 (relative)
            /* offset 11, len 3 */ 0x26, (byte) 0x8B, 0x07,             // ES: MOV AX, [BX] (segment override + ModR/M)
            /* offset 14, len 2 */ (byte) 0xF3, (byte) 0xAA,            // REP STOSB (repeat prefix)
            /* offset 16, len 1 */ (byte) 0xF4                          // HLT
        };

        List<DecodedInstruction> decoded = new Intel8086Decoder().decodeAll(stream);

        assertEquals(8, decoded.size());
        int[] expectedStarts = { 0, 1, 4, 7, 9, 11, 14, 16 };
        int[] expectedLengths = { 1, 3, 3, 2, 2, 3, 2, 1 };
        Opcode[] expectedOpcodes = {
            Opcode.NOP, Opcode.MOV, Opcode.MOV, Opcode.ADD, Opcode.JMP, Opcode.MOV, Opcode.STOSB, Opcode.HLT
        };
        for (int i = 0; i < decoded.size(); i++) {
            DecodedInstruction instr = decoded.get(i);
            assertEquals(expectedStarts[i], instr.startOffset(), "startOffset[" + i + "]");
            assertEquals(expectedLengths[i], instr.length(), "length[" + i + "]");
            assertEquals(expectedStarts[i] + expectedLengths[i], instr.nextOffset(), "nextOffset[" + i + "]");
            assertEquals(expectedOpcodes[i], instr.instruction().getOpcode(), "opcode[" + i + "]");
        }
        assertEquals("ES", decoded.get(5).instruction().getSegmentOverride());
        assertEquals(Opcode.REP, decoded.get(6).instruction().getPrefix());
        assertEquals(stream.length, decoded.get(decoded.size() - 1).nextOffset());
        assertTrue(decoded.stream().allMatch(d -> d.rawBytes().length == d.length()));
    }

    @Test
    void cpuLoadedFromTheMixedStreamFetchesThroughTheBiuAndHalts() {
        // Same shapes as above, but with the JMP SHORT retargeted to land
        // exactly on the HLT boundary (offset 16) so this also doubles as a
        // check that a relative branch computed from raw bytes still lines up
        // with an instruction boundary once translated for the BIU/program model.
        byte[] stream = {
            (byte) 0x90,
            (byte) 0xB8, 0x34, 0x12,
            (byte) 0x8B, 0x40, (byte) 0xFE,
            (byte) 0x03, 0x07,
            (byte) 0xEB, 0x05,
            0x26, (byte) 0x8B, 0x07,
            (byte) 0xF3, (byte) 0xAA,
            (byte) 0xF4
        };
        CPU machineCpu = new CPU();
        machineCpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        machineCpu.loadMachineCode(stream);
        int guard = 0;
        while (!machineCpu.isHalted() && guard++ < 500) machineCpu.step();

        assertTrue(machineCpu.isHalted());
        // The jump skips the ES:/REP instructions (offsets 11-15), so the BIU
        // never needs to fetch them — bytes actually consumed only covers the
        // instructions on the taken path: NOP, MOV, MOV, ADD, JMP, HLT.
        assertEquals(1 + 3 + 3 + 2 + 2 + 1, machineCpu.getBytesConsumed());
        assertTrue(machineCpu.getCycleTrace().stream().flatMap(s -> s.events().stream())
            .anyMatch(e -> e.type() == MicroarchitectureEventType.FETCH_BYTE && e.value() == 0x90));
    }
}

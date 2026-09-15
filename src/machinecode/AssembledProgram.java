package machinecode;

import instruction.Instruction;

import java.util.Arrays;
import java.util.List;

/** Immutable assembled byte stream and its instruction-boundary map. */
public record AssembledProgram(byte[] bytes, List<Integer> instructionOffsets, List<Instruction> instructions) {
    public AssembledProgram {
        bytes = Arrays.copyOf(bytes, bytes.length);
        instructionOffsets = List.copyOf(instructionOffsets);
        instructions = List.copyOf(instructions);
    }
    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
}

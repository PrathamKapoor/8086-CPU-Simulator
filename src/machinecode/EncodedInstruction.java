package machinecode;

import instruction.Instruction;

import java.util.Arrays;

/** Immutable result of encoding one semantic instruction. */
public record EncodedInstruction(Instruction instruction, byte[] bytes) {
    public EncodedInstruction {
        if (instruction == null) throw new IllegalArgumentException("instruction must not be null");
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("encoded instruction must contain bytes");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    public int length() { return bytes.length; }
}

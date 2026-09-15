package machinecode;

import instruction.Instruction;

import java.util.Arrays;
import java.util.List;

/** One complete decoded instruction and the stream boundary it consumed. */
public record DecodedInstruction(int startOffset, byte[] rawBytes, int length,
                                 Instruction instruction, List<Integer> prefixes) {
    public DecodedInstruction {
        if (startOffset < 0) throw new IllegalArgumentException("start offset must not be negative");
        if (rawBytes == null || rawBytes.length != length || length == 0) {
            throw new IllegalArgumentException("raw byte count must equal a non-zero instruction length");
        }
        if (instruction == null) throw new IllegalArgumentException("instruction must not be null");
        rawBytes = Arrays.copyOf(rawBytes, rawBytes.length);
        prefixes = List.copyOf(prefixes == null ? List.of() : prefixes);
    }

    @Override public byte[] rawBytes() { return Arrays.copyOf(rawBytes, rawBytes.length); }
    public int nextOffset() { return startOffset + length; }
}

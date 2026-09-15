package debugger;

/**
 * Where execution currently is, in every coordinate system this simulator
 * has a canonical identity for. {@code instructionIndex} is the simulator's
 * true execution identity (it is what {@code IP} holds and what
 * {@code ControlUnit}/{@code CPU.primeNextInstruction} dispatch on) for both
 * source-token and machine-code programs. {@code machineByteOffset} is only
 * meaningful for a machine-code-loaded program; it is {@code -1} otherwise.
 */
public record ExecutionPosition(
    int instructionIndex,
    int machineByteOffset,
    int microOpIndex,
    long totalMicroOpsExecuted,
    boolean instructionBoundary
) {
    public String toJson() {
        return "{\"instructionIndex\":" + instructionIndex
            + ",\"machineByteOffset\":" + machineByteOffset
            + ",\"microOpIndex\":" + microOpIndex
            + ",\"totalMicroOpsExecuted\":" + totalMicroOpsExecuted
            + ",\"instructionBoundary\":" + instructionBoundary + "}";
    }
}

package debugger;

import java.util.List;

/**
 * "Why did this change?" — everything about one retired instruction, derived
 * from the actual recorded trace events for its execution window (never
 * invented/inferred from the mnemonic alone).
 */
public record InstructionExplanation(
    int instructionIndex,
    String sourceText,
    String machineBytesHex,
    List<String> microOps,
    List<String> registersRead,
    List<String> registersWritten,
    List<Integer> memoryAddressesRead,
    List<Integer> memoryAddressesWritten,
    List<String> flagsWritten,
    boolean controlFlowChanged,
    int controlFlowTarget,
    long cycleStart,
    long cycleEnd
) {
    public String toJson() {
        return "{\"instructionIndex\":" + instructionIndex
            + ",\"sourceText\":" + Json.str(sourceText)
            + ",\"machineBytesHex\":" + Json.str(machineBytesHex)
            + ",\"microOps\":" + Json.array(microOps, Json::str)
            + ",\"registersRead\":" + Json.array(registersRead, Json::str)
            + ",\"registersWritten\":" + Json.array(registersWritten, Json::str)
            + ",\"memoryAddressesRead\":" + Json.array(memoryAddressesRead, String::valueOf)
            + ",\"memoryAddressesWritten\":" + Json.array(memoryAddressesWritten, String::valueOf)
            + ",\"flagsWritten\":" + Json.array(flagsWritten, Json::str)
            + ",\"controlFlowChanged\":" + controlFlowChanged
            + ",\"controlFlowTarget\":" + controlFlowTarget
            + ",\"cycleStart\":" + cycleStart
            + ",\"cycleEnd\":" + cycleEnd + "}";
    }
}

package debugger;

/**
 * A single typed, structured trace event. Deliberately not a formatted
 * string: every field a consumer (CLI, JSON API, GUI trace panel, "why did
 * this change" explainer) might need is a real field.
 */
public sealed interface TraceEvent {
    long sequence();
    long cycle();
    String toJson();

    record InstructionStart(long sequence, long cycle, int instructionIndex, int machineByteOffset,
                             String instructionText) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"INSTRUCTION_START\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex + ",\"machineByteOffset\":" + machineByteOffset
                + ",\"instructionText\":" + Json.str(instructionText) + "}";
        }
    }

    record InstructionRetired(long sequence, long cycle, int instructionIndex,
                               String instructionText) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"INSTRUCTION_RETIRED\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex
                + ",\"instructionText\":" + Json.str(instructionText) + "}";
        }
    }

    record MicroOpExecuted(long sequence, long cycle, int instructionIndex, String microOpType,
                            String rtlDescription, String destReg, String srcReg) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"MICRO_OP\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex + ",\"microOpType\":" + Json.str(microOpType)
                + ",\"rtlDescription\":" + Json.str(rtlDescription)
                + ",\"destReg\":" + Json.str(destReg) + ",\"srcReg\":" + Json.str(srcReg) + "}";
        }
    }

    record RegisterMutation(long sequence, long cycle, int instructionIndex, String register,
                             int oldValue, int newValue) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"REGISTER_MUTATION\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex + ",\"register\":" + Json.str(register)
                + ",\"oldValue\":" + oldValue + ",\"newValue\":" + newValue + "}";
        }
    }

    record FlagMutation(long sequence, long cycle, int instructionIndex, String flag,
                         boolean oldValue, boolean newValue) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"FLAG_MUTATION\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex + ",\"flag\":" + Json.str(flag)
                + ",\"oldValue\":" + oldValue + ",\"newValue\":" + newValue + "}";
        }
    }

    record MemoryWriteEvent(long sequence, long cycle, int instructionIndex, int address,
                             boolean hadPriorValue, int oldValue, int newValue) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"MEMORY_WRITE\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex + ",\"address\":" + address
                + ",\"hadPriorValue\":" + hadPriorValue + ",\"oldValue\":" + oldValue + ",\"newValue\":" + newValue + "}";
        }
    }

    record MemoryReadEvent(long sequence, long cycle, int instructionIndex, int address,
                            int value) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"MEMORY_READ\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"instructionIndex\":" + instructionIndex + ",\"address\":" + address + ",\"value\":" + value + "}";
        }
    }

    record ControlTransfer(long sequence, long cycle, int fromInstructionIndex, int toInstructionIndex,
                            String opcode) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"CONTROL_TRANSFER\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"fromInstructionIndex\":" + fromInstructionIndex + ",\"toInstructionIndex\":" + toInstructionIndex
                + ",\"opcode\":" + Json.str(opcode) + "}";
        }
    }

    record BreakpointHitEvent(long sequence, long cycle, int breakpointId, ExecutionPosition position) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"BREAKPOINT_HIT\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"breakpointId\":" + breakpointId + ",\"position\":" + position.toJson() + "}";
        }
    }

    record WatchpointHitEvent(long sequence, long cycle, WatchHit hit) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"WATCHPOINT_HIT\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"hit\":" + hit.toJson() + "}";
        }
    }

    record BiuFetchEvent(long sequence, long cycle, int physicalAddress, int value,
                          int queueSizeAfter) implements TraceEvent {
        public String toJson() {
            return "{\"type\":\"BIU_FETCH\",\"sequence\":" + sequence + ",\"cycle\":" + cycle
                + ",\"physicalAddress\":" + physicalAddress + ",\"value\":" + value
                + ",\"queueSizeAfter\":" + queueSizeAfter + "}";
        }
    }
}

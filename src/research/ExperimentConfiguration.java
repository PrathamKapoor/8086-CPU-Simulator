package research;

import simulator.profiler.TimingModel;

import java.util.List;
import java.util.Map;

/**
 * Everything that controls how a {@link Workload} is executed. Two
 * documented architectural facts shape this type (see
 * docs/verification/phase-6-research-platform-audit.md): the prefetch
 * queue's capacity is a compile-time constant, and program placement is
 * always physical address 0 in this simulator — neither is an
 * instance-configurable parameter anywhere in the frozen Phase 3/4/5 code,
 * so neither is offered here as an experiment axis.
 */
public record ExperimentConfiguration(
    TimingModel timingModel,
    long executionMicroOpLimit,
    boolean tracingEnabled,
    boolean debuggerEnabled,
    List<Integer> breakpointInstructionIndices,
    Map<String, Integer> expectedRegisters
) {
    public static final long DEFAULT_MICRO_OP_LIMIT = 2_000_000L;

    public ExperimentConfiguration {
        breakpointInstructionIndices = List.copyOf(breakpointInstructionIndices == null ? List.of() : breakpointInstructionIndices);
        expectedRegisters = Map.copyOf(expectedRegisters == null ? Map.of() : expectedRegisters);
        if (executionMicroOpLimit <= 0) throw new IllegalArgumentException("executionMicroOpLimit must be positive");
    }

    /** Timing mode SIMPLIFIED_8086, debugger/trace off, no execution limit surprises, no expectations asserted. */
    public static ExperimentConfiguration timed() {
        return new ExperimentConfiguration(TimingModel.SIMPLIFIED_8086, DEFAULT_MICRO_OP_LIMIT, false, false, List.of(), Map.of());
    }

    public static ExperimentConfiguration timed(Map<String, Integer> expectedRegisters) {
        return new ExperimentConfiguration(TimingModel.SIMPLIFIED_8086, DEFAULT_MICRO_OP_LIMIT, false, false, List.of(), expectedRegisters);
    }

    public ExperimentConfiguration withTracing() {
        return new ExperimentConfiguration(timingModel, executionMicroOpLimit, true, debuggerEnabled, breakpointInstructionIndices, expectedRegisters);
    }

    public ExperimentConfiguration withTimingModel(TimingModel model) {
        return new ExperimentConfiguration(model, executionMicroOpLimit, tracingEnabled, debuggerEnabled, breakpointInstructionIndices, expectedRegisters);
    }

    /** Stable, order-independent description used both for display and (indirectly) for hashing. */
    public String describe() {
        return "timing=" + timingModel + ",limit=" + executionMicroOpLimit + ",trace=" + tracingEnabled + ",debugger=" + debuggerEnabled;
    }
}

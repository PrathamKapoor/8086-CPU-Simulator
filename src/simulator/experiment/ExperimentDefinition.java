package simulator.experiment;

import java.util.Map;
import simulator.profiler.TimingModel;

/** Compact reproducible experiment declaration; expected registers are architectural assertions. */
public record ExperimentDefinition(String name, String source, TimingModel timingModel,
                                   Map<String, Integer> expectedRegisters, boolean includeTrace) {
    public ExperimentDefinition { expectedRegisters = Map.copyOf(expectedRegisters); }
}

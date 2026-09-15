package simulator.experiment;

import cpu.CPU;
import simulator.profiler.PerformanceSnapshot;

public record ExperimentResult(ExperimentDefinition definition, CPU cpu, PerformanceSnapshot metrics,
                               boolean architecturalResultMatches) {
    public String toJson() { return "{\"name\":\""+definition.name()+"\",\"timing\":\""+definition.timingModel()+"\",\"architecturalResultMatches\":"+architecturalResultMatches+",\"metrics\":"+metrics.toJson()+"}"; }
}

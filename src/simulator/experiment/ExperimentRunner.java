package simulator.experiment;

import cpu.CPU;
import instruction.InstructionParser;
import simulator.profiler.PerformanceProfiler;

public final class ExperimentRunner {
    private ExperimentRunner() { }
    public static ExperimentResult run(ExperimentDefinition definition) {
        CPU cpu = new CPU(); cpu.setTimingModel(definition.timingModel());
        cpu.loadProgram(new InstructionParser().parseProgram(definition.source()));
        int guard=0; while (!cpu.isHalted() && guard++ < 10000) cpu.step();
        boolean matches = cpu.isHalted() && definition.expectedRegisters().entrySet().stream().allMatch(e -> cpu.getRegister(e.getKey()).output()==(e.getValue()&0xFFFF));
        return new ExperimentResult(definition,cpu,new PerformanceProfiler(cpu).metrics(),matches);
    }
}

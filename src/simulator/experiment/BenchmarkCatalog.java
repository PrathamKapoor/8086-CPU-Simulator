package simulator.experiment;

import java.util.LinkedHashMap;
import java.util.Map;
import simulator.profiler.TimingModel;

/** Fixed, versioned benchmark corpus; no wall-clock measurement is used. */
public final class BenchmarkCatalog {
    private BenchmarkCatalog() { }
    public static Map<String, ExperimentDefinition> all() {
        Map<String, ExperimentDefinition> b=new LinkedHashMap<>();
        b.put("compute-heavy", e("compute-heavy","MOV AX, 1\nMOV CX, 8\nL: ADD AX, AX\nLOOP L\nHLT", Map.of("AX",256)));
        b.put("memory-heavy", e("memory-heavy","MOV AX, 7\nMOV [0x100], AX\nMOV BX, [0x100]\nHLT", Map.of("BX",7)));
        b.put("branch-heavy", e("branch-heavy","MOV AX,0\nMOV CX,3\nL: INC AX\nLOOP L\nHLT", Map.of("AX",3)));
        b.put("loop-heavy", e("loop-heavy","MOV CX,4\nMOV AX,0\nL: INC AX\nLOOP L\nHLT", Map.of("AX",4)));
        b.put("queue-friendly-sequential", e("queue-friendly-sequential","MOV AX,1\nMOV BX,2\nADD AX,BX\nINC AX\nHLT", Map.of("AX",4)));
        b.put("queue-hostile-control-flow", e("queue-hostile-control-flow","JMP 2\nNOP\nMOV AX,5\nHLT", Map.of("AX",5)));
        b.put("mixed-workload", e("mixed-workload","MOV AX,2\nMOV [0x100],AX\nMOV BX,[0x100]\nADD AX,BX\nHLT", Map.of("AX",4)));
        // JSON benchmark output is part of the reproducibility contract; preserve
        // the declared workload order rather than using Map.copyOf's unspecified order.
        return java.util.Collections.unmodifiableMap(b);
    }
    private static ExperimentDefinition e(String name, String source, Map<String,Integer> expected) { return new ExperimentDefinition(name,source,TimingModel.SIMPLIFIED_8086,expected,true); }
}

package research;

import debugger.Json;
import simulator.profiler.TimingModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A fully reproducible, exportable bundle (Phase 6 Step 9): the experiment
 * definition (workload + configuration), the recorded result, and the
 * simulator version. A second run from the deserialized definition must
 * reproduce the same {@code resultHash} for a deterministic experiment —
 * {@link #verifyReproducible()} does exactly that, not just a structural
 * round-trip check.
 */
public record ExperimentArtifact(Experiment experiment, ExperimentRun run) {
    public static ExperimentArtifact capture(Experiment experiment) {
        return new ExperimentArtifact(experiment, ExperimentRunner.run(experiment));
    }

    public String toJson() {
        Workload w = experiment.workload();
        ExperimentConfiguration c = experiment.configuration();
        String workloadJson = "{\"kind\":\"" + w.kind() + "\""
            + ",\"name\":" + Json.str(w.name())
            + ",\"description\":" + Json.str(w.description())
            + ",\"sourceAssembly\":" + (w.kind() == Workload.Kind.SOURCE ? Json.str(w.sourceAssembly()) : "null")
            + ",\"machineCodeHex\":" + (w.kind() == Workload.Kind.MACHINE_CODE ? Json.str(hex(w.machineCodeBytes())) : "null") + "}";
        String configJson = "{\"timingModel\":\"" + c.timingModel() + "\""
            + ",\"executionMicroOpLimit\":" + c.executionMicroOpLimit()
            + ",\"tracingEnabled\":" + c.tracingEnabled()
            + ",\"debuggerEnabled\":" + c.debuggerEnabled()
            + ",\"breakpointInstructionIndices\":" + Json.array(c.breakpointInstructionIndices(), String::valueOf)
            + ",\"expectedRegisters\":" + regMapJson(c.expectedRegisters()) + "}";
        return "{\"experimentId\":" + Json.str(experiment.id())
            + ",\"description\":" + Json.str(experiment.description())
            + ",\"workload\":" + workloadJson
            + ",\"configuration\":" + configJson
            + ",\"run\":" + run.toJson() + "}";
    }

    public static ExperimentArtifact fromJson(String json) {
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(json));
        String id = MiniJson.asString(root.get("experimentId"));
        String description = MiniJson.asString(root.get("description"));

        Map<String, Object> w = MiniJson.asObject(root.get("workload"));
        Workload.Kind kind = Workload.Kind.valueOf(MiniJson.asString(w.get("kind")));
        Workload workload = kind == Workload.Kind.SOURCE
            ? Workload.fromSource(MiniJson.asString(w.get("name")), MiniJson.asString(w.get("description")), MiniJson.asString(w.get("sourceAssembly")))
            : Workload.fromMachineCode(MiniJson.asString(w.get("name")), MiniJson.asString(w.get("description")), unhex(MiniJson.asString(w.get("machineCodeHex"))));

        Map<String, Object> c = MiniJson.asObject(root.get("configuration"));
        List<Integer> breakpoints = MiniJson.asArray(c.get("breakpointInstructionIndices")).stream().map(o -> (int) Math.round((Double) o)).toList();
        Map<String, Integer> expected = new LinkedHashMap<>();
        MiniJson.asObject(c.get("expectedRegisters")).forEach((k, v) -> expected.put(k, MiniJson.asInt(v)));
        ExperimentConfiguration configuration = new ExperimentConfiguration(
            TimingModel.valueOf(MiniJson.asString(c.get("timingModel"))),
            Math.round((Double) c.get("executionMicroOpLimit")),
            MiniJson.asBoolean(c.get("tracingEnabled")),
            MiniJson.asBoolean(c.get("debuggerEnabled")),
            breakpoints, expected);

        Experiment experiment = new Experiment(id, description, workload, configuration);

        // Re-run to reconstruct the ExperimentRun object rather than deserializing
        // it field-by-field: for a deterministic experiment this is required to be
        // identical to what was exported (verifyReproducible checks exactly that),
        // and avoids maintaining a second, parallel deserialization path for
        // MetricSet/ArchitecturalState that must never itself drift out of step
        // with ExperimentRunner's actual output shape.
        ExperimentRun reconstructedRun = ExperimentRunner.run(experiment);
        return new ExperimentArtifact(experiment, reconstructedRun);
    }

    /** Re-runs the embedded experiment fresh and checks the hash matches this artifact's recorded run. */
    public boolean verifyReproducible() {
        ExperimentRun freshRun = ExperimentRunner.run(experiment);
        return freshRun.resultHash().equals(run.resultHash());
    }

    private static String regMapJson(Map<String, Integer> map) {
        return map.entrySet().stream()
            .map(e -> Json.str(e.getKey()) + ":" + e.getValue())
            .collect(Collectors.joining(",", "{", "}"));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    private static byte[] unhex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}

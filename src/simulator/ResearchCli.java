package simulator;

import research.BatchMatrix;
import research.Comparison;
import research.Experiment;
import research.ExperimentArtifact;
import research.ExperimentCatalog;
import research.ExperimentConfiguration;
import research.ExperimentRun;
import research.ExperimentRunner;
import research.EvidenceReport;
import research.RepetitionResult;
import research.Timeline;
import research.Workload;
import simulator.profiler.TimingModel;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic CLI for the Phase 6 research platform, following
 * simulator.DebuggerCli's precedent: a testable core
 * ({@link #executeCommand}/{@link #runScript}) plus a thin {@code main()}.
 * No JavaFX dependency.
 */
public final class ResearchCli {
    private ResearchCli() { }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: ResearchCli experiment <list|show|run|compare|repeat|export|analyze|batch> ...");
            System.exit(2);
        }
        boolean ok = executeCommand(String.join(" ", args), System.out);
        if (!ok) System.exit(1);
    }

    public static void runScript(List<String> commands, PrintStream out) {
        for (String line : commands) executeCommand(line, out);
    }

    /** @return false if the command failed (unknown id, bad arguments, etc.) */
    public static boolean executeCommand(String rawLine, PrintStream out) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) return true;
        String[] parts = line.split("\\s+");
        if (parts.length < 2 || !parts[0].equalsIgnoreCase("experiment")) {
            out.println("unknown command: " + line + " (expected: experiment <subcommand> ...)");
            return false;
        }
        String sub = parts[1].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "list" -> list(out);
                case "show" -> show(out, parts[2]);
                case "run" -> runOne(out, parts[2], parts.length > 3 && parts[3].equalsIgnoreCase("--json"));
                case "compare" -> compare(out, parts[2], parts[3]);
                case "repeat" -> repeat(out, parts[2], Integer.parseInt(parts[3]));
                case "export" -> export(out, parts[2], parts.length > 3 ? parts[3] : null);
                case "analyze" -> analyze(out, parts[2]);
                case "batch" -> batch(out, parts[2], parts[3]);
                default -> { out.println("unknown experiment subcommand: " + sub); return false; }
            }
            return true;
        } catch (RuntimeException | IOException e) {
            out.println("error: " + e.getMessage());
            return false;
        }
    }

    private static void list(PrintStream out) {
        for (String id : ExperimentCatalog.all().keySet()) {
            Experiment e = ExperimentCatalog.get(id);
            out.println(id + " - " + e.description());
        }
    }

    private static void show(PrintStream out, String id) {
        Experiment e = ExperimentCatalog.get(id);
        out.println("id=" + e.id());
        out.println("description=" + e.description());
        out.println("workloadKind=" + e.workload().kind());
        out.println("configuration=" + e.configuration().describe());
    }

    private static void runOne(PrintStream out, String id, boolean json) {
        ExperimentRun run = ExperimentRunner.run(ExperimentCatalog.get(id));
        if (json) {
            out.println(run.toJson());
        } else {
            out.println("passed=" + run.passed() + " resultHash=" + run.resultHash());
            run.metrics().describe().forEach(out::println);
        }
    }

    private static void compare(PrintStream out, String idA, String idB) {
        ExperimentRun a = ExperimentRunner.run(ExperimentCatalog.get(idA));
        ExperimentRun b = ExperimentRunner.run(ExperimentCatalog.get(idB));
        Comparison cmp = Comparison.of(a, b);
        EvidenceReport report = EvidenceReport.from(cmp);
        report.lines().forEach(out::println);
    }

    private static void repeat(PrintStream out, String id, int times) {
        RepetitionResult result = RepetitionResult.run(ExperimentCatalog.get(id), times);
        out.println("repetitions=" + times + " allIdentical=" + result.allIdentical());
        result.statsByMetric().forEach((name, stats) ->
            out.println(name + ": min=" + stats.min() + " max=" + stats.max() + " mean=" + stats.mean() + " median=" + stats.median() + " spread=" + stats.spread()));
    }

    private static void export(PrintStream out, String id, String path) throws IOException {
        ExperimentArtifact artifact = ExperimentArtifact.capture(ExperimentCatalog.get(id));
        String json = artifact.toJson();
        if (path == null) {
            out.println(json);
        } else {
            if (path.endsWith(".csv")) {
                Files.writeString(Path.of(path), toCsv(artifact.run()), StandardCharsets.UTF_8);
            } else {
                Files.writeString(Path.of(path), json, StandardCharsets.UTF_8);
            }
            out.println("exported " + id + " to " + path);
        }
    }

    /** Deterministic CSV: one header row, one data row, metric columns in MetricSet's fixed order. */
    private static String toCsv(ExperimentRun run) {
        StringBuilder header = new StringBuilder("experimentId,resultHash,passed");
        StringBuilder row = new StringBuilder(csvField(run.experiment().id()) + "," + csvField(run.resultHash()) + "," + run.passed());
        for (var metric : run.metrics().all()) {
            header.append(',').append(csvField(metric.name()));
            row.append(',').append(metric.provenance() == research.MetricProvenance.UNAVAILABLE ? "" : metric.value());
        }
        return header + "\n" + row + "\n";
    }

    private static String csvField(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static void analyze(PrintStream out, String id) {
        Experiment e = ExperimentCatalog.get(id);
        cpu.CPU cpu = new cpu.CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        if (e.workload().kind() == Workload.Kind.SOURCE) {
            cpu.loadProgram(new instruction.InstructionParser().parseProgram(e.workload().sourceAssembly()));
        } else {
            cpu.loadMachineCode(e.workload().machineCodeBytes());
        }
        int guard = 0;
        while (!cpu.isHalted() && guard++ < e.configuration().executionMicroOpLimit()) cpu.step();
        Timeline timeline = Timeline.from(cpu);
        out.println(timeline.explainCycleCount());
        out.println("stalledCycleCount=" + timeline.stalledCycles().size());
        out.println("flushCycleCount=" + timeline.flushCycles().size());
    }

    private static void batch(PrintStream out, String idsCsv, String timingModelsCsv) {
        List<Workload> workloads = java.util.Arrays.stream(idsCsv.split(","))
            .map(id -> ExperimentCatalog.get(id).workload()).toList();
        List<BatchMatrix.Labeled<ExperimentConfiguration>> configs = java.util.Arrays.stream(timingModelsCsv.split(","))
            .map(name -> new BatchMatrix.Labeled<>(name, ExperimentConfiguration.timed().withTimingModel(TimingModel.valueOf(name.toUpperCase(Locale.ROOT)))))
            .toList();
        BatchMatrix matrix = BatchMatrix.run(workloads, configs);
        out.println(matrix.toJson());
    }
}

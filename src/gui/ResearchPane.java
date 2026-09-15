package gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Supplier;

/**
 * Phase 6 Step 16: a GUI workspace over the research/experimentation
 * platform. Every control here drives a real {@code research.*} class --
 * {@link ExperimentCatalog}, {@link ExperimentRunner}, {@link Comparison},
 * {@link RepetitionResult}, {@link Timeline}, {@link BatchMatrix} -- the
 * exact engine {@code simulator.ResearchCli} and the research test suite
 * exercise. Nothing here is sample/demo data; every list is populated from
 * an actual {@link ExperimentRun}. This pane does not touch or replace the
 * architecture dashboard, machine-code inspector, or {@link DebuggerPane}.
 */
public final class ResearchPane extends BorderPane {
    private final ComboBox<String> experimentABox = new ComboBox<>();
    private final ComboBox<String> experimentBBox = new ComboBox<>();
    private final TextField repeatCountField = new TextField("5");
    private final TextField batchTimingField = new TextField("FUNCTIONAL,SIMPLIFIED_8086");

    private final ListView<String> catalogList = new ListView<>();
    private final ListView<String> metricsList = new ListView<>();
    private final ListView<String> evidenceList = new ListView<>();
    private final ListView<String> repetitionList = new ListView<>();
    private final ListView<String> timelineList = new ListView<>();
    private final ListView<String> batchList = new ListView<>();

    public ResearchPane(Supplier<String> unusedProgramSource) {
        List<String> ids = List.copyOf(ExperimentCatalog.all().keySet());
        experimentABox.getItems().setAll(ids);
        experimentBBox.getItems().setAll(ids);
        if (!ids.isEmpty()) {
            experimentABox.getSelectionModel().select(0);
            experimentBBox.getSelectionModel().select(ids.size() > 1 ? 1 : 0);
        }
        catalogList.getItems().setAll(ids.stream()
            .map(id -> id + " -- " + ExperimentCatalog.get(id).description())
            .toList());
        catalogList.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int i = newIdx.intValue();
            if (i >= 0 && i < ids.size()) experimentABox.getSelectionModel().select(ids.get(i));
        });

        setTop(buildControls());
        setLeft(buildCatalogPanel());
        setCenter(buildResultTabs());
        setPadding(new Insets(10));
    }

    private HBox buildControls() {
        Button runA = new Button("Run A");
        Button compare = new Button("Compare A vs B");
        Button repeat = new Button("Repeat A");
        Button analyze = new Button("Analyze A timeline");
        Button batch = new Button("Batch A,B x timing models");
        Button exportA = new Button("Export A (JSON)");

        runA.setOnAction(e -> runSelected());
        compare.setOnAction(e -> compareSelected());
        repeat.setOnAction(e -> repeatSelected());
        analyze.setOnAction(e -> analyzeSelected());
        batch.setOnAction(e -> batchSelected());
        exportA.setOnAction(e -> exportSelected());

        repeatCountField.setPrefWidth(50);
        batchTimingField.setPrefWidth(220);

        HBox bar = new HBox(8,
            new Label("A:"), experimentABox, new Label("B:"), experimentBBox,
            runA, compare, new Label("times:"), repeatCountField, repeat, analyze,
            new Label("models:"), batchTimingField, batch, exportA);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 8, 0));
        return bar;
    }

    private VBox buildCatalogPanel() {
        Label title = new Label("Experiment catalog");
        title.getStyleClass().add("section-title");
        catalogList.setPrefWidth(320);
        VBox box = new VBox(6, title, catalogList);
        VBox.setVgrow(catalogList, Priority.ALWAYS);
        box.setPadding(new Insets(0, 8, 0, 0));
        return box;
    }

    private TabPane buildResultTabs() {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
            new Tab("Metrics", metricsList),
            new Tab("Evidence Report (A vs B)", evidenceList),
            new Tab("Repetition / Determinism", repetitionList),
            new Tab("Execution Timeline", timelineList),
            new Tab("Batch Matrix", batchList)
        );
        for (Tab t : tabs.getTabs()) t.setClosable(false);
        for (Tab t : tabs.getTabs()) VBox.setVgrow((ListView<?>) t.getContent(), Priority.ALWAYS);
        return tabs;
    }

    private Experiment selected(ComboBox<String> box) {
        String id = box.getSelectionModel().getSelectedItem();
        return id == null ? null : ExperimentCatalog.get(id);
    }

    private void runSelected() {
        Experiment a = selected(experimentABox);
        if (a == null) return;
        ExperimentRun run = ExperimentRunner.run(a);
        List<String> lines = new java.util.ArrayList<>();
        lines.add("experiment=" + run.experiment().id() + " passed=" + run.passed() + " resultHash=" + run.resultHash());
        lines.add("simulatorVersion=" + run.simulatorVersion() + " executedAt=" + run.executedAt());
        for (var m : run.metrics().all()) lines.add(m.describe());
        metricsList.getItems().setAll(lines);
    }

    private void compareSelected() {
        Experiment a = selected(experimentABox);
        Experiment b = selected(experimentBBox);
        if (a == null || b == null) return;
        Comparison cmp = Comparison.of(ExperimentRunner.run(a), ExperimentRunner.run(b));
        evidenceList.getItems().setAll(EvidenceReport.from(cmp).lines());
    }

    private void repeatSelected() {
        Experiment a = selected(experimentABox);
        if (a == null) return;
        int times;
        try {
            times = Integer.parseInt(repeatCountField.getText().trim());
        } catch (NumberFormatException ex) {
            repetitionList.getItems().setAll(List.of("invalid repeat count: " + repeatCountField.getText()));
            return;
        }
        RepetitionResult result = RepetitionResult.run(a, times);
        List<String> lines = new java.util.ArrayList<>();
        lines.add("repetitions=" + times + " allIdentical=" + result.allIdentical());
        result.statsByMetric().forEach((name, stats) ->
            lines.add(name + ": min=" + stats.min() + " max=" + stats.max() + " mean=" + stats.mean()
                + " median=" + stats.median() + " spread=" + stats.spread()));
        repetitionList.getItems().setAll(lines);
    }

    private void analyzeSelected() {
        Experiment a = selected(experimentABox);
        if (a == null) return;
        cpu.CPU cpu = new cpu.CPU();
        cpu.setTimingModel(TimingModel.SIMPLIFIED_8086);
        if (a.workload().kind() == Workload.Kind.SOURCE) {
            cpu.loadProgram(new instruction.InstructionParser().parseProgram(a.workload().sourceAssembly()));
        } else {
            cpu.loadMachineCode(a.workload().machineCodeBytes());
        }
        int guard = 0;
        while (!cpu.isHalted() && guard++ < (int) Math.min(Integer.MAX_VALUE, a.configuration().executionMicroOpLimit())) cpu.step();
        Timeline timeline = Timeline.from(cpu);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(timeline.explainCycleCount());
        lines.add("stalledCycleCount=" + timeline.stalledCycles().size());
        lines.add("flushCycleCount=" + timeline.flushCycles().size());
        lines.add("-- stalled cycles --");
        for (var entry : timeline.stalledCycles()) {
            lines.add("cycle " + entry.cycle() + " [" + entry.instructionIndex() + "] " + entry.instruction()
                + " biu=" + entry.biuState() + " eu=" + entry.euState());
        }
        timelineList.getItems().setAll(lines);
    }

    private void batchSelected() {
        Experiment a = selected(experimentABox);
        Experiment b = selected(experimentBBox);
        if (a == null || b == null) return;
        List<Workload> workloads = List.of(a.workload(), b.workload());
        List<BatchMatrix.Labeled<ExperimentConfiguration>> configs;
        try {
            configs = java.util.Arrays.stream(batchTimingField.getText().split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(name -> new BatchMatrix.Labeled<>(name,
                    ExperimentConfiguration.timed().withTimingModel(TimingModel.valueOf(name.toUpperCase(java.util.Locale.ROOT)))))
                .toList();
        } catch (IllegalArgumentException ex) {
            batchList.getItems().setAll(List.of("invalid timing model list: " + ex.getMessage()));
            return;
        }
        BatchMatrix matrix = BatchMatrix.run(workloads, configs);
        List<String> lines = new java.util.ArrayList<>();
        for (var cell : matrix.cells()) {
            lines.add(cell.workloadLabel() + " x " + cell.configurationLabel()
                + ": passed=" + cell.run().passed()
                + " totalCycles=" + cell.run().metrics().get("execution.totalCycles").value()
                + " instructionsRetired=" + cell.run().metrics().get("execution.instructionsRetired").value());
        }
        batchList.getItems().setAll(lines);
    }

    private void exportSelected() {
        Experiment a = selected(experimentABox);
        if (a == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(a.id() + ".json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        Window window = getScene() == null ? null : getScene().getWindow();
        java.io.File file = chooser.showSaveDialog(window);
        if (file == null) return;
        try {
            ExperimentArtifact artifact = ExperimentArtifact.capture(a);
            Files.writeString(file.toPath(), artifact.toJson(), StandardCharsets.UTF_8);
            metricsList.getItems().add(0, "exported " + a.id() + " to " + file);
        } catch (IOException ex) {
            metricsList.getItems().add(0, "export failed: " + ex.getMessage());
        }
    }
}

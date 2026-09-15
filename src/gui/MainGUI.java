package gui;

import cpu.CPU;
import cpu.registers.FLAGS;
import cpu.registers.Register;
import instruction.Instruction;
import instruction.InstructionParser;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import microoperation.MicroOperation;
import machinecode.Intel8086Decoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainGUI extends Application {

    private CPU cpu;
    private final InstructionParser parser = new InstructionParser();

    private CpuArchitectureView architectureView;

    private TextArea programEditor;
    private TextField instructionField;
    private Slider speedSlider;

    private Label cycleLabel;
    private Label phaseLabel;
    private Label instructionLabel;
    private Label microOpLabel;
    private Label busLabel;
    private Label statusLabel;

    private ListView<String> queueList;
    private ListView<String> executionLog;
    private ListView<String> microOpLog;
    private ListView<String> microarchitectureTimeline;

    private TableView<KeyValueRow> registerTable;
    private TableView<KeyValueRow> memoryTable;
    private TableView<KeyValueRow> busTable;
    private TableView<KeyValueRow> flagsTable;
    private TableView<KeyValueRow> machineCodeTable;

    private final ObservableList<String> queueItems = FXCollections.observableArrayList();
    private final ObservableList<String> executionItems = FXCollections.observableArrayList();
    private final ObservableList<String> microOpItems = FXCollections.observableArrayList();
    private final ObservableList<String> timelineItems = FXCollections.observableArrayList();
    private final ObservableList<KeyValueRow> registerRows = FXCollections.observableArrayList();
    private final ObservableList<KeyValueRow> memoryRows = FXCollections.observableArrayList();
    private final ObservableList<KeyValueRow> busRows = FXCollections.observableArrayList();
    private final ObservableList<KeyValueRow> flagRows = FXCollections.observableArrayList();
    private final ObservableList<KeyValueRow> machineCodeRows = FXCollections.observableArrayList();

    private Timeline runTimeline;

    @Override
    public void start(Stage stage) {
        cpu = new CPU();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildTopSection());
        root.setCenter(buildCenterSection());
        root.setBottom(buildEditorSection());

        Scene scene = new Scene(root, 1680, 980);
        String css = MainGUI.class.getResource("dashboard.css") != null
            ? MainGUI.class.getResource("dashboard.css").toExternalForm()
            : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        stage.setScene(scene);
        stage.setTitle("8086 CPU Simulator");
        stage.setMinWidth(1380);
        stage.setMinHeight(820);
        stage.show();

        programEditor.setText(defaultProgram());
        refreshAll(null);
        logEvent("Dashboard ready. Load the sample program or add your own instructions.");
    }

    private VBox buildTopSection() {
        VBox top = new VBox(14);
        top.setPadding(new Insets(18, 18, 10, 18));

        HBox header = new HBox(18);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("hero-card");

        VBox titleBox = new VBox(4);
        Label eyebrow = new Label("Interactive Teaching Interface");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("8086 CPU Simulator — EU / BIU / Segment:Offset / Flags");
        title.getStyleClass().add("hero-title");
        Label subtitle = new Label(
            "Full 16-bit ISA: MOV, ADD, SUB, MUL, DIV, SHL, SHR, AND, OR, XOR, NOT, INC, DEC, CMP, JMP, JZ, JNZ, JC, JNC, JO, LOOP, PUSH, POP, CALL, RET, INT, IRET, NOP, HLT. All flags: CF, PF, AF, ZF, SF, OF, DF, IF, TF.");
        subtitle.getStyleClass().add("hero-subtitle");
        subtitle.setWrapText(true);
        titleBox.getChildren().addAll(eyebrow, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox stats = new HBox(10,
            statCard("Cycle", cycleLabel = new Label("0")),
            statCard("Phase", phaseLabel = new Label("IDLE")),
            statCard("Instruction", instructionLabel = new Label("---")),
            statCard("Micro-op", microOpLabel = new Label("---"))
        );
        stats.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(titleBox, spacer, stats);

        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("panel-card");

        Button loadButton = actionButton("Load Program", "accent-button");
        Button addInstructionButton = actionButton("Add Instruction", "secondary-button");
        Button runButton = actionButton("Run", "run-button");
        Button pauseButton = actionButton("Pause", "warn-button");
        Button stepButton = actionButton("Step", "run-button");
        Button resetButton = actionButton("Reset", "danger-button");

        instructionField = new TextField();
        instructionField.setPromptText("Type one instruction, for example: MOV AX, 25");
        instructionField.getStyleClass().add("instruction-field");
        HBox.setHgrow(instructionField, Priority.ALWAYS);

        speedSlider = new Slider(80, 1200, 350);
        speedSlider.setShowTickLabels(false);
        speedSlider.setShowTickMarks(false);
        speedSlider.getStyleClass().add("speed-slider");

        VBox sliderBox = new VBox(4);
        Label sliderLabel = new Label("Speed");
        sliderLabel.getStyleClass().add("field-label");
        sliderBox.getChildren().addAll(sliderLabel, speedSlider);
        sliderBox.setMinWidth(180);

        statusLabel = new Label("Idle");
        statusLabel.getStyleClass().add("status-chip");
        busLabel = new Label("Buses idle");
        busLabel.getStyleClass().add("status-detail");

        VBox statusBox = new VBox(4, statusLabel, busLabel);
        statusBox.setMinWidth(260);

        controls.getChildren().addAll(
            loadButton, addInstructionButton, runButton, pauseButton, stepButton, resetButton,
            new Separator(Orientation.VERTICAL),
            instructionField,
            sliderBox,
            new Separator(Orientation.VERTICAL),
            statusBox
        );

        wireActions(loadButton, addInstructionButton, runButton, pauseButton, stepButton, resetButton);

        top.getChildren().addAll(header, controls);
        return top;
    }

    private SplitPane buildCenterSection() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.72);
        splitPane.getStyleClass().add("dashboard-split");

        VBox left = new VBox(12);
        left.setPadding(new Insets(0, 18, 12, 18));
        left.getChildren().add(buildArchitectureSection());
        VBox.setVgrow(left.getChildren().get(0), Priority.ALWAYS);

        VBox right = new VBox(12);
        right.setPadding(new Insets(0, 18, 12, 0));
        right.getChildren().add(buildSidePanel());
        VBox.setVgrow(right.getChildren().get(0), Priority.ALWAYS);

        splitPane.getItems().addAll(left, right);
        return splitPane;
    }

    private VBox buildArchitectureSection() {
        VBox wrapper = new VBox(10);
        wrapper.getStyleClass().add("panel-card");

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Processor Architecture Dashboard");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Execution Unit on the left, Bus Interface Unit on the right, with segment registers, stack, and I/O.");
        hint.getStyleClass().add("section-hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, spacer, hint);

        architectureView = new CpuArchitectureView();
        VBox.setVgrow(architectureView, Priority.ALWAYS);

        wrapper.getChildren().addAll(titleRow, architectureView);
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private VBox buildSidePanel() {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("panel-card");

        Label title = new Label("Inspector Panels");
        title.getStyleClass().add("section-title");

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("dashboard-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);

        queueList = new ListView<>(queueItems);
        queueList.getStyleClass().add("dashboard-list");

        executionLog = new ListView<>(executionItems);
        executionLog.getStyleClass().add("dashboard-list");

        microOpLog = new ListView<>(microOpItems);
        microOpLog.getStyleClass().add("dashboard-list");
        microarchitectureTimeline = new ListView<>(timelineItems);
        microarchitectureTimeline.getStyleClass().add("dashboard-list");

        registerTable = createKeyValueTable("Register", "Value", registerRows);
        memoryTable = createKeyValueTable("Cell", "Value", memoryRows);
        busTable = createKeyValueTable("Bus", "State", busRows);
        flagsTable = createKeyValueTable("Flag", "Value", flagRows);
        machineCodeTable = createKeyValueTable("Field", "Live pipeline value", machineCodeRows);

        tabs.getTabs().addAll(
            tab("Instruction Queue", queueList),
            tab("Execution Log", executionLog),
            tab("Micro-op Log", microOpLog),
            tab("Microarchitecture Timeline", microarchitectureTimeline),
            tab("Register State", registerTable),
            tab("Memory Table", memoryTable),
            tab("Bus State", busTable),
            tab("Flags", flagsTable),
            tab("Machine Code", machineCodeTable),
            tab("ISA Reference", createInstructionReference()),
            tab("Debugger", new DebuggerPane(() -> programEditor.getText())),
            tab("Research", new ResearchPane(() -> programEditor.getText()))
        );

        panel.getChildren().addAll(title, tabs);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private VBox buildEditorSection() {
        VBox shell = new VBox(10);
        shell.setPadding(new Insets(0, 18, 18, 18));

        VBox editorCard = new VBox(10);
        editorCard.getStyleClass().add("panel-card");

        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Program Workspace");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Write assembly here. Use MOV, ADD, SUB, MUL, DIV, SHL, SHR, AND, OR, XOR, NOT, INC, DEC, CMP, JMP, JZ, JNZ, JC, JNC, JO, LOOP, PUSH, POP, CALL, RET, INT, IRET, NOP, HLT.");
        hint.getStyleClass().add("section-hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, spacer, hint);

        programEditor = new TextArea();
        programEditor.getStyleClass().add("program-editor");
        programEditor.setPrefRowCount(10);
        VBox.setVgrow(programEditor, Priority.ALWAYS);

        editorCard.getChildren().addAll(titleRow, programEditor);
        shell.getChildren().add(editorCard);
        return shell;
    }

    private void wireActions(Button loadButton, Button addInstructionButton,
            Button runButton, Button pauseButton, Button stepButton, Button resetButton) {

        addInstructionButton.setOnAction(event -> appendInstruction());
        loadButton.setOnAction(event -> { stopRun(); loadProgram(); });
        stepButton.setOnAction(event -> { stopRun(); executeSingleStep(); });
        runButton.setOnAction(event -> startRun(runButton));
        pauseButton.setOnAction(event -> {
            stopRun();
            runButton.setText("Run");
            statusLabel.setText(cpu.isHalted() ? "Halted" : "Paused");
            logEvent("Simulation paused.");
        });
        resetButton.setOnAction(event -> {
            stopRun();
            runButton.setText("Run");
            cpu.reset();
            queueItems.clear();
            microOpItems.clear();
            executionItems.clear();
            refreshAll(null);
            statusLabel.setText("Reset");
            logEvent("Simulator reset.");
        });
    }

    private void appendInstruction() {
        String line = instructionField.getText() == null ? "" : instructionField.getText().trim();
        if (line.isEmpty()) { logEvent("Instruction input is empty."); return; }
        String existing = programEditor.getText();
        String prefix = existing == null || existing.isBlank() ? "" : System.lineSeparator();
        programEditor.appendText(prefix + line);
        instructionField.clear();
        logEvent("Instruction added to editor: " + line);
    }

    private void loadProgram() {
        try {
            List<Instruction> program = parser.parseProgram(programEditor.getText());
            cpu.loadProgram(program);
            queueItems.clear();
            microOpItems.clear();
            executionItems.clear();
            refreshAll(null);
            statusLabel.setText("Program loaded");
            logEvent("Loaded " + program.size() + " instruction(s).");
        } catch (Exception ex) {
            statusLabel.setText("Parse error");
            logEvent("Parse error: " + ex.getMessage());
        }
    }

    private void executeSingleStep() {
        if (cpu.getProgram().isEmpty()) { loadProgram(); if (cpu.getProgram().isEmpty()) return; }
        if (cpu.isHalted()) { logEvent("CPU is halted. Reset or reload."); return; }
        MicroOperation op = cpu.step();
        if (op == null) { logEvent("No micro-operation available."); refreshAll(null); return; }
        appendMicroOp(op);
        refreshAll(op);
        if (cpu.isHalted()) { statusLabel.setText("Halted"); logEvent("Program halted."); }
    }

    private void startRun(Button runButton) {
        if (cpu.getProgram().isEmpty()) { loadProgram(); if (cpu.getProgram().isEmpty()) return; }
        if (cpu.isHalted()) { logEvent("CPU is halted. Reset or reload."); return; }
        stopRun();
        runButton.setText("Running");
        statusLabel.setText("Running");
        runTimeline = new Timeline(new KeyFrame(Duration.millis(speedSlider.getValue()), event -> {
            if (cpu.isHalted() || cpu.getCurrentMicroOp() == null) {
                stopRun(); runButton.setText("Run");
                statusLabel.setText(cpu.isHalted() ? "Halted" : "Ready");
                return;
            }
            MicroOperation op = cpu.step();
            if (op != null) { appendMicroOp(op); refreshAll(op); }
            if (cpu.isHalted()) { stopRun(); runButton.setText("Run"); statusLabel.setText("Halted"); logEvent("Program halted."); }
        }));
        runTimeline.setCycleCount(Timeline.INDEFINITE);
        runTimeline.play();
        logEvent("Simulation running.");
    }

    private void stopRun() { if (runTimeline != null) { runTimeline.stop(); runTimeline = null; } }

    private void appendMicroOp(MicroOperation op) {
        String phase = determinePhase(op);
        String message = "CLK " + cpu.getClock().getCycleCount() + " | " + phase + " | " + op.getRtlDescription();
        microOpItems.add(message);
        microOpLog.scrollTo(microOpItems.size() - 1);
        logEvent(message);
    }

    private void refreshAll(MicroOperation lastOp) {
        cycleLabel.setText(String.valueOf(cpu.getClock().getCycleCount()));
        phaseLabel.setText(lastOp == null ? "IDLE" : determinePhase(lastOp));

        Instruction currentInstruction = cpu.getCurrentInstruction();
        if (currentInstruction == null && !cpu.getProgram().isEmpty()) {
            int ip = valueOf("IP");
            if (ip >= 0 && ip < cpu.getProgram().size()) {
                currentInstruction = cpu.getProgram().get(ip);
            }
        }

        instructionLabel.setText(currentInstruction == null ? "---" : currentInstruction.toString());
        microOpLabel.setText(lastOp == null ? "---" : lastOp.getRtlDescription());
        busLabel.setText(describeBusState());

        refreshQueue();
        refreshTimeline();
        refreshRegisterTable();
        refreshMemoryTable();
        refreshBusTable(lastOp);
        refreshFlagsTable();
        refreshMachineCodeInspection();

        architectureView.update(cpu, currentInstruction, lastOp, buildQueueValues(), statusLabel.getText());
    }

    private void refreshQueue() { queueItems.setAll(buildQueueValues()); }

    private List<String> buildQueueValues() {
        List<String> queue = new ArrayList<>();
        List<Instruction> program = cpu.getProgram();
        int[] tokens = cpu.getBiu().getPrefetchQueue().getContents();
        if (cpu.isMachineCodeProgram()) {
            for (int i = 0; i < 6; i++) queue.add(i < tokens.length ? String.format("%02X", tokens[i] & 0xFF) : "--");
            return queue;
        }
        for (int i = 0; i < 6; i++) {
            int index = i < tokens.length ? tokens[i] : -1;
            if (index >= 0 && index < program.size())
                queue.add(String.format("%d. %s", index, program.get(index)));
            else queue.add("--");
        }
        return queue;
    }

    private void refreshTimeline() {
        List<String> rows = new ArrayList<>();
        cpu.getCycleTrace().forEach(snapshot -> rows.add(String.format("C%04d BIU=%s EU=%s BUS=%s Q=%d %s",
            snapshot.cycle(), snapshot.biuState(), snapshot.euState(), snapshot.busOwner(), snapshot.queueOccupancy(), snapshot.microOperation())));
        timelineItems.setAll(rows);
        if (!rows.isEmpty()) microarchitectureTimeline.scrollTo(rows.size() - 1);
    }

    /** Uses the CPU's actual loaded bytes and decoded instruction stream; no UI-owned timing state exists. */
    private void refreshMachineCodeInspection() {
        machineCodeRows.clear();
        if (!cpu.isMachineCodeProgram() || cpu.getMachineCode().length == 0) {
            machineCodeRows.add(new KeyValueRow("Status", "Load machine-code bytes to inspect the live codec pipeline."));
            return;
        }
        var decoded = new Intel8086Decoder().decodeAll(cpu.getMachineCode());
        int index = Math.max(0, Math.min(valueOf("IP"), decoded.size() - 1));
        MachineCodeInspection inspection = MachineCodeInspection.from(decoded.get(index));
        machineCodeRows.addAll(
            new KeyValueRow("Byte offset", String.valueOf(inspection.offset())),
            new KeyValueRow("Encoded bytes", inspection.bytes()),
            new KeyValueRow("Instruction length", String.valueOf(inspection.length())),
            new KeyValueRow("Prefix", inspection.prefixes().isBlank() ? "--" : inspection.prefixes()),
            new KeyValueRow("Opcode", inspection.opcode()),
            new KeyValueRow("Decoded instruction", inspection.instruction()),
            new KeyValueRow("Displacement", String.valueOf(inspection.displacement())),
            new KeyValueRow("Immediate", String.valueOf(inspection.immediate())),
            new KeyValueRow("BIU queue", String.join(" ", buildQueueValues())));
    }

    private void refreshRegisterTable() {
        registerRows.clear();
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : List.of("AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI",
                "CS", "DS", "SS", "ES", "IP", "IR", "MAR", "MDR")) {
            values.put(name, cpu.getRegister(name).toHex());
        }
        values.put("AH/AL", splitWord(valueOf("AX")));
        values.put("BH/BL", splitWord(valueOf("BX")));
        values.put("CH/CL", splitWord(valueOf("CX")));
        values.put("DH/DL", splitWord(valueOf("DX")));
        values.put("FLAGS", cpu.getFlags().flagsStringFull());
        values.forEach((key, value) -> registerRows.add(new KeyValueRow(key, value)));
    }

    private void refreshMemoryTable() {
        memoryRows.clear();
        int maxShow = Math.min(cpu.getMemory().getSize(), 256);
        for (int i = 0; i < maxShow; i++) {
            int value = cpu.getMemory().directRead(i);
            boolean mappedInstruction = i < cpu.getProgram().size();
            if (value != 0 || i < 24 || mappedInstruction) {
                String cellValue = String.format("0x%04X", value);
                if (mappedInstruction && i < cpu.getProgram().size())
                    cellValue += "  |  " + cpu.getProgram().get(i);
                memoryRows.add(new KeyValueRow(String.format("0x%04X", i), cellValue));
            }
        }
    }

    private void refreshBusTable(MicroOperation lastOp) {
        busRows.clear();
        busRows.add(new KeyValueRow("Address Bus", cpu.getAddressBus().isActive() ? cpu.getAddressBus().toString() : "idle"));
        busRows.add(new KeyValueRow("Data Bus", cpu.getDataBus().isActive() ? cpu.getDataBus().toString() : "idle"));
        busRows.add(new KeyValueRow("Control Bus", cpu.getControlBus().getActiveSignals().isEmpty() ? "idle" : cpu.getControlBus().toString()));
        busRows.add(new KeyValueRow("Internal EU Bus", lastOp == null ? "idle" : lastOp.getBusActivity().name()));
        busRows.add(new KeyValueRow("External Bus", cpu.getAddressBus().isActive() || cpu.getDataBus().isActive() ? "active" : "idle"));
    }

    private void refreshFlagsTable() {
        FLAGS flags = cpu.getFlags();
        flagRows.setAll(
            new KeyValueRow("CF (Carry)", flags.isCarry() ? "1" : "0"),
            new KeyValueRow("PF (Parity)", flags.isParity() ? "1" : "0"),
            new KeyValueRow("AF (Aux Carry)", flags.isAuxCarry() ? "1" : "0"),
            new KeyValueRow("ZF (Zero)", flags.isZero() ? "1" : "0"),
            new KeyValueRow("SF (Sign)", flags.isSign() ? "1" : "0"),
            new KeyValueRow("OF (Overflow)", flags.isOverflow() ? "1" : "0"),
            new KeyValueRow("DF (Direction)", flags.isDirection() ? "1" : "0"),
            new KeyValueRow("IF (Interrupt)", flags.isInterrupt() ? "1" : "0"),
            new KeyValueRow("TF (Trap)", flags.isTrap() ? "1" : "0")
        );
    }

    private String describeBusState() {
        List<String> parts = new ArrayList<>();
        if (cpu.getAddressBus().isActive()) parts.add(cpu.getAddressBus().toString());
        if (cpu.getDataBus().isActive()) parts.add(cpu.getDataBus().toString());
        if (!cpu.getControlBus().getActiveSignals().isEmpty()) parts.add(cpu.getControlBus().toString());
        return parts.isEmpty() ? "Buses idle" : String.join(" | ", parts);
    }

    private String determinePhase(MicroOperation op) {
        if (op == null) return "IDLE";
        return switch (op.getType()) {
            case MAR_LOAD_PC, MDR_LOAD_MEMORY, IR_LOAD_MDR -> "FETCH";
            case DECODE -> "DECODE";
            case HALT -> "HALT";
            default -> "EXECUTE";
        };
    }

    private int valueOf(String registerName) {
        Register register = cpu.getRegister(registerName);
        return register == null ? 0 : register.output();
    }

    private String splitWord(int value) {
        int high = (value >> 8) & 0xFF;
        int low = value & 0xFF;
        return String.format("0x%02X / 0x%02X", high, low);
    }

    private void logEvent(String message) {
        executionItems.add(message);
        if (executionLog != null) executionLog.scrollTo(executionItems.size() - 1);
    }

    private String defaultProgram() {
        return String.join(System.lineSeparator(),
            "; 8086 CPU Simulator — Full ISA Demo",
            "; Arithmetic: ADD, SUB, INC, DEC, CMP",
            "; Logic: AND, OR, XOR, NOT",
            "; Shifts: SHL, SHR",
            "; Control: JMP, JZ, JNZ, LOOP",
            "",
            "MOV AX, 100",
            "MOV BX, 50",
            "ADD AX, BX",
            "SUB AX, 30",
            "INC AX",
            "DEC BX",
            "CMP AX, 121",
            "JZ 11",
            "MOV CX, 0",
            "JMP 12",
            "MOV CX, 1",
            "PUSH AX",
            "POP DX",
            "HLT"
        );
    }

    private HBox statCard(String title, Label valueLabel) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("metric-card");
        VBox textBox = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        valueLabel.getStyleClass().add("metric-value");
        valueLabel.setWrapText(true);
        valueLabel.setMaxWidth(170);
        textBox.getChildren().addAll(titleLabel, valueLabel);
        card.getChildren().add(textBox);
        return card;
    }

    private Button actionButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("action-button", styleClass);
        return button;
    }

    private TableView<KeyValueRow> createKeyValueTable(String keyHeader, String valueHeader, ObservableList<KeyValueRow> rows) {
        TableView<KeyValueRow> table = new TableView<>(rows);
        table.getStyleClass().add("dashboard-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<KeyValueRow, String> keyColumn = new TableColumn<>(keyHeader);
        keyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().key()));
        keyColumn.setMaxWidth(1f * Integer.MAX_VALUE * 0.38);
        TableColumn<KeyValueRow, String> valueColumn = new TableColumn<>(valueHeader);
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().value()));
        table.getColumns().setAll(keyColumn, valueColumn);
        return table;
    }

    private Tab tab(String title, javafx.scene.Node content) {
        ScrollPane wrapper = new ScrollPane(content);
        wrapper.setFitToWidth(true);
        wrapper.setFitToHeight(true);
        wrapper.getStyleClass().add("tab-scroll");
        Tab tab = new Tab(title, wrapper);
        tab.setClosable(false);
        return tab;
    }

    /**
     * Creates the ISA Reference tab content — a comprehensive instruction reference
     * with categories, syntax, flags affected, and RTL descriptions.
     */
    private javafx.scene.Node createInstructionReference() {
        TextArea ref = new TextArea();
        ref.setEditable(false);
        ref.setWrapText(true);
        ref.getStyleClass().add("program-editor");
        ref.setText(
            "=== 8086 INSTRUCTION SET REFERENCE ===\n\n" +
            "--- DATA TRANSFER ---\n" +
            "MOV dst, src     Move data between registers/memory/immediate\n" +
            "  MOV AX, BX       AX <- BX\n" +
            "  MOV AX, 100      AX <- 100\n" +
            "  MOV AX, [100]    AX <- Memory[DS:100]\n" +
            "  MOV [100], AX    Memory[DS:100] <- AX\n" +
            "  MOV DS, AX       Set segment register (NOT MOV CS,AX!)\n" +
            "  Flags: None\n\n" +
            "LOAD reg, [mem]  Load from memory (alias for MOV reg,[mem])\n" +
            "STORE [mem], reg Store to memory (alias for MOV [mem],reg)\n" +
            "XCHG dst, src    Exchange two registers\n" +
            "PUSH src         Push onto stack (SP <- SP-2, Mem[SS:SP] <- src)\n" +
            "POP dst          Pop from stack (dst <- Mem[SS:SP], SP <- SP+2)\n" +
            "PUSHF            Push FLAGS register onto stack\n" +
            "POPF             Pop FLAGS register from stack\n" +
            "LAHF             Load AH from FLAGS (SF:ZF:AF:PF:CF)\n" +
            "SAHF             Store AH into FLAGS\n\n" +
            "--- ARITHMETIC ---\n" +
            "ADD dst, src     dst <- dst + src        Flags: CF,ZF,SF,OF,PF,AF\n" +
            "ADC dst, src     dst <- dst + src + CF   Flags: CF,ZF,SF,OF,PF,AF\n" +
            "SUB dst, src     dst <- dst - src        Flags: CF,ZF,SF,OF,PF,AF\n" +
            "SBB dst, src     dst <- dst - src - CF   Flags: CF,ZF,SF,OF,PF,AF\n" +
            "INC dst          dst <- dst + 1          Flags: ZF,SF,OF,PF,AF (CF preserved!)\n" +
            "DEC dst          dst <- dst - 1          Flags: ZF,SF,OF,PF,AF (CF preserved!)\n" +
            "NEG dst          dst <- -dst (2's comp)  Flags: CF(=1 if operand!=0),ZF,SF,OF,PF,AF\n" +
            "CMP dst, src     Compare (dst-src, discard result)  Flags: CF,ZF,SF,OF,PF,AF\n" +
            "MUL src          DX:AX <- AX * src (unsigned)   Flags: CF,OF (SF,ZF,PF,AF undefined)\n" +
            "IMUL src         DX:AX <- AX * src (signed)     Flags: CF,OF\n" +
            "DIV src          AX <- DX:AX / src (quotient), DX <- remainder  Flags: undefined\n" +
            "IDIV src         Signed divide                    Flags: undefined\n" +
            "CBW              AX <- sign-extend(AL)\n" +
            "CWD              DX:AX <- sign-extend(AX)\n\n" +
            "--- LOGIC ---\n" +
            "AND dst, src     dst <- dst & src   Flags: CF=0,OF=0,ZF,SF,PF,AF\n" +
            "OR  dst, src     dst <- dst | src   Flags: CF=0,OF=0,ZF,SF,PF,AF\n" +
            "XOR dst, src     dst <- dst ^ src   Flags: CF=0,OF=0,ZF,SF,PF,AF\n" +
            "NOT dst          dst <- ~dst        Flags: None (no change)\n" +
            "TEST dst, src    dst & src (discard) Flags: CF=0,OF=0,ZF,SF,PF,AF\n\n" +
            "--- SHIFTS / ROTATES ---\n" +
            "SHL dst, count   Shift left (CF <- MSB)           Flags: CF,OF,ZF,SF,PF\n" +
            "SHR dst, count   Shift right logical (CF <- LSB)  Flags: CF,OF,ZF,SF,PF\n" +
            "SAR dst, count   Shift right arithmetic (sign ext) Flags: CF,ZF,SF,PF\n" +
            "ROL dst, count   Rotate left through LSB          Flags: CF,OF\n" +
            "ROR dst, count   Rotate right through MSB         Flags: CF,OF\n" +
            "RCL dst, count   Rotate left through carry        Flags: CF,OF\n" +
            "RCR dst, count   Rotate right through carry       Flags: CF,OF\n" +
            "  count can be immediate or CL register\n\n" +
            "--- CONTROL FLOW ---\n" +
            "JMP addr         Unconditional near jump\n" +
            "JZ / JE addr     Jump if ZF=1 (equal)\n" +
            "JNZ / JNE addr   Jump if ZF=0 (not equal)\n" +
            "JC / JB addr     Jump if CF=1 (carry/below)\n" +
            "JNC / JNB addr   Jump if CF=0 (no carry/not below)\n" +
            "JO addr          Jump if OF=1 (overflow)\n" +
            "JNO addr         Jump if OF=0\n" +
            "JS addr          Jump if SF=1 (negative)\n" +
            "JNS addr         Jump if SF=0 (positive)\n" +
            "JP / JPE addr    Jump if PF=1 (parity even)\n" +
            "JNP / JPO addr   Jump if PF=0 (parity odd)\n" +
            "JL / JNGE addr   Jump if SF!=OF (less)\n" +
            "JNL / JGE addr   Jump if SF=OF (not less)\n" +
            "JLE / JNG addr   Jump if ZF=1 or SF!=OF\n" +
            "JNLE / JG addr   Jump if ZF=0 and SF=OF\n" +
            "JBE / JNA addr   Jump if CF=1 or ZF=1\n" +
            "JNBE / JA addr   Jump if CF=0 and ZF=0\n" +
            "LOOP addr        CX <- CX-1; if CX!=0 jump\n" +
            "CALL addr        Push IP, jump to addr\n" +
            "RET              Pop IP from stack (return)\n\n" +
            "--- PROCESSOR CONTROL ---\n" +
            "NOP              No operation (1 cycle)\n" +
            "HLT              Halt processor\n" +
            "CLC              Clear carry flag (CF <- 0)\n" +
            "STC              Set carry flag (CF <- 1)\n" +
            "CMC              Complement carry flag (CF <- ~CF)\n" +
            "CLD              Clear direction flag (DF <- 0)\n" +
            "STD              Set direction flag (DF <- 1)\n" +
            "CLI              Clear interrupt flag (IF <- 0)\n" +
            "STI              Set interrupt flag (IF <- 1)\n\n" +
            "--- INTERRUPTS ---\n" +
            "INT vector       Software interrupt (push FLAGS,CS,IP; jump to IVT)\n" +
            "IRET             Interrupt return (pop IP,CS,FLAGS)\n\n" +
            "--- SEGMENT ADDRESSING ---\n" +
            "Physical Address = Segment Register * 16 + Offset\n" +
            "  DS: default for [BX], [SI], [DI], [BX+SI], [BX+DI]\n" +
            "  SS: default for [BP], [BP+SI], [BP+DI]\n" +
            "  CS: instruction fetch\n" +
            "  ES: string destination (ES:DI)\n\n" +
            "--- ADDRESSING MODES ---\n" +
            "  [100]            Direct address\n" +
            "  [BX]             Register indirect\n" +
            "  [BX+10]          Register indirect + displacement\n" +
            "  [BX+SI]          Base + index\n" +
            "  [BX+SI+10]       Base + index + displacement\n" +
            "  [BP+DI+5]        Base + index + displacement (SS segment)\n\n" +
            "--- MODELING STATUS (authoritative: isa.ISA.statusOf) ---\n" +
            "All instructions below parse; status describes execution fidelity.\n" +
            "SUPPORTED = full 8086 architectural effect (incl. DAA/DAS/INTO).\n" +
            "PARTIAL   = retires with explicitly scoped effect (trace says so).\n" +
            "  WAIT   PARTIAL  No x87 coprocessor modeled; no effect.\n" +
            "  LOCK   PARTIAL  Single CPU, no external bus master; no effect.\n" +
            "  ESC    PARTIAL  No external coprocessor modeled; no effect.\n" +
            "  REP/REPE/REPNE PARTIAL as standalone lines; full effect as string prefixes.\n" +
            "Everything else in this reference: SUPPORTED.\n"
        );
        return ref;
    }

    public static void main(String[] args) { launch(args); }

    public record KeyValueRow(String key, String value) { }
}

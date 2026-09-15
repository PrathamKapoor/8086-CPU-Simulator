package gui;

import debugger.Breakpoint;
import debugger.DebugSession;
import debugger.DebugState;
import debugger.StateDiff;
import debugger.StopReason;
import debugger.Watchpoint;
import instruction.Instruction;
import instruction.InstructionParser;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Supplier;

/**
 * A self-contained debugger workspace: its own {@link DebugSession}, entirely
 * independent of the CPU instance the rest of {@link MainGUI} visualizes (so
 * this never touches the existing architecture dashboard). Every control here
 * calls a real DebugSession method; every panel renders {@link DebugState} /
 * {@link debugger.TraceEvent} / {@link StateDiff} the session actually
 * produced. Nothing on this pane is hard-coded sample data.
 */
public final class DebuggerPane extends BorderPane {
    private final Supplier<String> programSource;
    private DebugSession session;

    private final Label stopReasonLabel = new Label("No session loaded");
    private final Label positionLabel = new Label("-");
    private final ListView<String> registersList = new ListView<>();
    private final ListView<String> flagsList = new ListView<>();
    private final ListView<String> disassemblyList = new ListView<>();
    private final ListView<String> breakpointsList = new ListView<>();
    private final ListView<String> watchpointsList = new ListView<>();
    private final ListView<String> microOpList = new ListView<>();
    private final ListView<String> traceList = new ListView<>();
    private final ListView<String> diffList = new ListView<>();
    private final ListView<String> biuQueueList = new ListView<>();
    private final TextArea memoryStackArea = new TextArea();
    private final TextField breakpointField = new TextField();
    private final TextField watchField = new TextField();

    private debugger.ExecutionSnapshot diffBaseline;

    public DebuggerPane(Supplier<String> programSource) {
        this.programSource = programSource;
        setTop(buildControls());
        setLeft(buildLeftPanel());
        setCenter(buildCenterPanel());
        setRight(buildRightPanel());
        setBottom(buildBottomPanel());
        setPadding(new Insets(10));
        refresh(StopReason.NOT_STARTED);
    }

    private HBox buildControls() {
        Button load = new Button("Load Program");
        Button run = new Button("Run");
        Button pause = new Button("Pause");
        Button cont = new Button("Continue");
        Button step = new Button("Step");
        Button microStep = new Button("Micro-step");
        Button stepOver = new Button("Step Over");
        Button stepOut = new Button("Step Out");
        Button reset = new Button("Reset");
        Button checkpoint = new Button("Checkpoint");
        Button rewind = new Button("Rewind 1");

        load.setOnAction(e -> loadProgram());
        run.setOnAction(e -> withSession(s -> refresh(s.run())));
        pause.setOnAction(e -> withSession(DebugSession::requestPause));
        cont.setOnAction(e -> withSession(s -> refresh(s.continueExecution())));
        step.setOnAction(e -> withSession(s -> refresh(s.stepInstruction())));
        microStep.setOnAction(e -> withSession(s -> refresh(s.stepMicroOp())));
        stepOver.setOnAction(e -> withSession(s -> refresh(s.stepOver())));
        stepOut.setOnAction(e -> withSession(s -> refresh(s.stepOut())));
        reset.setOnAction(e -> withSession(s -> { s.reset(); refresh(StopReason.NOT_STARTED); }));
        checkpoint.setOnAction(e -> withSession(s -> {
            try {
                int id = s.checkpoint();
                logLine(traceList, "checkpoint " + id + " taken");
            } catch (IllegalStateException ex) {
                logLine(traceList, "cannot checkpoint: " + ex.getMessage());
            }
        }));
        rewind.setOnAction(e -> withSession(s -> {
            boolean ok = s.rewindInstructions(1);
            logLine(traceList, ok ? "rewound 1 instruction" : "cannot rewind further");
            refresh(s.lastStopReason());
        }));

        HBox bar = new HBox(8, load, run, pause, cont, step, microStep, stepOver, stepOut, reset,
            new Separator(Orientation.VERTICAL), checkpoint, rewind);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 8, 0));
        return bar;
    }

    private VBox buildLeftPanel() {
        VBox box = new VBox(6, sectionLabel("Registers"), registersList, sectionLabel("Flags"), flagsList);
        registersList.setPrefWidth(160);
        flagsList.setPrefWidth(160);
        VBox.setVgrow(registersList, Priority.ALWAYS);
        VBox.setVgrow(flagsList, Priority.ALWAYS);
        box.setPadding(new Insets(0, 8, 0, 0));
        return box;
    }

    private VBox buildCenterPanel() {
        VBox box = new VBox(6, sectionLabel("Disassembly (current instruction highlighted)"), disassemblyList,
            new HBox(8, new Label("Position:"), positionLabel));
        VBox.setVgrow(disassemblyList, Priority.ALWAYS);
        return box;
    }

    private VBox buildRightPanel() {
        breakpointField.setPromptText("instruction index [condition]");
        Button addBreak = new Button("Add breakpoint");
        addBreak.setOnAction(e -> withSession(this::addBreakpointFromField));

        watchField.setPromptText("address|register|flag [write|read]");
        Button addWatch = new Button("Add watch");
        addWatch.setOnAction(e -> withSession(this::addWatchFromField));

        VBox box = new VBox(6,
            sectionLabel("Stop reason"), stopReasonLabel,
            sectionLabel("Breakpoints"), breakpointField, addBreak, breakpointsList,
            sectionLabel("Watchpoints"), watchField, addWatch, watchpointsList);
        box.setPadding(new Insets(0, 0, 0, 8));
        box.setPrefWidth(220);
        VBox.setVgrow(breakpointsList, Priority.ALWAYS);
        VBox.setVgrow(watchpointsList, Priority.ALWAYS);
        return box;
    }

    private TabPane buildBottomPanel() {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
            new Tab("Micro-operations", microOpList),
            new Tab("Trace", traceList),
            new Tab("State Diff", buildDiffTab()),
            new Tab("BIU Queue", biuQueueList),
            new Tab("Memory / Stack", buildMemoryStackTab())
        );
        for (Tab t : tabs.getTabs()) t.setClosable(false);
        tabs.setPrefHeight(220);
        return tabs;
    }

    private VBox buildDiffTab() {
        Button markBaseline = new Button("Mark baseline");
        Button showDiff = new Button("Diff vs baseline");
        markBaseline.setOnAction(e -> withSession(s -> {
            diffBaseline = s.currentState().snapshot();
            logLine(diffList, "baseline marked");
        }));
        showDiff.setOnAction(e -> withSession(s -> {
            if (diffBaseline == null) { logLine(diffList, "no baseline marked yet"); return; }
            StateDiff diff = DebugSession.diff(diffBaseline, s.currentState().snapshot());
            diffList.getItems().setAll(diff.isEmpty() ? List.of("(no change)") : diff.describe());
        }));
        VBox box = new VBox(6, new HBox(8, markBaseline, showDiff), diffList);
        VBox.setVgrow(diffList, Priority.ALWAYS);
        return box;
    }

    private VBox buildMemoryStackTab() {
        memoryStackArea.setEditable(false);
        memoryStackArea.setPrefRowCount(6);
        Button refreshMem = new Button("Refresh memory/stack view");
        refreshMem.setOnAction(e -> withSession(this::refreshMemoryStack));
        VBox box = new VBox(6, refreshMem, memoryStackArea);
        VBox.setVgrow(memoryStackArea, Priority.ALWAYS);
        return box;
    }

    private void loadProgram() {
        String source = programSource.get();
        try {
            List<Instruction> program = new InstructionParser().parseProgram(source);
            session = DebugSession.forSourceProgram(program);
            session.setTracing(true);
            diffBaseline = null;
            refresh(StopReason.NOT_STARTED);
            logLine(traceList, "loaded " + program.size() + " instruction(s)");
        } catch (RuntimeException ex) {
            stopReasonLabel.setText("Load failed: " + ex.getMessage());
        }
    }

    private void withSession(java.util.function.Consumer<DebugSession> action) {
        if (session == null) { stopReasonLabel.setText("Load a program first"); return; }
        action.accept(session);
    }

    private void addBreakpointFromField(DebugSession s) {
        String text = breakpointField.getText();
        if (text == null || text.isBlank()) return;
        String[] parts = text.trim().split("\\s+", 2);
        int index = Integer.parseInt(parts[0]);
        String condition = parts.length > 1 ? parts[1] : null;
        int id = s.addInstructionBreakpoint(index, condition);
        breakpointField.clear();
        renderBreakpoints(s);
        logLine(traceList, "breakpoint " + id + " added at instruction[" + index + "]");
    }

    private void addWatchFromField(DebugSession s) {
        String text = watchField.getText();
        if (text == null || text.isBlank()) return;
        String[] parts = text.trim().split("\\s+");
        String target = parts[0].toUpperCase(java.util.Locale.ROOT);
        int id;
        if (target.matches("[A-Z]+") && List.of("AX","BX","CX","DX","SP","BP","SI","DI","CS","DS","SS","ES","IP").contains(target)) {
            id = s.addRegisterWatch(target);
        } else if (target.matches("CF|PF|AF|ZF|SF|TF|IF|DF|OF")) {
            id = s.addFlagWatch(target);
        } else {
            int address = target.startsWith("0X") ? Integer.parseInt(target.substring(2), 16) : Integer.parseInt(target);
            Watchpoint.Access access = parts.length > 1 && parts[1].equalsIgnoreCase("read")
                ? Watchpoint.Access.READ : Watchpoint.Access.WRITE;
            id = s.addMemoryWatch(address, access);
        }
        watchField.clear();
        renderWatchpoints(s);
        logLine(traceList, "watchpoint " + id + " added");
    }

    private void refreshMemoryStack(DebugSession s) {
        StringBuilder sb = new StringBuilder();
        int sp = s.cpu().getRegister("SP").output();
        int ss = s.cpu().getRegister("SS").output();
        sb.append("SS:SP = ").append(String.format("%04X:%04X", ss, sp)).append('\n');
        sb.append("Stack top 8 words:\n");
        for (int i = 0; i < 8; i++) {
            int addr = s.cpu().computePhysicalAddress(ss, sp + i * 2);
            sb.append(String.format("  [%05X] %04X%n", addr, s.cpu().getMemory().directRead(addr)));
        }
        memoryStackArea.setText(sb.toString());
    }

    private void refresh(StopReason reason) {
        if (session == null) return;
        DebugState state = session.currentState();
        stopReasonLabel.setText(reason + (state.lastBreakpointId() != null ? " (bp " + state.lastBreakpointId() + ")" : "")
            + (state.lastWatchHit() != null && reason == StopReason.WATCHPOINT ? " (" + state.lastWatchHit().kind() + ")" : ""));
        positionLabel.setText("instruction[" + state.position().instructionIndex() + "] micro-op "
            + state.position().microOpIndex() + (state.halted() ? " HALTED" : ""));

        registersList.getItems().setAll(
            List.of("AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI", "CS", "DS", "SS", "ES", "IP").stream()
                .map(r -> r + " = " + String.format("%04X", session.cpu().getRegister(r).output()))
                .toList());
        flagsList.getItems().setAll(List.of(session.cpu().getFlags().flagsStringFull().split(" ")));

        List<Instruction> program = session.cpu().getProgram();
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < program.size(); i++) {
            lines.add((i == state.position().instructionIndex() ? "-> " : "   ") + "[" + i + "] " + program.get(i));
        }
        disassemblyList.getItems().setAll(lines);

        renderBreakpoints(session);
        renderWatchpoints(session);

        microOpOf(state);

        traceList.getItems().setAll(session.trace().stream()
            .skip(Math.max(0, session.trace().size() - 200))
            .map(this::describeTraceEvent).toList());

        biuQueueList.getItems().setAll(state.snapshot().prefetchQueueContents().stream()
            .map(b -> String.format("%02X", b)).toList());
    }

    private void microOpOf(DebugState state) {
        var op = session.cpu().getCurrentMicroOp();
        microOpList.getItems().setAll(op == null ? List.of("(none pending)") : List.of(op.getRtlDescription()));
    }

    private String describeTraceEvent(debugger.TraceEvent e) {
        return switch (e) {
            case debugger.TraceEvent.InstructionStart s -> "START  [" + s.instructionIndex() + "] " + s.instructionText();
            case debugger.TraceEvent.InstructionRetired r -> "RETIRE [" + r.instructionIndex() + "] " + r.instructionText();
            case debugger.TraceEvent.MicroOpExecuted m -> "  uop: " + m.rtlDescription();
            case debugger.TraceEvent.RegisterMutation r -> "  " + r.register() + ": " + r.oldValue() + " -> " + r.newValue();
            case debugger.TraceEvent.FlagMutation f -> "  " + f.flag() + ": " + f.oldValue() + " -> " + f.newValue();
            case debugger.TraceEvent.MemoryWriteEvent w -> String.format("  mem[%05X] %d -> %d", w.address(), w.oldValue(), w.newValue());
            case debugger.TraceEvent.MemoryReadEvent r -> String.format("  read mem[%05X] = %d", r.address(), r.value());
            case debugger.TraceEvent.ControlTransfer c -> "  branch " + c.opcode() + ": [" + c.fromInstructionIndex() + "] -> [" + c.toInstructionIndex() + "]";
            case debugger.TraceEvent.BreakpointHitEvent b -> "BREAKPOINT " + b.breakpointId();
            case debugger.TraceEvent.WatchpointHitEvent w -> "WATCHPOINT " + w.hit().watchpointId();
            case debugger.TraceEvent.BiuFetchEvent f -> String.format("  fetch [%05X] = %02X", f.physicalAddress(), f.value());
        };
    }

    private void renderBreakpoints(DebugSession s) {
        List<Breakpoint> bps = s.breakpoints();
        breakpointsList.getItems().setAll(bps.stream()
            .map(b -> b.id() + ": " + b.describeLocation() + (b.condition() != null ? " if " + b.condition() : "")
                + (b.enabled() ? "" : " (disabled)") + " hits=" + b.hitCount())
            .toList());
    }

    private void renderWatchpoints(DebugSession s) {
        List<Watchpoint> wps = s.watchpoints();
        watchpointsList.getItems().setAll(wps.stream()
            .map(w -> w.id() + ": " + w.describe() + " (" + w.access() + ")" + (w.enabled() ? "" : " (disabled)") + " hits=" + w.hitCount())
            .toList());
    }

    private static void logLine(ListView<String> list, String line) {
        list.getItems().add(0, line);
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }
}

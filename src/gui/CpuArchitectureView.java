package gui;

import cpu.CPU;
import cpu.registers.FLAGS;
import instruction.Instruction;
import javafx.animation.FadeTransition;
import javafx.animation.PathTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import microoperation.MicroOperation;
import microoperation.MicroOperationType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CpuArchitectureView extends StackPane {

    private final Pane canvas = new Pane();
    private final Pane overlay = new Pane();

    private final Map<String, Label> valueLabels = new HashMap<>();
    private final Map<String, StackPane> blocks = new HashMap<>();
    private final Map<String, Label> queueSlots = new HashMap<>();
    private final Map<String, Circle> busIndicators = new HashMap<>();

    private Label phaseLabel;
    private Label instructionLabel;
    private Label microLabel;
    private Label busStateLabel;
    private Label statusLabel;
    private Label aluOpLabel;
    private Label aluValueLabel;
    private Label flagValueLabel;

    public CpuArchitectureView() {
        getStyleClass().add("architecture-root");
        canvas.setMinSize(1180, 720);
        canvas.setPrefSize(1180, 720);
        overlay.setPickOnBounds(false);
        overlay.setMouseTransparent(true);
        getChildren().addAll(canvas, overlay);
        buildDiagram();
    }

    private void buildDiagram() {
        canvas.getChildren().clear();
        overlay.getChildren().clear();
        blocks.clear();
        valueLabels.clear();
        queueSlots.clear();
        busIndicators.clear();

        canvas.getChildren().addAll(
            roundedRect(170, 52, 820, 570, "#111B2D", "#35506F", 36),
            roundedRect(190, 84, 365, 506, "rgba(28,44,70,0.96)", "#406C93", 22),
            roundedRect(605, 84, 365, 506, "rgba(23,39,65,0.96)", "#406C93", 22),
            accentLine(580, 108, 580, 584, "#4D6B8A", 2.4, true)
        );

        phaseLabel = floatingInfo("Phase: IDLE", 28, 24);
        instructionLabel = floatingInfo("Instruction: ---", 28, 52);
        microLabel = floatingInfo("Micro-op: ---", 28, 80);
        busStateLabel = floatingInfo("Bus: idle", 28, 108);
        statusLabel = floatingInfo("Status: Idle", 980, 24);

        canvas.getChildren().addAll(
            titleLabel("Execution Unit (EU)", 218, 98, "block-title"),
            titleLabel("Bus Interface Unit (BIU)", 632, 98, "block-title"),
            phaseLabel, instructionLabel, microLabel, busStateLabel, statusLabel
        );

        buildExecutionUnit();
        buildBusInterfaceUnit();
        buildExternalBlocks();
        buildBusNetwork();
    }

    private void buildExecutionUnit() {
        StackPane generalRegisters = block("generalRegisters", 220, 138, 260, 172, "General Purpose Registers");
        GridPane generalGrid = registerGrid();
        generalGrid.add(registerPair("AX", "AH", "AL"), 0, 0);
        generalGrid.add(registerPair("BX", "BH", "BL"), 1, 0);
        generalGrid.add(registerPair("CX", "CH", "CL"), 0, 1);
        generalGrid.add(registerPair("DX", "DH", "DL"), 1, 1);
        generalGrid.add(singleRegister("SP"), 0, 2);
        generalGrid.add(singleRegister("BP"), 1, 2);
        generalGrid.add(singleRegister("SI"), 0, 3);
        generalGrid.add(singleRegister("DI"), 1, 3);
        ((VBox) generalRegisters.getChildren().get(0)).getChildren().add(generalGrid);

        StackPane tempBlock = block("temporary", 240, 354, 160, 74, "Temporary Registers");
        ((VBox) tempBlock.getChildren().get(0)).getChildren().add(centerBox(
            registerValue("TEMP A", "TEMP_A", "0x0000"),
            registerValue("TEMP B", "TEMP_B", "0x0000")
        ));

        StackPane aluBlock = block("alu", 238, 456, 168, 96, "ALU");
        VBox aluContent = centerBox();
        aluOpLabel = smallValue("Operation: ---");
        aluValueLabel = smallValue("Result: 0x0000");
        aluContent.getChildren().addAll(aluOpLabel, aluValueLabel);
        ((VBox) aluBlock.getChildren().get(0)).getChildren().add(aluContent);

        StackPane flagsBlock = block("flags", 222, 574, 198, 54, "Flags");
        flagValueLabel = smallValue("CF=0 PF=0 AF=0 ZF=0 SF=0 OF=0");
        ((VBox) flagsBlock.getChildren().get(0)).getChildren().add(centerBox(flagValueLabel));

        StackPane controlBlock = block("euControl", 446, 430, 88, 112, "EU Control");
        ((VBox) controlBlock.getChildren().get(0)).getChildren().add(centerBox(
            smallValue("Decode"), smallValue("Dispatch"), smallValue("Execute")
        ));

        canvas.getChildren().addAll(generalRegisters, tempBlock, aluBlock, flagsBlock, controlBlock);
    }

    private void buildBusInterfaceUnit() {
        StackPane segmentBlock = block("segmentRegisters", 650, 138, 166, 188, "Segment Registers");
        GridPane segmentGrid = registerGrid();
        segmentGrid.add(singleRegister("CS"), 0, 0);
        segmentGrid.add(singleRegister("DS"), 1, 0);
        segmentGrid.add(singleRegister("SS"), 0, 1);
        segmentGrid.add(singleRegister("ES"), 1, 1);
        segmentGrid.add(singleRegister("IP"), 0, 2);
        segmentGrid.add(singleRegister("IR"), 1, 2);
        segmentGrid.add(singleRegister("MAR"), 0, 3);
        segmentGrid.add(singleRegister("MDR"), 1, 3);
        ((VBox) segmentBlock.getChildren().get(0)).getChildren().add(segmentGrid);

        StackPane commBlock = block("commRegisters", 836, 138, 114, 188, "Internal\nComm Registers");
        ((VBox) commBlock.getChildren().get(0)).getChildren().add(centerBox(
            registerValue("IR", "IR_DISPLAY", "0x0000"),
            registerValue("MAR", "MAR_DISPLAY", "0x0000"),
            registerValue("MDR", "MDR_DISPLAY", "0x0000")
        ));

        StackPane queueBlock = block("queue", 650, 402, 300, 82, "Instruction Queue");
        HBox slotRow = new HBox(8);
        slotRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            Label slot = new Label("--");
            slot.getStyleClass().add("queue-slot");
            slot.setPrefSize(42, 42);
            slot.setAlignment(Pos.CENTER);
            slot.setTextAlignment(TextAlignment.CENTER);
            queueSlots.put("Q" + i, slot);
            slotRow.getChildren().add(slot);
        }
        ((VBox) queueBlock.getChildren().get(0)).getChildren().add(slotRow);

        StackPane busControl = block("busControl", 802, 516, 148, 74, "Bus Control Logic");
        ((VBox) busControl.getChildren().get(0)).getChildren().add(centerBox(
            smallValue("Fetch"), smallValue("Queue"), smallValue("Memory")
        ));

        canvas.getChildren().addAll(segmentBlock, commBlock, queueBlock, busControl);
    }

    private void buildExternalBlocks() {
        StackPane memory = block("memory", 1026, 146, 130, 152, "RAM");
        ((VBox) memory.getChildren().get(0)).getChildren().add(centerBox(
            smallValue("1K words"), smallValue("20-bit Addr"), smallValue("16-bit Data")
        ));

        StackPane input = block("input", 32, 474, 110, 84, "Input");
        ((VBox) input.getChildren().get(0)).getChildren().add(centerBox(
            smallValue("Program"), smallValue("Instruction")
        ));

        StackPane output = block("output", 1040, 462, 116, 96, "Output");
        ((VBox) output.getChildren().get(0)).getChildren().add(centerBox(
            smallValue("Console"), smallValue("Register/ALU")
        ));

        StackPane ioControl = block("controlUnit", 478, 246, 84, 88, "Control\nUnit");
        ((VBox) ioControl.getChildren().get(0)).getChildren().add(centerBox(
            smallValue("Decode"), smallValue("Microcode")
        ));

        canvas.getChildren().addAll(memory, input, output, ioControl);
    }

    private void buildBusNetwork() {
        canvas.getChildren().addAll(
            labelAt("Address Bus (20 bits)", 642, 30, "bus-title"),
            labelAt("Data Bus (16 bits)", 1030, 320, "bus-title"),
            labelAt("Internal ALU Bus", 362, 332, "bus-title"),
            labelAt("External Bus", 1018, 424, "bus-title"),
            labelAt("Input -> Memory -> BIU -> Queue -> IR -> EU -> ALU -> Output", 170, 658, "flow-caption")
        );

        canvas.getChildren().addAll(
            accentLine(706, 50, 1110, 50, "#4FC3F7", 5.2, false),
            accentLine(1110, 50, 1110, 306, "#4FC3F7", 5.2, false),
            accentLine(430, 346, 668, 346, "#47D7AC", 4.6, false),
            accentLine(1110, 328, 1110, 432, "#4FC3F7", 4.6, false),
            accentLine(962, 432, 1110, 432, "#4FC3F7", 4.6, false),
            accentLine(150, 514, 220, 514, "#B58CFF", 4.6, false),
            accentLine(960, 514, 1038, 514, "#FFB85C", 4.6, false),
            accentLine(580, 470, 650, 470, "#47D7AC", 4.2, false),
            accentLine(534, 290, 650, 290, "#47D7AC", 4.2, false),
            accentLine(404, 398, 404, 456, "#FFB85C", 4.6, false),
            accentLine(404, 504, 404, 574, "#FFB85C", 4.6, false),
            accentLine(404, 504, 478, 504, "#47D7AC", 4.2, false),
            accentLine(478, 290, 478, 246, "#47D7AC", 3.8, false),
            accentLine(816, 484, 816, 516, "#8D9FB6", 3.8, true)
        );

        CubicCurve fetchCurve = new CubicCurve(816, 444, 816, 360, 862, 334, 892, 326);
        styleCurve(fetchCurve, "#8D9FB6", 3.2, true);
        CubicCurve inputCurve = new CubicCurve(142, 514, 164, 446, 186, 388, 220, 346);
        styleCurve(inputCurve, "#B58CFF", 3.2, true);
        CubicCurve outputCurve = new CubicCurve(960, 514, 1004, 506, 1018, 498, 1040, 492);
        styleCurve(outputCurve, "#FFB85C", 3.2, true);
        canvas.getChildren().addAll(fetchCurve, inputCurve, outputCurve);

        busIndicators.put("address", busPulse(716, 50, "#4FC3F7"));
        busIndicators.put("data", busPulse(1110, 340, "#4FC3F7"));
        busIndicators.put("control", busPulse(580, 470, "#47D7AC"));
        busIndicators.put("alu", busPulse(404, 474, "#FFB85C"));
        busIndicators.put("memory", busPulse(1082, 220, "#B58CFF"));
    }

    public void update(CPU cpu, Instruction currentInstruction, MicroOperation op, List<String> queueValues, String statusText) {
        phaseLabel.setText("Phase: " + phase(op));
        instructionLabel.setText("Instruction: " + (currentInstruction == null ? "---" : currentInstruction));
        microLabel.setText("Micro-op: " + (op == null ? "---" : op.getRtlDescription()));
        busStateLabel.setText("Bus: " + describeBus(cpu));
        statusLabel.setText("Status: " + statusText);

        setValue("AX", cpu.getRegister("AX").toHex());
        setValue("BX", cpu.getRegister("BX").toHex());
        setValue("CX", cpu.getRegister("CX").toHex());
        setValue("DX", cpu.getRegister("DX").toHex());
        setValue("AH", byteHex(cpu.getRegister("AX").highByte()));
        setValue("AL", byteHex(cpu.getRegister("AX").lowByte()));
        setValue("BH", byteHex(cpu.getRegister("BX").highByte()));
        setValue("BL", byteHex(cpu.getRegister("BX").lowByte()));
        setValue("CH", byteHex(cpu.getRegister("CX").highByte()));
        setValue("CL", byteHex(cpu.getRegister("CX").lowByte()));
        setValue("DH", byteHex(cpu.getRegister("DX").highByte()));
        setValue("DL", byteHex(cpu.getRegister("DX").lowByte()));
        setValue("SP", cpu.getRegister("SP").toHex());
        setValue("BP", cpu.getRegister("BP").toHex());
        setValue("SI", cpu.getRegister("SI").toHex());
        setValue("DI", cpu.getRegister("DI").toHex());
        setValue("CS", cpu.getRegister("CS").toHex());
        setValue("DS", cpu.getRegister("DS").toHex());
        setValue("SS", cpu.getRegister("SS").toHex());
        setValue("ES", cpu.getRegister("ES").toHex());
        setValue("IP", cpu.getRegister("IP").toHex());
        setValue("IR", cpu.getRegister("IR").toHex());
        setValue("MAR", cpu.getRegister("MAR").toHex());
        setValue("MDR", cpu.getRegister("MDR").toHex());
        setValue("IR_DISPLAY", cpu.getRegister("IR").toHex());
        setValue("MAR_DISPLAY", cpu.getRegister("MAR").toHex());
        setValue("MDR_DISPLAY", cpu.getRegister("MDR").toHex());
        // TEMP_A/TEMP_B: show ALU operands when active, otherwise last known values
        setValue("TEMP_A", cpu.getAlu().isActive() ? wordHex(cpu.getAlu().getLastA()) : "---");
        setValue("TEMP_B", cpu.getAlu().isActive() ? wordHex(cpu.getAlu().getLastB()) : "---");

        aluOpLabel.setText("Operation: " + (cpu.getAlu().isActive() ? cpu.getAlu().getOperationString() : "---"));
        aluValueLabel.setText("Result: " + wordHex(cpu.getAlu().getLastResult()));

        FLAGS flags = cpu.getFlags();
        flagValueLabel.setText("CF=" + b(flags.isCarry()) + " PF=" + b(flags.isParity()) + " AF=" + b(flags.isAuxCarry())
            + " ZF=" + b(flags.isZero()) + " SF=" + b(flags.isSign()) + " OF=" + b(flags.isOverflow()));

        for (int i = 0; i < 6; i++) {
            Label slot = queueSlots.get("Q" + i);
            String value = i < queueValues.size() ? queueValues.get(i) : "--";
            String text = value.contains(". ") ? value.substring(value.indexOf(". ") + 2) : value;
            slot.setText(text.length() > 10 ? text.substring(0, 10) : text);
            slot.getStyleClass().remove("queue-slot-active");
            if (i == 0) slot.getStyleClass().add("queue-slot-active");
        }

        highlightBlocks(op);
        highlightBuses(cpu, op);
        if (op != null) animate(op, cpu);
    }

    private void highlightBlocks(MicroOperation op) {
        blocks.values().forEach(block -> block.getStyleClass().remove("active-block"));
        if (op == null) return;
        activate("euControl");
        switch (op.getType()) {
            case MAR_LOAD_PC, MDR_LOAD_MEMORY, IR_LOAD_MDR, MAR_LOAD_ADDR, MDR_LOAD_REG, MEMORY_WRITE_MDR -> {
                activate("busControl"); activate("segmentRegisters");
                activate("commRegisters"); activate("memory");
            }
            case REG_LOAD_IMM, REG_LOAD_MDR, REG_LOAD_REG -> activate("generalRegisters");
            case ALU_ADD, ALU_SUB, ALU_INC, ALU_DEC, ALU_AND, ALU_OR, ALU_XOR, ALU_NOT, ALU_CMP,
                 ALU_MUL, ALU_DIV, ALU_SHL, ALU_SHR, ALU_ROL, ALU_ROR, ALU_IMM -> {
                activate("generalRegisters"); activate("alu");
                activate("flags"); activate("temporary");
            }
            case DECODE -> { activate("queue"); activate("controlUnit"); activate("euControl"); }
            case HALT -> activate("output");
            default -> { }
        }
    }

    private void highlightBuses(CPU cpu, MicroOperation op) {
        pulse("address", cpu.getAddressBus().isActive());
        pulse("data", cpu.getDataBus().isActive());
        pulse("control", op != null && (op.getType() == MicroOperationType.DECODE || op.getType() == MicroOperationType.IR_LOAD_MDR));
        pulse("alu", cpu.getAlu().isActive());
        pulse("memory", cpu.getAddressBus().isActive() || cpu.getDataBus().isActive());
    }

    private void animate(MicroOperation op, CPU cpu) {
        String source = sourceBlock(op);
        String destination = destinationBlock(op);
        if (source == null || destination == null || !blocks.containsKey(source) || !blocks.containsKey(destination)) return;

        Bounds sourceBounds = blocks.get(source).getBoundsInParent();
        Bounds destinationBounds = blocks.get(destination).getBoundsInParent();
        double startX = sourceBounds.getMinX() + sourceBounds.getWidth() / 2;
        double startY = sourceBounds.getMinY() + sourceBounds.getHeight() / 2;
        double endX = destinationBounds.getMinX() + destinationBounds.getWidth() / 2;
        double endY = destinationBounds.getMinY() + destinationBounds.getHeight() / 2;

        Circle packet = new Circle(6);
        packet.setFill(flowColor(op));
        packet.setStroke(Color.rgb(255, 255, 255, 0.8));
        packet.setStrokeWidth(1.2);
        overlay.getChildren().add(packet);

        Path path = new Path();
        path.getElements().add(new javafx.scene.shape.MoveTo(startX, startY));
        double controlOffset = Math.abs(endX - startX) > Math.abs(endY - startY) ? 40 : 20;
        path.getElements().add(new javafx.scene.shape.CubicCurveTo(
            startX + controlOffset, startY, endX - controlOffset, endY, endX, endY));

        PathTransition transition = new PathTransition(Duration.millis(620), path, packet);
        transition.setOnFinished(event -> overlay.getChildren().remove(packet));
        transition.play();
    }

    private String sourceBlock(MicroOperation op) {
        return switch (op.getType()) {
            case MAR_LOAD_PC -> "segmentRegisters";
            case MDR_LOAD_MEMORY -> "memory";
            case IR_LOAD_MDR -> "commRegisters";
            case DECODE -> "queue";
            case MAR_LOAD_ADDR -> "controlUnit";
            case MDR_LOAD_REG, REG_LOAD_REG -> "generalRegisters";
            case REG_LOAD_MDR -> "commRegisters";
            case REG_LOAD_IMM -> "input";
            case MEMORY_WRITE_MDR -> "commRegisters";
            case PC_LOAD_ADDR -> "controlUnit";
            case ALU_ADD, ALU_SUB, ALU_INC, ALU_DEC, ALU_AND, ALU_OR, ALU_XOR, ALU_NOT, ALU_CMP,
                 ALU_MUL, ALU_DIV, ALU_SHL, ALU_SHR, ALU_ROL, ALU_ROR, ALU_IMM -> "temporary";
            case HALT -> "euControl";
            default -> "euControl";
        };
    }

    private String destinationBlock(MicroOperation op) {
        return switch (op.getType()) {
            case MAR_LOAD_PC, MAR_LOAD_ADDR, IR_LOAD_MDR, MDR_LOAD_MEMORY, MDR_LOAD_REG -> "commRegisters";
            case REG_LOAD_MDR, REG_LOAD_IMM, REG_LOAD_REG -> "generalRegisters";
            case MEMORY_WRITE_MDR -> "memory";
            case PC_LOAD_ADDR -> "segmentRegisters";
            case DECODE -> "controlUnit";
            case ALU_ADD, ALU_SUB, ALU_INC, ALU_DEC, ALU_AND, ALU_OR, ALU_XOR, ALU_NOT, ALU_CMP,
                 ALU_MUL, ALU_DIV, ALU_SHL, ALU_SHR, ALU_ROL, ALU_ROR, ALU_IMM -> "alu";
            case HALT -> "output";
            default -> "euControl";
        };
    }

    private Color flowColor(MicroOperation op) {
        return switch (op.getType()) {
            case MDR_LOAD_MEMORY, MEMORY_WRITE_MDR, MAR_LOAD_ADDR, MAR_LOAD_PC -> Color.web("#B58CFF");
            case DECODE, IR_LOAD_MDR, PC_LOAD_ADDR -> Color.web("#47D7AC");
            case HALT -> Color.web("#F87171");
            default -> Color.web("#4FC3F7");
        };
    }

    private void pulse(String key, boolean active) {
        Circle indicator = busIndicators.get(key);
        if (indicator == null) return;
        indicator.setOpacity(active ? 1.0 : 0.18);
        if (active) {
            FadeTransition fade = new FadeTransition(Duration.millis(520), indicator);
            fade.setFromValue(1.0); fade.setToValue(0.35); fade.setCycleCount(2); fade.setAutoReverse(true);
            fade.play();
        }
    }

    private void activate(String key) {
        StackPane block = blocks.get(key);
        if (block != null && !block.getStyleClass().contains("active-block"))
            block.getStyleClass().add("active-block");
    }

    private void setValue(String key, String value) {
        Label label = valueLabels.get(key);
        if (label != null) label.setText(value);
    }

    private String phase(MicroOperation op) {
        if (op == null) return "IDLE";
        return switch (op.getType()) {
            case MAR_LOAD_PC, MDR_LOAD_MEMORY, IR_LOAD_MDR -> "FETCH";
            case DECODE -> "DECODE";
            case HALT -> "HALT";
            default -> "EXECUTE";
        };
    }

    private String describeBus(CPU cpu) {
        String address = cpu.getAddressBus().isActive() ? cpu.getAddressBus().toString() : "";
        String data = cpu.getDataBus().isActive() ? cpu.getDataBus().toString() : "";
        String control = cpu.getControlBus().getActiveSignals().isEmpty() ? "" : cpu.getControlBus().toString();
        return List.of(address, data, control).stream().filter(s -> !s.isBlank())
            .reduce((a, b) -> a + " | " + b).orElse("idle");
    }

    private StackPane block(String key, double x, double y, double width, double height, String title) {
        StackPane block = new StackPane();
        block.getStyleClass().add("diagram-block");
        block.setLayoutX(x); block.setLayoutY(y); block.setPrefSize(width, height);
        VBox shell = new VBox(8);
        shell.setAlignment(Pos.TOP_CENTER);
        shell.setPadding(new Insets(12, 14, 12, 14));
        shell.prefWidthProperty().bind(block.widthProperty());
        shell.prefHeightProperty().bind(block.heightProperty());
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("diagram-block-title");
        titleLabel.setWrapText(true);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        shell.getChildren().add(titleLabel);
        block.getChildren().add(shell);
        blocks.put(key, block);
        return block;
    }

    private VBox centerBox(Node... labels) { VBox box = new VBox(6); box.setAlignment(Pos.CENTER); box.getChildren().addAll(labels); return box; }
    private GridPane registerGrid() { GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setAlignment(Pos.CENTER); return grid; }

    private VBox registerPair(String main, String high, String low) {
        VBox box = new VBox(4); box.setAlignment(Pos.CENTER);
        box.getChildren().addAll(registerValue(main, main, "0x0000"), bytePair(high, low));
        return box;
    }

    private HBox bytePair(String high, String low) {
        HBox row = new HBox(6); row.setAlignment(Pos.CENTER);
        row.getChildren().addAll(registerValue(high, high, "0x00"), registerValue(low, low, "0x00"));
        return row;
    }

    private VBox singleRegister(String name) {
        VBox box = new VBox(4); box.setAlignment(Pos.CENTER);
        box.getChildren().add(registerValue(name, name, "0x0000"));
        return box;
    }

    private VBox registerValue(String labelText, String key, String initial) {
        VBox box = new VBox(2); box.setAlignment(Pos.CENTER);
        Label label = new Label(labelText); label.getStyleClass().add("mini-register-name");
        Label value = new Label(initial); value.getStyleClass().add("mini-register-value");
        valueLabels.put(key, value);
        box.getChildren().addAll(label, value);
        return box;
    }

    private Label smallValue(String text) {
        Label label = new Label(text); label.getStyleClass().add("small-diagram-value");
        label.setWrapText(true); label.setTextAlignment(TextAlignment.CENTER);
        return label;
    }

    private javafx.scene.shape.Rectangle roundedRect(double x, double y, double width, double height, String fill, String stroke, double radius) {
        javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(x, y, width, height);
        rect.setFill(Color.web(fill)); rect.setStroke(Color.web(stroke)); rect.setStrokeWidth(2);
        rect.setArcWidth(radius); rect.setArcHeight(radius);
        return rect;
    }

    private Line accentLine(double startX, double startY, double endX, double endY, String color, double width, boolean dashed) {
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(Color.web(color)); line.setStrokeWidth(width); line.setStrokeLineCap(StrokeLineCap.ROUND);
        if (dashed) line.getStrokeDashArray().addAll(12.0, 10.0);
        return line;
    }

    private void styleCurve(CubicCurve curve, String color, double width, boolean dashed) {
        curve.setFill(null); curve.setStroke(Color.web(color)); curve.setStrokeWidth(width);
        if (dashed) curve.getStrokeDashArray().addAll(10.0, 8.0);
    }

    private Label titleLabel(String text, double x, double y, String styleClass) {
        Label label = new Label(text); label.setLayoutX(x); label.setLayoutY(y); label.getStyleClass().add(styleClass); return label;
    }

    private Label floatingInfo(String text, double x, double y) {
        Label label = new Label(text); label.setLayoutX(x); label.setLayoutY(y); label.getStyleClass().add("floating-info"); return label;
    }

    private Label labelAt(String text, double x, double y, String styleClass) {
        Label label = new Label(text); label.setLayoutX(x); label.setLayoutY(y); label.getStyleClass().add(styleClass); return label;
    }

    private Circle busPulse(double x, double y, String color) {
        Circle pulse = new Circle(x, y, 7, Color.web(color)); pulse.setOpacity(0.18);
        canvas.getChildren().add(pulse);
        return pulse;
    }

    private String wordHex(int value) { return String.format("0x%04X", value & 0xFFFF); }
    private String byteHex(int value) { return String.format("0x%02X", value & 0xFF); }
    private int b(boolean v) { return v ? 1 : 0; }
}

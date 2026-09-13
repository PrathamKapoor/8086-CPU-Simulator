package cpu;

import bus.AddressBus;
import bus.ControlBus;
import bus.DataBus;
import clock.Clock;
import cpu.registers.*;
import instruction.Instruction;
import memory.Memory;
import microoperation.MicroOperation;
import microoperation.MicroOperationExecutor;
import microoperation.MicroOperationType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * CPU — top-level component that wires all hardware modules together.
 *
 * Full 8086 register set:
 *   General:  AX, BX, CX, DX
 *   Pointer:  SP, BP
 *   Index:    SI, DI
 *   Segment:  CS, DS, SS, ES
 *   Internal: IP, IR, MAR, MDR, FLAGS
 *
 * Segment:offset address computation:
 *   physical = segment * 16 + offset (20-bit)
 *
 * Lazy micro-op generation for correct branch handling.
 */
public class CPU {

    // ---- Registers ----------------------------------------------------------
    private final AX ax; private final BX bx;
    private final CX cx; private final DX dx;
    private final SP sp; private final BP bp;
    private final SI si; private final DI di;
    private final CS cs; private final DS ds;
    private final SS ss; private final ES es;
    private final PC pc; private final IR ir;
    private final MAR mar; private final MDR mdr;
    private final FLAGS flags;
    private final Map<String, Register> registerFile;

    // ---- Buses --------------------------------------------------------------
    private final AddressBus addressBus;
    private final DataBus    dataBus;
    private final ControlBus controlBus;

    // ---- Core components ----------------------------------------------------
    private final ALU                  alu;
    private final Memory               memory;
    private final Clock                clock;
    private final ControlUnit          controlUnit;
    private final MicroOperationExecutor executor;
    private final InstructionDecoder   decoder;

    // ---- Microarchitecture ---------------------------------------------------
    private final cpu.biu.BusInterfaceUnit biu;
    private final cpu.eu.ExecutionUnit     eu;

    // ---- Program state ------------------------------------------------------
    private List<Instruction>    program     = new ArrayList<>();
    private List<MicroOperation> microOpBatch = new ArrayList<>();
    private int  batchIndex     = 0;
    private long totalCyclesRun = 0;
    private boolean halted      = false;

    /** Flat trace list — appended on every executed micro-op (for the trace panel). */
    private final List<MicroOperation> executedTrace = new ArrayList<>();

    // ---- Observer callbacks -------------------------------------------------
    private Consumer<MicroOperation> onMicroOpExecuted;
    private Runnable                 onHalt;

    // =========================================================================
    //  Construction
    // =========================================================================

    public CPU() {
        ax = new AX(); bx = new BX(); cx = new CX(); dx = new DX();
        sp = new SP(); bp = new BP(); si = new SI(); di = new DI();
        cs = new CS(); ds = new DS(); ss = new SS(); es = new ES();
        pc = new PC(); ir = new IR(); mar = new MAR(); mdr = new MDR();
        flags = new FLAGS();

        registerFile = new LinkedHashMap<>();
        registerFile.put("AX", ax); registerFile.put("BX", bx);
        registerFile.put("CX", cx); registerFile.put("DX", dx);
        registerFile.put("SP", sp); registerFile.put("BP", bp);
        registerFile.put("SI", si); registerFile.put("DI", di);
        registerFile.put("CS", cs); registerFile.put("DS", ds);
        registerFile.put("SS", ss); registerFile.put("ES", es);
        registerFile.put("IP", pc); registerFile.put("IR", ir);
        registerFile.put("MAR", mar); registerFile.put("MDR", mdr);
        registerFile.put("FLAGS", flags);

        addressBus = new AddressBus();
        dataBus    = new DataBus();
        controlBus = new ControlBus();

        alu    = new ALU(flags);
        clock  = new Clock();
        memory = new Memory(addressBus, dataBus, controlBus);

        decoder  = new InstructionDecoder();
        executor = new MicroOperationExecutor(
                registerFile, pc, ir, mar, mdr, flags,
                alu, memory, addressBus, dataBus, controlBus, clock);
        controlUnit = new ControlUnit(executor, decoder, registerFile);
        controlUnit.setFlags(flags);
        controlUnit.setBuses(controlBus, dataBus);

        // BIU / EU integration (Phase 3)
        biu = new cpu.biu.BusInterfaceUnit(cs, pc, memory);
        eu  = new cpu.eu.ExecutionUnit(this, biu.getPrefetchQueue());

        // Set initial segment values for realistic operation
        ds.load(0x0000);
        ss.load(0x0000);
        es.load(0x0000);
        cs.load(0x0000);
    }

    // =========================================================================
    //  Program loading
    // =========================================================================

    /**
     * Load a program into memory and prime the first instruction.
     * Instructions are stored at addresses 0..N-1 so the fetch bus read looks real.
     */
    public void loadProgram(List<Instruction> instructions) {
        reset();
        this.program = new ArrayList<>(instructions);

        // Write instruction indices into memory for realistic fetch
        for (int i = 0; i < instructions.size(); i++) {
            memory.directWrite(i, i);
        }

        // Initialize BIU / EU state
        biu.reset();
        eu.beginDecode();

        halted = false;
        primeNextInstruction();
    }

    // =========================================================================
    //  Execution
    // =========================================================================

    /**
     * Execute ONE micro-operation (one clock cycle).
     * @return the MicroOperation that was executed, or null if halted.
     */
    public MicroOperation step() {
        if (halted || microOpBatch.isEmpty()) return null;

        // BIU tick: fetch instruction bytes into prefetch queue
        if (!biu.isHalted() && biu.getPrefetchQueue().availableBytes() < cpu.biu.PrefetchQueue.CAPACITY) {
            biu.tick();
        }

        // EU tick: consume instruction bytes from prefetch queue
        if (eu.canConsume()) {
            int byteVal = eu.consumeByte();
            // Conceptual model: byte represents instruction index (simulated)
            // Because InstructionParser operates on assembly text, the EU
            // tracks consumption conceptually rather than binary-decoding.
            // Full binary decoder is outside this simplified model scope.
            eu.beginDecode();
            if (byteVal >= 0 && byteVal < program.size()) {
                // Conceptual decode: EU knows which instruction is being fetched
                eu.endDecode();
            }
        }

        MicroOperation op = microOpBatch.get(batchIndex);
        clock.tick();
        op.execute();
        totalCyclesRun++;
        executedTrace.add(op);

        // Update phase tracking
        controlUnit.setCurrentPhase(determinePhase(op.getType()));

        // Reset ALU active flag after non-ALU ops
        if (!isAluType(op.getType())) alu.setActive(false);

        if (op.getType() == MicroOperationType.HALT) {
            halted = true;
            microOpBatch.clear();
            batchIndex = 0;
            if (onHalt != null) onHalt.run();
            if (onMicroOpExecuted != null) onMicroOpExecuted.accept(op);
            return op;
        }

        if (onMicroOpExecuted != null) onMicroOpExecuted.accept(op);

        batchIndex++;

        if (batchIndex >= microOpBatch.size()) {
            primeNextInstruction();
        }

        return op;
    }

    /**
     * Execute ALL remaining micro-ops until HLT or end of program.
     */
    public void run() {
        while (!halted && !microOpBatch.isEmpty()) {
            step();
        }
    }

    // =========================================================================
    //  Reset
    // =========================================================================

    public void reset() {
        for (Register r : registerFile.values()) r.clear();
        // Initialize SP to top of 64K stack segment per 8086 architecture
        getRegister("SP").load(0xFFFE);
        getRegister("SS").load(0x0000);
        getRegister("CS").load(0x0000);
        memory.reset();
        clock.reset();
        alu.reset();
        decoder.reset();
        addressBus.clear();
        dataBus.clear();
        controlBus.clearAll();
        microOpBatch.clear();
        batchIndex     = 0;
        totalCyclesRun = 0;
        halted         = false;
        executedTrace.clear();
        program        = new ArrayList<>();
        controlUnit.setCurrentPhase("IDLE");
    }

    // =========================================================================
    //  Lazy micro-op priming
    // =========================================================================

    private void primeNextInstruction() {
        microOpBatch.clear();
        batchIndex = 0;

        int instrIdx = pc.output();

        if (instrIdx < 0 || instrIdx >= program.size()) {
            halted = true;
            return;
        }

        Instruction instr = program.get(instrIdx);
        microOpBatch.addAll(controlUnit.generateMicroOps(instr));
    }

    // =========================================================================
    //  Segment:offset address computation
    // =========================================================================

    /**
     * Compute physical address from segment:offset.
     * physical = segment * 16 + offset (20-bit result).
     */
    public int computePhysicalAddress(int segment, int offset) {
        return ((segment * 16) + offset) & 0xFFFFF;
    }

    /**
     * Compute physical address using a named segment register and an offset.
     */
    public int computePhysicalAddress(String segReg, int offset) {
        Register seg = registerFile.get(segReg.toUpperCase());
        if (seg == null) throw new IllegalArgumentException("Unknown segment: " + segReg);
        return computePhysicalAddress(seg.output(), offset);
    }

    // =========================================================================
    //  Accessors for GUI
    // =========================================================================

    public Register              getRegister(String name)   { return registerFile.get(name); }
    public Map<String, Register> getAllRegisters()           { return registerFile; }
    public FLAGS                 getFlags()                  { return flags; }
    public ALU                   getAlu()                    { return alu; }
    public Memory                getMemory()                 { return memory; }
    public Clock                 getClock()                  { return clock; }
    public AddressBus            getAddressBus()             { return addressBus; }
    public DataBus               getDataBus()                { return dataBus; }
    public ControlBus            getControlBus()             { return controlBus; }
    public ControlUnit           getControlUnit()            { return controlUnit; }
    public InstructionDecoder    getDecoder()                { return decoder; }
    public List<Instruction>     getProgram()                { return program; }
    public List<MicroOperation>  getExecutedTrace()          { return executedTrace; }
    public int                   getBatchIndex()             { return batchIndex; }
    public boolean               isHalted()                  { return halted; }
    public long                  getTotalCyclesRun()         { return totalCyclesRun; }

    public MicroOperation getCurrentMicroOp() {
        if (halted || microOpBatch.isEmpty() || batchIndex >= microOpBatch.size()) return null;
        return microOpBatch.get(batchIndex);
    }

    public MicroOperation getLastExecutedMicroOp() {
        if (executedTrace.isEmpty()) return null;
        return executedTrace.get(executedTrace.size() - 1);
    }

    public Instruction getCurrentInstruction() {
        return decoder.getCurrentInstruction();
    }

    public void setOnMicroOpExecuted(Consumer<MicroOperation> cb) { this.onMicroOpExecuted = cb; }
    public void setOnHalt(Runnable cb)                            { this.onHalt = cb; }

    // ---- Helpers -------------------------------------------------------------

    private String determinePhase(MicroOperationType t) {
        return switch (t) {
            case MAR_LOAD_PC, MDR_LOAD_MEMORY, IR_LOAD_MDR -> "FETCH";
            case DECODE -> "DECODE";
            case HALT -> "HALT";
            default -> "EXECUTE";
        };
    }

    private static boolean isAluType(MicroOperationType t) {
        return switch (t) {
            case ALU_ADD, ALU_SUB, ALU_ADC, ALU_SBB, ALU_INC, ALU_DEC, ALU_NEG,
                 ALU_AND, ALU_OR, ALU_XOR, ALU_NOT, ALU_CMP, ALU_TEST,
                 ALU_MUL, ALU_IMUL, ALU_DIV, ALU_IDIV,
                  ALU_SHL, ALU_SHR, ALU_SAR, ALU_ROL, ALU_ROR, ALU_RCL, ALU_RCR,
                  ALU_IMM, ALU_ADJUST -> true;
            default -> false;
        };
    }
}

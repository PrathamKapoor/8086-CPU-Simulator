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
import cpu.biu.BiuTickResult;
import cpu.microarchitecture.*;
import simulator.profiler.TimingModel;
import machinecode.DecodedInstruction;
import machinecode.Intel8086Decoder;

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

    // ---- Microarchitecture metrics -------------------------------------------
    private int  stallCycles          = 0;
    private int  queueFlushes         = 0;
    private int  bytesFetched         = 0;
    private int  bytesConsumed        = 0;
    private int  maxQueueOccupancy    = 0;
    private int  totalOverlapCycles   = 0;
    private int  busActiveCycles      = 0;
    private int  biuFetchEvents       = 0;
    private TimingModel timingModel = TimingModel.FUNCTIONAL;
    private final List<CycleSnapshot> cycleTrace = new ArrayList<>();
    private int timedInstructionIndex = -1;

    // ---- Program state ------------------------------------------------------
    private List<Instruction>    program     = new ArrayList<>();
    private boolean machineCodeProgram = false;
    private byte[] machineCode = new byte[0];
    private List<Integer> machineInstructionOffsets = new ArrayList<>();
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
        this.machineCodeProgram = false;
        this.machineCode = new byte[0];
        this.machineInstructionOffsets = new ArrayList<>();

        // Write instruction indices into memory for realistic fetch
        for (int i = 0; i < instructions.size(); i++) {
            memory.directWrite(i, i);
        }

        // Initialize BIU / EU state
        biu.reset();
        biu.getFetchState().setNextFetchOffset(0);
        eu.beginDecode();
        stallCycles = 0;
        queueFlushes = 0;
        bytesFetched = 0;
        bytesConsumed = 0;
        maxQueueOccupancy = 0;
        totalOverlapCycles = 0;
        busActiveCycles = 0;
        biuFetchEvents = 0;
        cycleTrace.clear();
        timedInstructionIndex = -1;

        halted = false;
        primeNextInstruction();
    }

    /**
     * Load a real 8086 byte stream.  The decoder creates the same semantic
     * Instruction objects used by the source-token path; execution still goes
     * through this CPU's existing ControlUnit and micro-operation executor.
     */
    public void loadMachineCode(byte[] bytes) {
        if (bytes == null) throw new IllegalArgumentException("machine-code bytes must not be null");
        reset();
        Intel8086Decoder machineDecoder = new Intel8086Decoder();
        List<Instruction> decoded = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            DecodedInstruction next = machineDecoder.decode(bytes, offset);
            decoded.add(next.instruction());
            offsets.add(offset);
            offset = next.nextOffset();
        }
        this.program = translateMachineControlTargets(decoded, offsets);
        this.machineCodeProgram = true;
        this.machineCode = java.util.Arrays.copyOf(bytes, bytes.length);
        this.machineInstructionOffsets = offsets;
        for (int i = 0; i < bytes.length; i++) memory.directWrite(i, bytes[i] & 0xFF);

        biu.reset();
        biu.getFetchState().setNextFetchOffset(0);
        eu.beginDecode();
        stallCycles = queueFlushes = bytesFetched = bytesConsumed = maxQueueOccupancy = totalOverlapCycles = busActiveCycles = biuFetchEvents = 0;
        cycleTrace.clear();
        timedInstructionIndex = -1;
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
        // The field is always initialized; the guard keeps the legacy body below
        // source-compatible while the deterministic protocol owns normal execution.
        if (timingModel != null) return timingModel == TimingModel.FUNCTIONAL ? stepFunctional() : stepTimed();

        // BIU tick: fetch instruction bytes into prefetch queue
        boolean biuActiveThisCycle = false;
        if (!biu.isHalted() && biu.getPrefetchQueue().availableBytes() < cpu.biu.PrefetchQueue.CAPACITY) {
            biu.tick();
            bytesFetched++;
            biuFetchEvents++;
            biuActiveThisCycle = true;
            if (biu.getPrefetchQueue().availableBytes() > maxQueueOccupancy) {
                maxQueueOccupancy = biu.getPrefetchQueue().availableBytes();
            }
        }

        // EU tick: consume instruction bytes from prefetch queue
        boolean euActiveThisCycle = false;
        if (eu.canConsume()) {
            int byteVal = eu.consumeByte();
            bytesConsumed++;
            euActiveThisCycle = true;
            eu.beginDecode();
            if (byteVal >= 0 && byteVal < program.size()) {
                eu.endDecode();
            }
        } else {
            stallCycles++;
        }

        // Overlap tracking: both BIU and EU active in same cycle
        if (biuActiveThisCycle && euActiveThisCycle) {
            totalOverlapCycles++;
        }

        // Flush BIU prefetch queue for control-flow events
        instruction.Opcode currentOpcode = null;
        if (decoder != null && decoder.getCurrentInstruction() != null) {
            currentOpcode = decoder.getCurrentInstruction().getOpcode();
        }
        if (currentOpcode != null && requiresFlush(currentOpcode)) {
            biu.getPrefetchQueue().clear();
            queueFlushes++;
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

    private MicroOperation stepFunctional() {
        int index = pc.output();
        MicroOperation op = microOpBatch.get(batchIndex);
        boolean retired = batchIndex + 1 >= microOpBatch.size() || op.getType() == MicroOperationType.HALT;
        MicroOperation result = executeCurrent();
        List<MicroarchitectureEvent> events = retired ? List.of(new MicroarchitectureEvent(totalCyclesRun,
            MicroarchitectureEventType.INSTRUCTION_RETIRE, -1, index, 1)) : List.of();
        snapshot(UnitState.IDLE, UnitState.EU_ACTIVE, BusOwner.NONE, index, op, retired, events);
        return result;
    }

    private MicroOperation stepTimed() {
        boolean start = batchIndex == 0;
        if (start) timedInstructionIndex = pc.output();
        int index = timedInstructionIndex;
        MicroOperation op = microOpBatch.get(batchIndex);
        List<MicroarchitectureEvent> events = new ArrayList<>();
        int requiredBytes = machineCodeProgram ? instructionByteLength(index) : 1;
        if (start && biu.getPrefetchQueue().size() < requiredBytes) {
            BiuTickResult fetched = biu.tick(true, fetchLength());
            fetchEvents(events, fetched);
            events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.QUEUE_EMPTY, -1, -1, 0));
            events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.EU_STALL, -1, -1, 0));
            stallCycles++; clock.tick(); totalCyclesRun++;
            snapshot(fetched.fetched() ? UnitState.BIU_ACTIVE : UnitState.IDLE, UnitState.EU_WAITING_FOR_QUEUE,
                fetched.fetched() ? BusOwner.BIU_FETCH : BusOwner.NONE, index, op, false, events);
            return null;
        }
        boolean memoryOperation = usesExternalMemory(op.getType());
        BiuTickResult fetched = biu.tick(!memoryOperation, fetchLength());
        fetchEvents(events, fetched);
        BusOwner owner = memoryOperation ? BusOwner.EU_MEMORY : (fetched.fetched() ? BusOwner.BIU_FETCH : BusOwner.NONE);
        if (memoryOperation) {
            events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.BUS_BUSY, -1, -1, 0));
            events.add(new MicroarchitectureEvent(totalCyclesRun + 1, isMemoryWrite(op.getType()) ? MicroarchitectureEventType.MEM_WRITE : MicroarchitectureEventType.MEM_READ, -1, -1, 0));
            if (fetched.waitingForBus()) events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.BIU_STALL, -1, -1, 0));
        }
        if (start) {
            for (int consumed = 0; consumed < requiredBytes; consumed++) {
                int token = eu.consumeByte(); bytesConsumed++;
                events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.QUEUE_POP, -1, token, 1));
            }
            events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.EU_START, -1, index, 1));
        }
        boolean retired = batchIndex + 1 >= microOpBatch.size() || op.getType() == MicroOperationType.HALT;
        MicroOperation result = executeCurrent();
        if (retired) {
            events.add(new MicroarchitectureEvent(totalCyclesRun, MicroarchitectureEventType.EU_COMPLETE, -1, index, 1));
            events.add(new MicroarchitectureEvent(totalCyclesRun, MicroarchitectureEventType.INSTRUCTION_RETIRE, -1, index, 1));
            if (pc.output() != ((index + 1) & 0xFFFF)) {
                int flushed = biu.getPrefetchQueue().size(); biu.flushTo(fetchOffsetForInstruction(pc.output())); queueFlushes++;
                events.add(new MicroarchitectureEvent(totalCyclesRun, MicroarchitectureEventType.CONTROL_TRANSFER, -1, pc.output(), 1));
                events.add(new MicroarchitectureEvent(totalCyclesRun, MicroarchitectureEventType.QUEUE_FLUSH, -1, -1, flushed));
            }
            timedInstructionIndex = -1;
        }
        if (fetched.fetched() && !memoryOperation) totalOverlapCycles++;
        snapshot(fetched.fetched() ? UnitState.BIU_ACTIVE : (fetched.waitingForBus() ? UnitState.BIU_WAITING_FOR_BUS : (fetched.queueFull() ? UnitState.QUEUE_FULL : UnitState.IDLE)),
            UnitState.EU_ACTIVE, owner, index, op, retired, events);
        return result;
    }

    private MicroOperation executeCurrent() {
        MicroOperation op = microOpBatch.get(batchIndex);
        clock.tick(); op.execute(); totalCyclesRun++; executedTrace.add(op);
        controlUnit.setCurrentPhase(determinePhase(op.getType()));
        if (!isAluType(op.getType())) alu.setActive(false);
        if (op.getType() == MicroOperationType.HALT) {
            halted = true; microOpBatch.clear(); batchIndex = 0;
            if (onHalt != null) onHalt.run(); if (onMicroOpExecuted != null) onMicroOpExecuted.accept(op); return op;
        }
        if (onMicroOpExecuted != null) onMicroOpExecuted.accept(op);
        batchIndex++; if (batchIndex >= microOpBatch.size()) primeNextInstruction(); return op;
    }

    private void fetchEvents(List<MicroarchitectureEvent> events, BiuTickResult result) {
        if (!result.fetched()) { if (result.queueFull()) events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.QUEUE_FULL, -1, -1, 0)); return; }
        bytesFetched++; biuFetchEvents++; busActiveCycles++; maxQueueOccupancy = Math.max(maxQueueOccupancy, biu.getPrefetchQueue().size());
        events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.FETCH_BYTE, result.physicalAddress(), result.value(), 1));
        events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.QUEUE_PUSH, result.physicalAddress(), result.value(), 1));
        events.add(new MicroarchitectureEvent(totalCyclesRun + 1, MicroarchitectureEventType.BUS_BUSY, result.physicalAddress(), result.value(), 1));
    }

    private void snapshot(UnitState biuState, UnitState euState, BusOwner owner, int index, MicroOperation op, boolean retired, List<MicroarchitectureEvent> events) {
        List<Integer> queue = new ArrayList<>(); for (int value : biu.getPrefetchQueue().getContents()) queue.add(value);
        cycleTrace.add(new CycleSnapshot(totalCyclesRun, biuState, euState, owner, queue, queue.size(), index,
            index >= 0 && index < program.size() ? program.get(index).toString() : "", op.getRtlDescription(), retired, events));
    }

    private static boolean usesExternalMemory(MicroOperationType type) { return type == MicroOperationType.MDR_LOAD_MEMORY || type == MicroOperationType.MEMORY_WRITE_MDR || type == MicroOperationType.MEM_READ || type.name().startsWith("STRING_"); }
    private static boolean isMemoryWrite(MicroOperationType type) { return type == MicroOperationType.MEMORY_WRITE_MDR || type == MicroOperationType.STRING_MOVS || type == MicroOperationType.STRING_STOS; }

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
        cycleTrace.clear();
        biu.reset();
        stallCycles = queueFlushes = bytesFetched = bytesConsumed = maxQueueOccupancy = totalOverlapCycles = busActiveCycles = biuFetchEvents = 0;
        program        = new ArrayList<>();
        machineCodeProgram = false;
        machineCode = new byte[0];
        machineInstructionOffsets = new ArrayList<>();
        controlUnit.setCurrentPhase("IDLE");
    }

    // =========================================================================
    //  Lazy micro-op priming
    // =========================================================================

    /**
     * Debugger-only hook: after externally restoring registers/memory/flags
     * to a checkpoint (see {@code debugger.DebugSession}), re-derive the
     * pending micro-op batch for the (now-restored) PC and clear any halt
     * latched after that point. This calls the exact same
     * {@link #primeNextInstruction()} every ordinary instruction boundary
     * already uses — it is not a second execution path.
     */
    public void resyncAfterExternalRestore() {
        halted = false;
        primeNextInstruction();
    }

    /**
     * Debugger-only hook: restore the timing/BIU instrumentation counters
     * captured in an earlier {@code debugger.ExecutionSnapshot}. These are
     * plain metrics with no existing public mutator and no influence on
     * architectural execution; this exists purely so a restored checkpoint
     * is faithful in the debugger's displayed/verified state too.
     */
    public void restoreInstrumentationCounters(int stallCycles, int queueFlushes, int bytesFetched, int bytesConsumed,
                                               int maxQueueOccupancy, int totalOverlapCycles, int busActiveCycles,
                                               int biuFetchEvents, long totalCyclesRun) {
        this.stallCycles = stallCycles;
        this.queueFlushes = queueFlushes;
        this.bytesFetched = bytesFetched;
        this.bytesConsumed = bytesConsumed;
        this.maxQueueOccupancy = maxQueueOccupancy;
        this.totalOverlapCycles = totalOverlapCycles;
        this.busActiveCycles = busActiveCycles;
        this.biuFetchEvents = biuFetchEvents;
        this.totalCyclesRun = totalCyclesRun;
    }

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

    private int fetchLength() { return machineCodeProgram ? machineCode.length : program.size(); }

    private int instructionByteLength(int instructionIndex) {
        if (!machineCodeProgram) return 1;
        if (instructionIndex < 0 || instructionIndex >= machineInstructionOffsets.size()) return 0;
        int start = machineInstructionOffsets.get(instructionIndex);
        int end = instructionIndex + 1 < machineInstructionOffsets.size()
            ? machineInstructionOffsets.get(instructionIndex + 1) : machineCode.length;
        return end - start;
    }

    private int fetchOffsetForInstruction(int instructionIndex) {
        if (!machineCodeProgram) return instructionIndex;
        if (instructionIndex < 0 || instructionIndex >= machineInstructionOffsets.size()) return machineCode.length;
        return machineInstructionOffsets.get(instructionIndex);
    }

    /** Converts decoded byte-relative targets to the existing ControlUnit's instruction-index convention. */
    private static List<Instruction> translateMachineControlTargets(List<Instruction> decoded, List<Integer> offsets) {
        List<Instruction> translated = new ArrayList<>(decoded.size());
        for (Instruction instruction : decoded) {
            if (!usesMachineRelativeTarget(instruction.getOpcode())) {
                translated.add(instruction);
                continue;
            }
            int targetOffset = instruction.getAddress();
            int targetIndex = offsets.indexOf(targetOffset);
            if (targetIndex < 0) {
                throw new IllegalArgumentException("machine-code control transfer targets non-instruction offset " + targetOffset);
            }
            translated.add(copyWithAddress(instruction, targetIndex));
        }
        return translated;
    }

    private static boolean usesMachineRelativeTarget(instruction.Opcode opcode) {
        return opcode == instruction.Opcode.JMP || opcode == instruction.Opcode.CALL
            || opcode == instruction.Opcode.JZ_JE || opcode == instruction.Opcode.JNZ_JNE
            || opcode == instruction.Opcode.JC_JB || opcode == instruction.Opcode.JNC_JNB
            || opcode == instruction.Opcode.JO || opcode == instruction.Opcode.JNO
            || opcode == instruction.Opcode.JS || opcode == instruction.Opcode.JNS
            || opcode == instruction.Opcode.JP_JPE || opcode == instruction.Opcode.JNP_JPO
            || opcode == instruction.Opcode.JL_JNGE || opcode == instruction.Opcode.JNL_JGE
            || opcode == instruction.Opcode.JLE_JNG || opcode == instruction.Opcode.JNLE_JG
            || opcode == instruction.Opcode.JBE_JNA || opcode == instruction.Opcode.JNBE_JA
            || opcode == instruction.Opcode.LOOP || opcode == instruction.Opcode.LOOPZ
            || opcode == instruction.Opcode.LOOPNZ || opcode == instruction.Opcode.JCXZ;
    }

    private static Instruction copyWithAddress(Instruction instruction, int address) {
        return new Instruction.Builder(instruction.getOpcode())
            .format(instruction.getFormat()).dest(instruction.getDestReg()).src(instruction.getSrcReg())
            .imm(instruction.getImmediate()).addr(address).raw(instruction.getRawText())
            .baseReg(instruction.getBaseReg()).indexReg(instruction.getIndexReg()).disp(instruction.getDisplacement())
            .segOverride(instruction.getSegmentOverride()).encoded(instruction.getEncoded()).prefix(instruction.getPrefix()).build();
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
    public boolean               isMachineCodeProgram()      { return machineCodeProgram; }
    public byte[]                getMachineCode()            { return java.util.Arrays.copyOf(machineCode, machineCode.length); }
    public List<MicroOperation>  getExecutedTrace()          { return executedTrace; }
    public int                   getBatchIndex()             { return batchIndex; }
    public boolean               isHalted()                  { return halted; }
    public long                  getTotalCyclesRun()         { return totalCyclesRun; }
    public TimingModel           getTimingModel()             { return timingModel; }
    public void                  setTimingModel(TimingModel model) { timingModel = model == null ? TimingModel.FUNCTIONAL : model; }
    public List<CycleSnapshot>   getCycleTrace()              { return List.copyOf(cycleTrace); }
    public CycleSnapshot         getLastCycleSnapshot()       { return cycleTrace.isEmpty() ? null : cycleTrace.get(cycleTrace.size() - 1); }

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

    private boolean requiresFlush(instruction.Opcode op) {
        if (op == null) return false;
        return op == instruction.Opcode.JMP
            || op == instruction.Opcode.CALL
            || op == instruction.Opcode.RET || op == instruction.Opcode.RETF
            || op == instruction.Opcode.JZ_JE || op == instruction.Opcode.JNZ_JNE
            || op == instruction.Opcode.JC_JB || op == instruction.Opcode.JNC_JNB
            || op == instruction.Opcode.JO || op == instruction.Opcode.JNO
            || op == instruction.Opcode.JS || op == instruction.Opcode.JNS
            || op == instruction.Opcode.JP_JPE || op == instruction.Opcode.JNP_JPO
            || op == instruction.Opcode.JL_JNGE || op == instruction.Opcode.JNL_JGE
            || op == instruction.Opcode.JLE_JNG || op == instruction.Opcode.JNLE_JG
            || op == instruction.Opcode.JB_JNAE || op == instruction.Opcode.JBE_JNA
            || op == instruction.Opcode.JNBE_JA
            || op == instruction.Opcode.LOOP || op == instruction.Opcode.LOOPZ
            || op == instruction.Opcode.LOOPNZ || op == instruction.Opcode.JCXZ
            || op == instruction.Opcode.INT || op == instruction.Opcode.INTO
            || op == instruction.Opcode.IRET;
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

    // ---- Microarchitecture instrumentation accessors ---------------------------
    public int getStallCycles() { return stallCycles; }
    public int getQueueFlushes() { return queueFlushes; }
    public int getBytesFetched() { return bytesFetched; }
    public int getBytesConsumed() { return bytesConsumed; }
    public int getMaxQueueOccupancy() { return maxQueueOccupancy; }
    public int getTotalOverlapCycles() { return totalOverlapCycles; }
    public int getBusActiveCycles() { return busActiveCycles; }
    public int getBiuFetchEvents() { return biuFetchEvents; }
    public cpu.biu.BusInterfaceUnit getBiu() { return biu; }
    public cpu.eu.ExecutionUnit getEu() { return eu; }
}

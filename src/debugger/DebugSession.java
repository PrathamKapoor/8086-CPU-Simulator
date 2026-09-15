package debugger;

import cpu.CPU;
import cpu.biu.BusState;
import cpu.biu.FetchState;
import cpu.biu.PrefetchQueue;
import cpu.microarchitecture.MicroarchitectureEventType;
import cpu.registers.FLAGS;
import instruction.Instruction;
import instruction.Opcode;
import machinecode.DecodedInstruction;
import machinecode.Intel8086Decoder;
import memory.MemoryAccessListener;
import microoperation.MicroOperation;
import simulator.profiler.TimingModel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A first-class observability/control layer around an existing {@link CPU}.
 * This does not duplicate instruction semantics: every architectural effect
 * still happens inside {@code ControlUnit}/{@code MicroOperationExecutor}
 * via {@link CPU#step()}. DebugSession only decides *when* to call that,
 * and observes what happened through the CPU's existing public API plus
 * the two small hooks documented in
 * docs/verification/phase-5-debugger-design-note.md.
 */
public final class DebugSession implements MemoryAccessListener {
    private static final String[] TRACKED_REGISTERS = {
        "AX", "BX", "CX", "DX", "SP", "BP", "SI", "DI", "CS", "DS", "SS", "ES", "IP", "IR", "MAR", "MDR"
    };
    private static final String[] FLAG_NAMES = { "CF", "PF", "AF", "ZF", "SF", "TF", "IF", "DF", "OF" };
    private static final int[] FLAG_BITS = {
        FLAGS.CF_BIT, FLAGS.PF_BIT, FLAGS.AF_BIT,
        FLAGS.ZF_BIT, FLAGS.SF_BIT, FLAGS.TF_BIT,
        FLAGS.IF_BIT, FLAGS.DF_BIT, FLAGS.OF_BIT
    };
    public static final long DEFAULT_EXECUTION_LIMIT = 5_000_000L;
    public static final int DEFAULT_AUTO_HISTORY_CAPACITY = 10_000;

    private final CPU cpu;
    private final MemoryJournal journal;
    private final List<Integer> machineInstructionOffsets; // empty for source-token programs

    private final Map<Integer, Breakpoint> breakpoints = new LinkedHashMap<>();
    private final Map<Integer, Watchpoint> watchpoints = new LinkedHashMap<>();
    private int nextBreakpointId = 1;
    private int nextWatchpointId = 1;
    /**
     * Pairs a captured snapshot with a purely-internal creation-order marker.
     * This must be independent of {@code ExecutionSnapshot} itself (and of
     * memory-journal position): two checkpoints taken back-to-back with no
     * memory writes in between share the same journal position, but the
     * later one still must be treated as "after" the earlier one once we
     * restore past it — a plain journal-position comparison can't tell them
     * apart. Restoring an earlier checkpoint prunes every later one to
     * reflect the undo/redo-style divergence documented in the design note.
     */
    private record CheckpointEntry(long sequence, ExecutionSnapshot snapshot) { }

    private int nextCheckpointId = 1;
    private long snapshotSequence = 0;
    private final Map<Integer, CheckpointEntry> namedCheckpoints = new LinkedHashMap<>();

    private final int autoHistoryCapacity;
    private final Deque<CheckpointEntry> autoHistory = new ArrayDeque<>();

    private boolean tracingEnabled = false;
    private final List<TraceEvent> trace = new ArrayList<>();
    private long traceSequence = 0;

    /**
     * The instruction index whose micro-ops are currently executing. IP
     * itself increments during the FETCH micro-op — the 3rd of every
     * instruction's batch, well before its EXECUTE-phase micro-ops run — so
     * live IP reads from inside {@link #onWrite}/{@link #onRead} (which fire
     * synchronously during EXECUTE-phase micro-ops) would misattribute the
     * access to the *next* instruction. This field is set once per
     * {@link #stepMicroOp()} call, before {@code cpu.step()} runs.
     */
    private int currentExecutingInstructionIndex = -1;

    private long microOpCounter = 0;
    private long instructionsRetired = 0;
    private StopReason lastStopReason = StopReason.NOT_STARTED;
    private Integer lastBreakpointId = null;
    private WatchHit lastWatchHit = null;
    private List<WatchHit> pendingWatchHits = new ArrayList<>();
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);

    private DebugSession(CPU cpu, List<Integer> machineInstructionOffsets) {
        this.cpu = cpu;
        this.machineInstructionOffsets = machineInstructionOffsets;
        this.journal = new MemoryJournal(cpu.getMemory());
        // Attach AFTER program load: program bytes are the fixed baseline, not undoable "runtime" writes.
        cpu.getMemory().setAccessListener(this);
        this.autoHistoryCapacity = DEFAULT_AUTO_HISTORY_CAPACITY;
    }

    // FUNCTIONAL is deliberate, not the default-by-omission: stepTimed() (SIMPLIFIED_8086/EXPERIMENTAL)
    // can return null from CPU.step() without advancing batchIndex — a BIU stall cycle with no
    // micro-op executed — which breaks the "one CPU.step() call == one micro-op" boundary detection
    // this whole session is built on. stepFunctional() always executes exactly one micro-op per call.
    public static DebugSession forSourceProgram(List<Instruction> program) {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.FUNCTIONAL);
        cpu.loadProgram(program);
        return new DebugSession(cpu, List.of());
    }

    public static DebugSession forMachineCode(byte[] bytes) {
        CPU cpu = new CPU();
        cpu.setTimingModel(TimingModel.FUNCTIONAL);
        cpu.loadMachineCode(bytes);
        List<Integer> offsets = new ArrayList<>();
        int offset = 0;
        Intel8086Decoder decoder = new Intel8086Decoder();
        while (offset < bytes.length) {
            DecodedInstruction next = decoder.decode(bytes, offset);
            offsets.add(offset);
            offset = next.nextOffset();
        }
        return new DebugSession(cpu, offsets);
    }

    public CPU cpu() { return cpu; }

    // =========================================================================
    //  Execution control
    // =========================================================================

    /** One micro-operation — the simulator's true atomic step (see design note). */
    public StopReason stepMicroOp() { return stepMicroOp(true); }

    /**
     * @param suppressBreakpointHere if true, a breakpoint sitting exactly at
     * the instruction boundary this call starts from is ignored (this call
     * still executes). Every directed step (this method's public overload,
     * stepInstruction/stepOver/stepOut's first inner step) suppresses so
     * that "step" from a breakpoint you are already stopped at actually
     * moves forward, matching standard debugger behavior; run()/continue()
     * suppress only their own first step for the same reason, then check
     * normally as execution proceeds — including back around a loop to the
     * same position.
     */
    private StopReason stepMicroOp(boolean suppressBreakpointHere) {
        if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
        pendingWatchHits = new ArrayList<>();

        int preBatchIndex = cpu.getBatchIndex();
        boolean isInstructionStart = preBatchIndex == 0;
        // IMPORTANT: only re-derive the executing instruction's index AT a boundary.
        // IP itself increments during the FETCH micro-op (3rd of the instruction's
        // batch), well before EXECUTE-phase micro-ops run, so re-reading live IP on
        // every stepMicroOp() call (rather than holding it fixed for the whole
        // instruction) would mislabel that instruction's own later micro-ops as
        // belonging to "the next instruction" the moment FETCH completes.
        if (isInstructionStart) currentExecutingInstructionIndex = cpu.getRegister("IP").output();
        int preIndex = currentExecutingInstructionIndex;

        boolean checkRegWatch = hasNonMemoryWatch();
        boolean captureRegState = checkRegWatch || tracingEnabled;
        Map<String, Integer> regsBefore = captureRegState ? snapshotTrackedRegisters() : null;
        int flagsBefore = captureRegState ? cpu.getFlags().output() : 0;

        if (isInstructionStart) {
            ExecutionPosition pos = positionFor(preIndex, true);
            Breakpoint hit = suppressBreakpointHere ? null : matchingBreakpoint(pos);
            if (hit != null) {
                hit.recordHit();
                lastBreakpointId = hit.id();
                if (tracingEnabled) trace.add(new TraceEvent.BreakpointHitEvent(nextSeq(), cpu.getTotalCyclesRun(), hit.id(), pos));
                return lastStopReason = StopReason.BREAKPOINT;
            }
            recordAutoHistory();
            if (tracingEnabled) {
                trace.add(new TraceEvent.InstructionStart(nextSeq(), cpu.getTotalCyclesRun(), preIndex,
                    machineOffsetFor(preIndex), instructionTextAt(preIndex)));
            }
        }

        MicroOperation op;
        try {
            op = cpu.step();
        } catch (ArithmeticException e) {
            // e.g. DIV/IDIV by zero or quotient overflow raised from inside a micro-op's action.
            return lastStopReason = StopReason.EXCEPTION_TRAP;
        } catch (RuntimeException e) {
            // A malformed/unsupported instruction reaching ControlUnit (e.g. an
            // unknown register name or an operand format it has no case for).
            return lastStopReason = StopReason.INVALID_INSTRUCTION;
        }
        microOpCounter++;
        if (op != null && tracingEnabled) {
            trace.add(new TraceEvent.MicroOpExecuted(nextSeq(), cpu.getTotalCyclesRun(), preIndex,
                op.getType().name(), op.getRtlDescription(), op.getDestReg(), op.getSrcReg()));
            recordBiuFetchEvents();
        }

        if (captureRegState) processRegisterAndFlagChanges(regsBefore, flagsBefore, checkRegWatch);

        boolean retiredNow = cpu.getBatchIndex() == 0 || cpu.isHalted();
        if (retiredNow) {
            instructionsRetired++;
            if (tracingEnabled) {
                trace.add(new TraceEvent.InstructionRetired(nextSeq(), cpu.getTotalCyclesRun(), preIndex, instructionTextAt(preIndex)));
            }
            if (!cpu.isHalted()) {
                int actualNext = cpu.getRegister("IP").output();
                if (actualNext != preIndex + 1 && tracingEnabled) {
                    Instruction retired = instructionAt(preIndex);
                    trace.add(new TraceEvent.ControlTransfer(nextSeq(), cpu.getTotalCyclesRun(), preIndex, actualNext,
                        retired == null ? "?" : retired.getOpcode().name()));
                }
            }
        }

        if (!pendingWatchHits.isEmpty()) {
            lastWatchHit = pendingWatchHits.get(0);
            return lastStopReason = StopReason.WATCHPOINT;
        }
        if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
        return lastStopReason = StopReason.SINGLE_STEP;
    }

    /** All micro-ops of exactly one instruction (see design note for the boundary definition). */
    public StopReason stepInstruction() { return stepInstruction(true); }

    /** @param suppressBreakpointHere see {@link #stepMicroOp(boolean)} — applies only to this call's first micro-op. */
    private StopReason stepInstruction(boolean suppressBreakpointHere) {
        if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
        boolean first = true;
        do {
            StopReason r = stepMicroOp(first && suppressBreakpointHere);
            first = false;
            if (r == StopReason.BREAKPOINT || r == StopReason.WATCHPOINT || r == StopReason.TERMINATION) return r;
        } while (cpu.getBatchIndex() != 0 && !cpu.isHalted());
        return lastStopReason = cpu.isHalted() ? StopReason.TERMINATION : StopReason.SINGLE_STEP;
    }

    /**
     * Step over a CALL as one logical step (runs the callee to completion);
     * degrades to {@link #stepInstruction()} for any other instruction —
     * this is not faked, it is exactly what "step over" means when the
     * current instruction is not a call. A breakpoint reached *inside* the
     * callee still stops (only the CALL instruction itself, which we are
     * already standing on, is suppressed).
     */
    public StopReason stepOver() {
        if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
        Instruction current = cpu.getCurrentInstruction();
        if (current == null || current.getOpcode() != Opcode.CALL) return stepInstruction();

        int callIndex = cpu.getRegister("IP").output();
        int returnTarget = callIndex + 1;
        int spAtCall = cpu.getRegister("SP").output();
        StopReason r = stepInstruction(true);
        if (r != StopReason.SINGLE_STEP) return r;
        while (true) {
            if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
            if (cpu.getRegister("IP").output() == returnTarget && cpu.getRegister("SP").output() == spAtCall) {
                return lastStopReason = StopReason.SINGLE_STEP;
            }
            StopReason inner = stepInstruction(false);
            if (inner != StopReason.SINGLE_STEP) return inner;
        }
    }

    /**
     * Run until the current subroutine returns (the first RET/RETF retired
     * at a stack depth shallower than when stepOut() was called). This is a
     * documented heuristic appropriate to this simulator's CALL/RET model,
     * not a claim of handling arbitrary stack manipulation. A breakpoint
     * reached before the return still stops (only the instruction already
     * standing at when stepOut() was called is suppressed).
     */
    public StopReason stepOut() {
        if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
        int spAtEntry = cpu.getRegister("SP").output();
        boolean first = true;
        while (true) {
            Instruction before = cpu.getCurrentInstruction();
            StopReason r = stepInstruction(first);
            first = false;
            if (r != StopReason.SINGLE_STEP) return r;
            if (cpu.isHalted()) return lastStopReason = StopReason.TERMINATION;
            boolean wasReturn = before != null && (before.getOpcode() == Opcode.RET || before.getOpcode() == Opcode.RETF);
            if (wasReturn && cpu.getRegister("SP").output() > spAtEntry) return lastStopReason = StopReason.SINGLE_STEP;
        }
    }

    /**
     * Run until a breakpoint, watchpoint, termination, user pause, or the
     * micro-op limit. Like a real debugger's "continue", a breakpoint at the
     * position execution is already stopped at does not immediately re-fire
     * — only this call's first step suppresses it; every position reached
     * afterwards (including looping back to the same one) is checked
     * normally.
     */
    public StopReason run() { return run(DEFAULT_EXECUTION_LIMIT); }

    public StopReason run(long microOpLimit) {
        pauseRequested.set(false);
        long start = microOpCounter;
        // Only suppress the very first check if we are actually resuming from a
        // breakpoint stop at this exact position; a fresh/reset session (or one
        // that stopped for any other reason) must still honor a breakpoint sitting
        // at its starting instruction -- see breakpointAtFirstInstruction.
        boolean first = lastStopReason == StopReason.BREAKPOINT;
        while (true) {
            if (pauseRequested.get()) return lastStopReason = StopReason.USER_PAUSE;
            if (microOpCounter - start >= microOpLimit) return lastStopReason = StopReason.EXECUTION_LIMIT;
            StopReason r = stepMicroOp(first);
            first = false;
            if (r != StopReason.SINGLE_STEP) return r;
        }
    }

    /** Alias for {@link #run()} — in this single-session model, resuming after a stop is the same loop. */
    public StopReason continueExecution() { return run(); }

    /** Cooperative pause: takes effect the next time the run loop checks it. Safe to call from another thread. */
    public void requestPause() { pauseRequested.set(true); }

    public void reset() {
        cpu.reset();
        breakpoints.clear();
        watchpoints.clear();
        namedCheckpoints.clear();
        autoHistory.clear();
        trace.clear();
        traceSequence = 0;
        microOpCounter = 0;
        instructionsRetired = 0;
        lastStopReason = StopReason.NOT_STARTED;
        lastBreakpointId = null;
        lastWatchHit = null;
    }

    // =========================================================================
    //  Breakpoints
    // =========================================================================

    public int addInstructionBreakpoint(int instructionIndex, String condition) {
        int id = nextBreakpointId++;
        breakpoints.put(id, new Breakpoint(id, Breakpoint.Kind.INSTRUCTION_INDEX, instructionIndex,
            condition == null || condition.isBlank() ? null : new BreakpointCondition(condition)));
        return id;
    }

    public int addMachineOffsetBreakpoint(int byteOffset, String condition) {
        int id = nextBreakpointId++;
        breakpoints.put(id, new Breakpoint(id, Breakpoint.Kind.MACHINE_OFFSET, byteOffset,
            condition == null || condition.isBlank() ? null : new BreakpointCondition(condition)));
        return id;
    }

    public boolean removeBreakpoint(int id) { return breakpoints.remove(id) != null; }
    public List<Breakpoint> breakpoints() { return List.copyOf(breakpoints.values()); }

    private Breakpoint matchingBreakpoint(ExecutionPosition pos) {
        for (Breakpoint bp : breakpoints.values()) {
            if (bp.enabled() && bp.matchesLocation(pos) && (bp.condition() == null || bp.condition().evaluate(cpu))) {
                return bp;
            }
        }
        return null;
    }

    // =========================================================================
    //  Watchpoints
    // =========================================================================

    public int addMemoryWatch(int address, Watchpoint.Access access) { return addMemoryRangeWatch(address, address, access); }

    public int addMemoryRangeWatch(int addressLow, int addressHigh, Watchpoint.Access access) {
        int id = nextWatchpointId++;
        watchpoints.put(id, Watchpoint.memory(id, addressLow, addressHigh, access));
        return id;
    }

    public int addRegisterWatch(String register) {
        int id = nextWatchpointId++;
        watchpoints.put(id, Watchpoint.register(id, register));
        return id;
    }

    public int addFlagWatch(String flag) {
        int id = nextWatchpointId++;
        watchpoints.put(id, Watchpoint.flag(id, flag));
        return id;
    }

    public boolean removeWatchpoint(int id) { return watchpoints.remove(id) != null; }
    public List<Watchpoint> watchpoints() { return List.copyOf(watchpoints.values()); }

    private boolean hasNonMemoryWatch() {
        for (Watchpoint w : watchpoints.values()) if (w.enabled() && w.kind() != Watchpoint.Kind.MEMORY) return true;
        return false;
    }

    private Map<String, Integer> snapshotTrackedRegisters() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String name : TRACKED_REGISTERS) values.put(name, cpu.getRegister(name).output());
        return values;
    }

    /**
     * One before/after diff of the small tracked-register set + FLAGS,
     * shared by trace recording (RegisterMutation/FlagMutation events, when
     * tracing) and register/flag watchpoint evaluation (when any exist).
     * Polling ~16 fixed registers is O(1) and unrelated to the "never poll
     * the 1 MB memory" rule, which is specifically about Memory.
     */
    private void processRegisterAndFlagChanges(Map<String, Integer> before, int flagsBefore, boolean checkWatches) {
        int flagsAfter = cpu.getFlags().output();
        ExecutionPosition pos = positionFor(currentExecutingInstructionIndex, false);
        String instrText = instructionTextAt(pos.instructionIndex());

        for (String name : TRACKED_REGISTERS) {
            int oldValue = before.get(name);
            int newValue = cpu.getRegister(name).output();
            if (oldValue == newValue) continue;
            if (tracingEnabled) {
                trace.add(new TraceEvent.RegisterMutation(nextSeq(), cpu.getTotalCyclesRun(), currentExecutingInstructionIndex,
                    name, oldValue, newValue));
            }
            if (checkWatches) {
                for (Watchpoint w : watchpoints.values()) {
                    if (w.enabled() && w.kind() == Watchpoint.Kind.REGISTER && w.name().equals(name)) {
                        fireWatch(w, Watchpoint.Access.WRITE, -1, name, oldValue, newValue, pos, instrText);
                    }
                }
            }
        }

        if (flagsBefore != flagsAfter) {
            for (int i = 0; i < FLAG_NAMES.length; i++) {
                boolean oldValue = ((flagsBefore >> FLAG_BITS[i]) & 1) == 1;
                boolean newValue = ((flagsAfter >> FLAG_BITS[i]) & 1) == 1;
                if (oldValue == newValue) continue;
                if (tracingEnabled) {
                    trace.add(new TraceEvent.FlagMutation(nextSeq(), cpu.getTotalCyclesRun(), currentExecutingInstructionIndex,
                        FLAG_NAMES[i], oldValue, newValue));
                }
                if (checkWatches) {
                    for (Watchpoint w : watchpoints.values()) {
                        if (w.enabled() && w.kind() == Watchpoint.Kind.FLAG && w.name().equals(FLAG_NAMES[i])) {
                            fireWatch(w, Watchpoint.Access.WRITE, -1, FLAG_NAMES[i], oldValue ? 1 : 0, newValue ? 1 : 0, pos, instrText);
                        }
                    }
                }
            }
        }
    }


    @Override
    public void onWrite(int address, boolean hadPriorValue, int priorValue, int newValue) {
        journal.recordWrite(address, hadPriorValue, priorValue, newValue);
        if (tracingEnabled) {
            trace.add(new TraceEvent.MemoryWriteEvent(nextSeq(), cpu.getTotalCyclesRun(), currentExecutingInstructionIndex,
                address, hadPriorValue, priorValue, newValue));
        }
        if (priorValue == newValue || watchpoints.isEmpty()) return;
        ExecutionPosition pos = positionFor(currentExecutingInstructionIndex, false);
        String instrText = instructionTextAt(currentExecutingInstructionIndex);
        for (Watchpoint w : watchpoints.values()) {
            if (w.enabled() && w.kind() == Watchpoint.Kind.MEMORY && w.coversAddress(address)
                && (w.access() == Watchpoint.Access.WRITE || w.access() == Watchpoint.Access.READ_WRITE)) {
                fireWatch(w, Watchpoint.Access.WRITE, address, null, priorValue, newValue, pos, instrText);
            }
        }
    }

    @Override
    public void onRead(int address, int value) {
        if (tracingEnabled) {
            trace.add(new TraceEvent.MemoryReadEvent(nextSeq(), cpu.getTotalCyclesRun(), currentExecutingInstructionIndex, address, value));
        }
        if (watchpoints.isEmpty()) return;
        ExecutionPosition pos = positionFor(currentExecutingInstructionIndex, false);
        String instrText = instructionTextAt(currentExecutingInstructionIndex);
        for (Watchpoint w : watchpoints.values()) {
            if (w.enabled() && w.kind() == Watchpoint.Kind.MEMORY && w.coversAddress(address)
                && (w.access() == Watchpoint.Access.READ || w.access() == Watchpoint.Access.READ_WRITE)) {
                fireWatch(w, Watchpoint.Access.READ, address, null, value, value, pos, instrText);
            }
        }
    }

    private void fireWatch(Watchpoint w, Watchpoint.Access access, int address, String name, int oldValue, int newValue,
                           ExecutionPosition pos, String instrText) {
        w.recordHit();
        WatchHit hit = new WatchHit(w.id(), w.kind(), access, address, name, oldValue, newValue, pos, instrText);
        pendingWatchHits.add(hit);
        if (tracingEnabled) trace.add(new TraceEvent.WatchpointHitEvent(nextSeq(), cpu.getTotalCyclesRun(), hit));
    }

    // =========================================================================
    //  Trace
    // =========================================================================

    public void setTracing(boolean enabled) { this.tracingEnabled = enabled; }
    public boolean isTracing() { return tracingEnabled; }
    public List<TraceEvent> trace() { return List.copyOf(trace); }
    public void clearTrace() { trace.clear(); traceSequence = 0; }

    private long nextSeq() { return traceSequence++; }

    private void recordBiuFetchEvents() {
        var snapshot = cpu.getLastCycleSnapshot();
        if (snapshot == null) return;
        for (var event : snapshot.events()) {
            if (event.type() == MicroarchitectureEventType.FETCH_BYTE) {
                trace.add(new TraceEvent.BiuFetchEvent(nextSeq(), event.cycle(), event.address(), event.value(), snapshot.queueOccupancy()));
            }
        }
    }

    // =========================================================================
    //  "Why did this change?"
    // =========================================================================

    public InstructionExplanation explainLastRetiredInstruction() {
        for (int i = trace.size() - 1; i >= 0; i--) {
            if (trace.get(i) instanceof TraceEvent.InstructionRetired retired) {
                int start = i;
                while (start > 0 && !(trace.get(start) instanceof TraceEvent.InstructionStart)) start--;
                return buildExplanation(trace.subList(start, i + 1), retired.instructionIndex());
            }
        }
        return null;
    }

    private InstructionExplanation buildExplanation(List<TraceEvent> window, int instructionIndex) {
        List<String> microOps = new ArrayList<>();
        List<String> read = new ArrayList<>();
        List<String> written = new ArrayList<>();
        List<Integer> memRead = new ArrayList<>();
        List<Integer> memWritten = new ArrayList<>();
        List<String> flagsWritten = new ArrayList<>();
        boolean controlFlowChanged = false;
        int controlFlowTarget = -1;
        long cycleStart = -1, cycleEnd = -1;

        for (TraceEvent e : window) {
            if (cycleStart < 0) cycleStart = e.cycle();
            cycleEnd = e.cycle();
            if (e instanceof TraceEvent.MicroOpExecuted m) {
                microOps.add(m.rtlDescription());
                if (m.srcReg() != null && !read.contains(m.srcReg())) read.add(m.srcReg());
                if (m.destReg() != null && !written.contains(m.destReg())) written.add(m.destReg());
            } else if (e instanceof TraceEvent.MemoryWriteEvent w) {
                memWritten.add(w.address());
            } else if (e instanceof TraceEvent.MemoryReadEvent r) {
                memRead.add(r.address());
            } else if (e instanceof TraceEvent.ControlTransfer c) {
                controlFlowChanged = true;
                controlFlowTarget = c.toInstructionIndex();
            } else if (e instanceof TraceEvent.FlagMutation f) {
                if (!flagsWritten.contains(f.flag())) flagsWritten.add(f.flag());
            }
        }
        Instruction instr = instructionAt(instructionIndex);
        String bytesHex = instr != null && instr.getEncoded() != null ? toHex(instr.getEncoded()) : "";
        return new InstructionExplanation(instructionIndex, instructionTextAt(instructionIndex), bytesHex,
            List.copyOf(microOps), List.copyOf(read), List.copyOf(written), List.copyOf(memRead), List.copyOf(memWritten),
            List.copyOf(flagsWritten), controlFlowChanged, controlFlowTarget, cycleStart < 0 ? 0 : cycleStart, cycleEnd < 0 ? 0 : cycleEnd);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    // =========================================================================
    //  Snapshots / checkpoints / time travel
    // =========================================================================

    /** Checkpoints can only be taken at an instruction boundary (see design note). */
    public int checkpoint() {
        requireInstructionBoundary();
        int id = nextCheckpointId++;
        namedCheckpoints.put(id, new CheckpointEntry(snapshotSequence++, captureSnapshot()));
        return id;
    }

    public boolean restore(int checkpointId) {
        CheckpointEntry entry = namedCheckpoints.get(checkpointId);
        if (entry == null) return false;
        applySnapshot(entry.snapshot());
        pruneStaleHistory(entry.sequence());
        return true;
    }

    /** Rewind to the state recorded {@code count} instruction-boundaries ago (bounded by history capacity). */
    public boolean rewindInstructions(int count) {
        if (count <= 0 || count > autoHistory.size()) return false;
        CheckpointEntry target = null;
        var it = autoHistory.descendingIterator();
        for (int i = 0; i < count && it.hasNext(); i++) target = it.next();
        if (target == null) return false;
        applySnapshot(target.snapshot());
        for (int i = 0; i < count; i++) autoHistory.pollLast();
        pruneStaleHistory(target.sequence());
        return true;
    }

    public ExecutionSnapshot snapshotNow() {
        requireInstructionBoundary();
        return captureSnapshot();
    }

    private void requireInstructionBoundary() {
        if (cpu.getBatchIndex() != 0 && !cpu.isHalted()) {
            throw new IllegalStateException("Checkpoints can only be taken at an instruction boundary; "
                + "call stepInstruction() to finish the current instruction first.");
        }
    }

    private void recordAutoHistory() {
        if (autoHistoryCapacity <= 0) return;
        if (autoHistory.size() >= autoHistoryCapacity) autoHistory.pollFirst();
        autoHistory.addLast(new CheckpointEntry(snapshotSequence++, captureSnapshot()));
    }

    private void pruneStaleHistory(long sequenceFloor) {
        autoHistory.removeIf(e -> e.sequence() > sequenceFloor);
        namedCheckpoints.values().removeIf(e -> e.sequence() > sequenceFloor);
    }

    private ExecutionSnapshot captureSnapshot() {
        Map<String, Integer> regs = new LinkedHashMap<>();
        for (String name : TRACKED_REGISTERS) regs.put(name, cpu.getRegister(name).output());
        PrefetchQueue queue = cpu.getBiu().getPrefetchQueue();
        List<Integer> queueContents = new ArrayList<>();
        for (int b : queue.getContents()) queueContents.add(b);
        FetchState fs = cpu.getBiu().getFetchState();
        BusState bs = cpu.getBiu().getBusState();
        return new ExecutionSnapshot(
            regs, cpu.getFlags().output(), cpu.isHalted(),
            cpu.getTotalCyclesRun(), microOpCounter, instructionsRetired,
            cpu.getStallCycles(), cpu.getQueueFlushes(), cpu.getBytesFetched(), cpu.getBytesConsumed(),
            cpu.getMaxQueueOccupancy(), cpu.getTotalOverlapCycles(), cpu.getBusActiveCycles(), cpu.getBiuFetchEvents(),
            queueContents, fs.getFetchPhysicalAddress(), fs.getNextFetchOffset(),
            bs.isAddressBusActive(), bs.isDataBusActive(), bs.isControlBusActive(), bs.getPhysicalAddress(),
            cpu.getControlUnit().getCurrentPhase(), journal.position()
        );
    }

    private void applySnapshot(ExecutionSnapshot snap) {
        journal.rewindTo(snap.memoryJournalPosition());
        for (Map.Entry<String, Integer> e : snap.registers().entrySet()) cpu.getRegister(e.getKey()).load(e.getValue());
        cpu.getFlags().load(snap.flags());

        PrefetchQueue queue = cpu.getBiu().getPrefetchQueue();
        queue.clear();
        for (int b : snap.prefetchQueueContents()) queue.enqueue(b);
        FetchState fs = cpu.getBiu().getFetchState();
        fs.setFetchPhysicalAddress(snap.fetchPhysicalAddress());
        fs.setNextFetchOffset(snap.nextFetchOffset());
        BusState bs = cpu.getBiu().getBusState();
        bs.setAddressBusActive(snap.addressBusActive());
        bs.setDataBusActive(snap.dataBusActive());
        bs.setControlBusActive(snap.controlBusActive());
        bs.setPhysicalAddress(snap.busPhysicalAddress());
        cpu.getControlUnit().setCurrentPhase(snap.controlUnitPhase());

        cpu.restoreInstrumentationCounters(snap.stallCycles(), snap.queueFlushes(), snap.bytesFetched(), snap.bytesConsumed(),
            snap.maxQueueOccupancy(), snap.totalOverlapCycles(), snap.busActiveCycles(), snap.biuFetchEvents(), snap.totalCyclesRun());
        cpu.resyncAfterExternalRestore();

        microOpCounter = snap.totalMicroOpsExecuted();
        instructionsRetired = snap.totalInstructionsRetired();
    }

    // =========================================================================
    //  Inspection / diff / JSON state
    // =========================================================================

    public ExecutionPosition currentPosition() { return positionFor(cpu.getRegister("IP").output(), cpu.getBatchIndex() == 0); }

    private ExecutionPosition positionFor(int instructionIndex, boolean boundary) {
        return new ExecutionPosition(instructionIndex, machineOffsetFor(instructionIndex), cpu.getBatchIndex(),
            microOpCounter, boundary);
    }

    private int machineOffsetFor(int instructionIndex) {
        if (machineInstructionOffsets.isEmpty() || instructionIndex < 0 || instructionIndex >= machineInstructionOffsets.size()) return -1;
        return machineInstructionOffsets.get(instructionIndex);
    }

    private Instruction instructionAt(int index) {
        List<Instruction> program = cpu.getProgram();
        return index >= 0 && index < program.size() ? program.get(index) : null;
    }

    private String instructionTextAt(int index) {
        Instruction instr = instructionAt(index);
        return instr == null ? "" : instr.toString();
    }

    public StopReason lastStopReason() { return lastStopReason; }
    public Integer lastBreakpointId() { return lastBreakpointId; }
    public WatchHit lastWatchHit() { return lastWatchHit; }
    public long instructionsRetired() { return instructionsRetired; }
    public long microOpsExecuted() { return microOpCounter; }

    public DebugState currentState() {
        int index = cpu.getRegister("IP").output();
        Instruction instr = instructionAt(index);
        String bytesHex = instr != null && instr.getEncoded() != null ? toHex(instr.getEncoded()) : "";
        ExecutionSnapshot snap = cpu.getBatchIndex() == 0 || cpu.isHalted() ? captureSnapshot() : captureSnapshotAllowingMidInstruction();
        return new DebugState(currentPosition(), lastStopReason, cpu.isHalted(), instructionTextAt(index), bytesHex,
            snap, breakpoints(), watchpoints(), lastWatchHit, lastBreakpointId);
    }

    /** Read-only inspection snapshot for display purposes even mid-instruction (never used to restore). */
    private ExecutionSnapshot captureSnapshotAllowingMidInstruction() { return captureSnapshot(); }

    public static StateDiff diff(ExecutionSnapshot before, ExecutionSnapshot after) {
        return StateDiff.of(before, after);
    }

    public StateDiff diffSinceCheckpoint(int checkpointId, ExecutionSnapshot after) {
        CheckpointEntry entry = namedCheckpoints.get(checkpointId);
        if (entry == null) throw new IllegalArgumentException("no such checkpoint: " + checkpointId);
        ExecutionSnapshot before = entry.snapshot();
        Map<Integer, int[]> memoryDelta = journal.netChanges(before.memoryJournalPosition(), after.memoryJournalPosition());
        return StateDiff.of(before, after, memoryDelta);
    }
}

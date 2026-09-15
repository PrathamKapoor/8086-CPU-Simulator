# Phase 5 — Debugger & Time-Travel Observability Layer

This document is the reference for the debugger subsystem added in Phase 5,
on top of the frozen `phase-4-complete` baseline. It covers the execution
model, every capability, its limitations, and where to find the tests that
prove each claim. The forensic audit this design is based on is in
[`phase-5-debugger-design-note.md`](phase-5-debugger-design-note.md) — read
that first for *why* the design looks the way it does; this document is the
*what* and *how to use it*.

## Architectural principle

The debugger is an external observer/controller. It does not duplicate
instruction semantics. Every architectural effect still happens inside
`ControlUnit`/`MicroOperationExecutor` via the existing `CPU.step()`:

```
machine code / semantic instruction → ControlUnit → micro-operations
    → MicroOperationExecutor → CPU architectural state
                                        ↑
                              debugger.DebugSession observes and controls
```

Only two additive changes were made to existing classes, both non-invasive
(see the design note for the full audit):

- `memory.MemoryAccessListener` + a single nullable-listener hook in
  `Memory.busWrite/directWrite/busRead` — watchpoints observe real mutation
  points, never poll the 1&nbsp;MB address space.
- `CPU.resyncAfterExternalRestore()` / `CPU.restoreInstrumentationCounters()`
  — after a debugger-driven register/memory restore, re-derive the pending
  micro-op batch from the restored `IP` via the *same*
  `primeNextInstruction()` every ordinary instruction boundary already
  calls, and restore the timing counters that have getters but no public
  setters.

## Execution model: instruction vs. micro-op

`CPU.step()` executes exactly one `MicroOperation` — the simulator's true
atomic unit. An **instruction** is the span of `step()` calls between two
successive `batchIndex == 0` observations (the point where
`primeNextInstruction()` has just re-derived a fresh micro-op batch for the
current `IP`). This is not a new definition Phase 5 invented — it is exactly
what `ControlUnit.generateMicroOps`/`CPU.primeNextInstruction` already treat
as one instruction; the debugger only observes it via the existing public
`getBatchIndex()`/`getRegister("IP")`/`isHalted()`.

One non-obvious consequence, discovered and fixed while building this: `IP`
increments during the **FETCH** micro-op — the 3rd of every instruction's
batch — well before that instruction's **EXECUTE**-phase micro-ops run. Any
code that wants to know "which instruction is this micro-op/memory-access
part of" must hold the instruction's starting index fixed for the whole
instruction, not re-read live `IP`, or it will misattribute events to "the
next instruction" the moment FETCH completes. `DebugSession` does this via
`currentExecutingInstructionIndex`, set only at a boundary.

## Execution control

| Command | Method | Semantics |
|---|---|---|
| RUN | `run()` / `run(long microOpLimit)` | Run until breakpoint, watchpoint, termination, user pause, or the micro-op limit (default 5,000,000). |
| CONTINUE | `continueExecution()` | Alias for `run()` — in this single-session model, resuming after a stop is the same loop. |
| PAUSE | `requestPause()` | Cooperative: sets a flag the run loop checks every micro-op. Safe to call from another thread (e.g. a GUI "Pause" button while `run()` executes on a background thread); in a synchronous CLI script it only has an effect if called from a separate thread, since a single-threaded script can't interrupt its own blocking call. |
| STEP MICRO-OP | `stepMicroOp()` | One micro-op — the atomic unit. |
| STEP INSTRUCTION | `stepInstruction()` | All micro-ops of exactly one instruction. |
| STEP OVER | `stepOver()` | If the current instruction is `CALL`, runs the callee to completion as one logical step (tracked by return-IP *and* SP depth, to avoid false-positives on recursion); otherwise degrades to `stepInstruction()` — this is not faked, it's what "step over" means for a non-call. |
| STEP OUT | `stepOut()` | Runs until the first `RET`/`RETF` retires at a shallower stack depth than when called. A documented heuristic for this simulator's CALL/RET model, not a claim of handling arbitrary stack manipulation. |
| RESET | `reset()` | Delegates to `CPU.reset()` and clears all debugger-side state (breakpoints, watchpoints, checkpoints, trace). |

### Breakpoint suppression semantics

A breakpoint stops execution *before* the guarded instruction runs. The
subtlety: once stopped there, the *next* command must actually execute that
instruction and move on — not immediately re-report the same breakpoint
forever (this was a real bug caught during CI-script testing; see the
`steppingPastABreakpointActuallyExecutesItInsteadOfReTriggering` and
`continueAfterBreakpointAlsoMovesPastItAndCanReHitOnALoopBack` regression
tests in `BreakpointWatchpointMatrixTest`). The rule, matching standard
debugger UX (gdb/lldb):

- Directed stepping (`stepMicroOp`, `stepInstruction`, `stepOver`, `stepOut`)
  **always** executes its own first step, ignoring a breakpoint exactly at
  the position it starts from — you asked to move forward by one unit, not
  to scan for the next stop.
- `run()`/`continueExecution()` suppress the check for their own first step
  **only when actually resuming from a `BREAKPOINT` stop at that position**.
  A fresh or just-reset session still honors a breakpoint sitting at its
  very first instruction (`run()` from scratch with a breakpoint at entry
  must still stop there).
- `stepOver()`/`stepOut()` suppress only their *own* first step; a
  breakpoint reached later — inside the callee or the remainder of the
  loop being stepped through — still stops normally.

## Breakpoints

Two location kinds (`Breakpoint.Kind`):

- `INSTRUCTION_INDEX` — the simulator's canonical execution identity for
  both source-token and machine-code programs (`ExecutionPosition.instructionIndex`).
- `MACHINE_OFFSET` — a raw byte offset into a machine-code-loaded program
  (`ExecutionPosition.machineByteOffset`, `-1` for source-token programs).

```java
session.addInstructionBreakpoint(5, null);       // unconditional
session.addInstructionBreakpoint(5, "CX == 1");  // conditional
session.addMachineOffsetBreakpoint(0x0A, "AX != 0");
```

### Conditional breakpoints

`BreakpointCondition` is a small, deterministic, side-effect-free expression:
`<TOKEN> <OP> <VALUE>` — one comparison, not a language. `TOKEN` is a
general/segment/`IP` register name or a flag name (`CF PF AF ZF SF TF IF DF
OF`); `OP` is one of `== != < <= > >=`; `VALUE` is decimal or
`0x`/`H`-suffixed hex (`0` / `1` for a flag). Evaluated by reading the CPU's
current state only — never mutates anything.

## Watchpoints

`Watchpoint.Kind`: `MEMORY` (address or inclusive range, `READ`/`WRITE`/
`READ_WRITE`), `REGISTER`, `FLAG`.

- **Memory** watchpoints are wired to the actual `Memory.busWrite`/
  `busRead` mutation points via `MemoryAccessListener` — never a poll over
  the 1&nbsp;MB address space.
- **Register/flag** watchpoints compare a before/after snapshot of the
  fixed, small (~16-entry) register set + FLAGS around each `CPU.step()`
  call. This is O(1) and unrelated to the "never poll memory" rule, which
  is specifically about the address space — polling 16 registers is cheap
  and only happens when a non-memory watch actually exists.

Every hit is a `WatchHit`: address/register name, old/new value, access
type, the instruction responsible, and the `ExecutionPosition` (including
whether it landed exactly on an instruction boundary — a read/write
watchpoint typically fires *mid*-instruction, before that instruction's
result-committing micro-op runs; see `memoryReadWatchpointStopsOnActualRead`
for the precise granularity this gives you).

## Snapshots / checkpoints / time travel

`ExecutionSnapshot` is a plain `record` capturing registers, FLAGS, halted
state, timing/BIU counters, the prefetch queue's contents, `FetchState`, bus
state, `ControlUnit`'s phase, and a `memoryJournalPosition` — **not** a copy
of memory. Two snapshots are `.equals()` exactly when every piece of state
they capture is identical, which is what the determinism tests compare.

### Why memory isn't copied: the undo journal

`MemoryJournal` is an append-only log of `(address, hadPriorValue,
priorValue, newValue)` tuples, one per write, fed by the same `Memory`
listener hook watchpoints use. A checkpoint just records the journal's
current length — O(1). Restoring replays the log **backwards** from the
current length down to the checkpoint's recorded length, undoing each write
directly on the `MemoryCell` (bypassing the listener, so undoing never gets
journaled itself) — O(changes-since-checkpoint), never O(1&nbsp;MB).

**This is an undo/redo-style divergence, not a branching snapshot DAG.**
Restoring to an earlier checkpoint truncates the journal past that point;
any checkpoint or auto-history entry recorded later in that now-discarded
timeline becomes invalid (`restore()`/`rewindInstructions()` return `false`
for it). New writes after a restore start a fresh forward timeline from
there, like undo in an editor. Staleness is tracked by a purely-internal,
monotonically increasing sequence number attached to each checkpoint at
creation time (`DebugSession.CheckpointEntry`) — deliberately independent
of `ExecutionSnapshot`'s public equality (so it never affects determinism
comparisons) and of the memory journal position specifically, because two
checkpoints taken back-to-back with no memory writes between them share the
same journal position while still being at different points in time (see
`TimeTravelMatrixTest.multipleSequentialCheckpointsRestoreInAnyOrderUntilAnEarlierOneIsUsed`).

### API

```java
int a = session.checkpoint();          // only valid at an instruction boundary
session.restore(a);                     // true if a is still valid
session.rewindInstructions(3);          // rewind to 3 instruction-boundaries ago (bounded auto-history, default capacity 10,000)
ExecutionSnapshot now = session.snapshotNow();
```

Checkpoints can only be taken at an instruction boundary
(`requireInstructionBoundary()` throws `IllegalStateException` otherwise):
`microOpBatch`/`batchIndex` are fully derived from `IP` at a boundary via
`primeNextInstruction()`, so a boundary-only checkpoint never needs to
serialize the in-flight micro-op batch at all — restoring it is just
`cpu.resyncAfterExternalRestore()`.

**This is deterministic simulator-state time travel, not physical reverse
execution of an actual 8086.** No real hardware can restore a prior
architectural state from nothing; this works because the simulator's entire
future is a pure function of the state captured here.

## Deterministic trace

Every event is a typed `TraceEvent` record (a sealed interface — never a
bare formatted string): `InstructionStart`, `InstructionRetired`,
`MicroOpExecuted`, `RegisterMutation`, `FlagMutation`, `MemoryWriteEvent`,
`MemoryReadEvent`, `ControlTransfer`, `BreakpointHitEvent`,
`WatchpointHitEvent`, `BiuFetchEvent`. Disabled by default
(`setTracing(false)`) since it is a meaningfully heavier, opted-in mode (see
Performance below); every event has a `toJson()`.

`BiuFetchEvent`s are derived from the *existing* Phase 3 trace
architecture — `CPU.getLastCycleSnapshot()`'s `MicroarchitectureEvent`s of
type `FETCH_BYTE` — reused, not reimplemented. `DebugSession` deliberately
runs its `CPU` under `TimingModel.FUNCTIONAL`, not `SIMPLIFIED_8086`:
`stepTimed()` (the timed path) can return `null` from `CPU.step()` without
advancing `batchIndex` (a BIU stall cycle with no micro-op executed), which
breaks the "one `step()` call = one micro-op" boundary detection this whole
session is built on. Under `FUNCTIONAL`, `BiuFetchEvent`s simply won't
appear (no stall-cycle fetch simulation runs) — the queue/fetch-pointer
*state* is still fully captured in every `ExecutionSnapshot` via direct
`PrefetchQueue`/`FetchState` inspection, independent of the timing model.

## State diff

`StateDiff.of(before, after[, memoryDelta])` computes register, flag, and
(optionally) memory changes between two snapshots — used identically by the
CLI `diff` output and any GUI/JSON consumer; the logic is not duplicated
anywhere. `diffSinceCheckpoint(id, after)` additionally derives the memory
delta from the journal range between the checkpoint and `after`, via
`MemoryJournal.netChanges` (only valid when `after` is later in the same,
non-truncated timeline as the checkpoint).

## "Why did this change?" — `explainLastRetiredInstruction()`

Returns an `InstructionExplanation` for the most recently retired
instruction, built entirely from that instruction's own trace-event window
(requires `setTracing(true)`): source text, machine bytes (when
machine-code-loaded), the micro-op RTL sequence, registers read/written
(from each `MicroOperation`'s `srcReg`/`destReg` — the same fields the
existing GUI trace already uses for register highlighting, not invented),
memory addresses read/written, flags written, and whether/where control
flow changed. Nothing here is inferred from the mnemonic; it is entirely
reconstructed from events the execution actually produced.

## CLI debugger

`simulator.DebuggerCli` — no JavaFX dependency, fully scriptable, and what
CI drives. `DebuggerCli.executeCommand`/`runScript` are the testable core
(session + command list + `PrintStream` → deterministic output); `main()`
is a thin wrapper: `DebuggerCli <program.asm|--hex BYTES> [scriptFile]`,
or an interactive stdin loop if no script file is given.

Commands: `break`, `breakoffset`, `watch`, `watchreg`, `watchflag`, `run`,
`continue`, `step`, `stepmicro`, `stepover`, `stepout`, `reset`, `trace
[off]`, `regs`, `flags`, `memory <addr> <len>`, `stack`, `checkpoint`,
`restore <id>`, `rewind [n]`, `explain`, `state`, `statejson`, `tracejson`,
`tracesave <path>`, `traceload <path>`, `quit`.

## JSON debugger API

Every domain type has a `toJson()`: `DebugState`, `ExecutionSnapshot`,
`StateDiff`, `TraceEvent` (each variant), `Breakpoint`, `Watchpoint`,
`WatchHit`, `InstructionExplanation`, `ExecutionPosition`. Field ordering
is fixed per type (no `HashMap`-driven nondeterminism in the JSON text);
breakpoint/watchpoint ids are small session-local sequence numbers, never
raw object identities. `debugger.Json` is the shared hand-rolled
escape/array helper (matching the escaping convention already used by
`simulator.MainSimulator`) — no external JSON library dependency.

## Trace export/import — inspection only, not replay

`tracesave <path>` writes the JSON trace array to a file. `traceload <path>`
reads one back and prints it for review. **This is explicitly inspection
only.** Loading a trace does not reconstruct a session or re-execute
anything — `traceload`'s own output says so
(`"REPLAY FROM TRACE is not supported"`). The tested, supported replay
mechanism in this simulator is **snapshot-based**: checkpoint → restore →
continue (see Time travel above, and `DeterminismReplayTest`/
`TimeTravelMatrixTest`). The two are deliberately not conflated: a trace log
alone doesn't carry enough state (memory contents, BIU queue, etc.) to
resume execution correctly, and building that reconstruction was out of
scope for this pass — better to not claim it than to claim it untested.

## GUI debugger

`gui.DebuggerPane`, wired into `MainGUI` as a new "Debugger" tab alongside
the existing inspector tabs — **the existing architecture dashboard is
untouched**; this is a separate, self-contained workspace with its own
`DebugSession`, independent of the CPU instance the dashboard visualizes.

Layout: **top** — Run/Pause/Continue/Step/Micro-step/Step Over/Step
Out/Reset/Checkpoint/Rewind; **left** — registers, flags; **center** —
disassembly with the current instruction highlighted; **right** —
breakpoints (add by index + optional condition), watchpoints (add by
address/register/flag), current stop reason; **bottom** — micro-operations,
trace, state diff (mark a baseline, diff against current), BIU prefetch
queue, memory/stack inspection. Every control calls a real `DebugSession`
method and every panel renders what `currentState()`/`trace()`/`diff()`
actually returned — nothing hard-coded.

This project's existing GUI tests (see `gui.MachineCodeInspectionTest`)
only ever unit-test UI-neutral projection classes, never raw JavaFX `Node`
construction — there is no TestFX/Monocle headless-UI infrastructure here.
`DebuggerPane` follows that same boundary: its logic calls straight through
to `DebugSession`, which is exhaustively tested; the JavaFX wiring itself
is verified by compilation and code review, consistent with how the rest of
this GUI is verified.

## Performance characteristics

Measured by `DebuggerPerformanceTest` (printed, not just asserted — see
that class for exact numbers on a given run): a **dormant** session (no
breakpoints, no watchpoints, tracing off) adds roughly 10-30% over raw
`CPU` execution in this environment — a couple of empty-collection checks
per micro-op. A **traced** session (an intentionally heavier, opted-in
mode) costs roughly another 1.5-2x on top of that. Fifty *disabled*
watchpoints add negligible overhead versus zero watchpoints, confirming
disabled capabilities stay dormant rather than merely "not stopping."

## Limitations, honestly stated

- **Simulator-state time travel, not physical reverse execution.** No real
  8086 can un-execute an instruction; this works because the entire
  simulator is a deterministic function of the state a snapshot captures.
- **Checkpoints are undo/redo, not a branching DAG.** Restoring to an
  earlier point invalidates later checkpoints/auto-history in that
  timeline; there is exactly one "current" future at a time.
- **`stepOver`/`stepOut` are heuristics** appropriate to this simulator's
  CALL/RET model (matched by return-`IP` + `SP` depth for step-over, by the
  first `RET`/`RETF` at a shallower depth for step-out) — not a claim of
  correctly handling arbitrary, non-matching stack manipulation.
- **Trace import is inspection-only**; it is not a replay mechanism.
- **`PAUSE` is cooperative**, checked once per micro-op; it has no effect
  called from the same thread that is blocked inside a synchronous `run()`.
- **INVALID_INSTRUCTION/EXCEPTION_TRAP** cover exceptions actually
  reachable from inside `CPU.step()` today (an unhandled operand format,
  DIV-by-zero); they are not a general fault/exception-handling model for
  the 8086 (no IVT-driven exception dispatch is modeled beyond the existing
  simplified `INT`/`IRET`).
- **A malformed instruction as the very first instruction of a program**
  throws during `CPU.loadProgram`, before any `DebugSession` exists to
  catch it (`primeNextInstruction()` runs eagerly at load time) — this is
  existing, frozen `CPU` behavior from before Phase 5, not something this
  layer changes or can intercept.

## Where the tests live

| Capability | Test class |
|---|---|
| Domain model, stepping, breakpoints/watchpoints/checkpoints (smoke) | `debugger.DebugSessionTest` |
| Determinism / replay (Step 18) | `debugger.DeterminismReplayTest` |
| Breakpoint/watchpoint/stop-reason matrix (Step 19) | `debugger.BreakpointWatchpointMatrixTest` |
| Time-travel matrix (Step 20) | `debugger.TimeTravelMatrixTest` |
| Performance (Step 21) | `debugger.DebuggerPerformanceTest` |
| CLI + JSON | `simulator.DebuggerCliTest` |

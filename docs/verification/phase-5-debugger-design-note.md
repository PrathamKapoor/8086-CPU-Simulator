# Phase 5 forensic audit — design note

Baseline: `phase-4-complete` (`1e12bf0`). This note records what the audit
found before any debugger code was written, per Phase 5 Step 1.

## Deterministic state that governs future execution

- **Registers**: `AX BX CX DX SP BP SI DI CS DS SS ES IP IR MAR MDR FLAGS`,
  all `Register` instances in `CPU.registerFile` (a `LinkedHashMap`). All
  loadable via the existing public `Register.load(int)` / `.output()`.
- **Memory**: `Memory` is a lazy `MemoryCell[0x100000]`. Two write paths —
  `busWrite()` (bus-mediated, used by every micro-op) and `directWrite()`
  (program load only). Two read paths — `busRead()` and
  `directRead()/readByte()`. `MemoryCell` tracks `written` (never-written
  vs. written-to-zero) as well as `value`.
- **Micro-op cursor**: `CPU.microOpBatch` (the decoded micro-ops for the
  *current* instruction) and `CPU.batchIndex` (position within it). Both are
  **private with no public mutator**, and both are fully *derived* from
  `pc.output()` at instruction-start via the private `primeNextInstruction()`
  (`microOpBatch = controlUnit.generateMicroOps(program.get(pc.output()))`,
  `batchIndex = 0`). This is the key fact the whole checkpoint design rests
  on: a checkpoint taken at an instruction boundary (`batchIndex == 0`) never
  needs to serialize `microOpBatch` — it only needs `IP`, and re-priming
  reproduces it exactly.
- **`halted`**: private, `isHalted()` getter only, no public setter.
- **BIU**: `PrefetchQueue` (int[6] + size, public `enqueue/dequeue/clear/
  getContents()`), `FetchState` (`nextFetchOffset`, `fetchPhysicalAddress`,
  both with public setters), `BusState` (bus-active flags + `physicalAddress`,
  public setters; `cycle` has no setter and is display-only — confirmed by
  reading every call site, nothing branches on it).
- **`ExecutionUnit`** (`decoding`/`executing` booleans): confirmed inert in
  the current execution model — `stepTimed()` (the only reachable stepping
  path; see below) tracks its own `start = batchIndex == 0` instead of
  consulting `eu`. Not correctness-relevant.
- **`ALU`** internal fields (`lastResult/lastA/lastB/lastOperation/active`):
  confirmed display-only by reading `execute()` — every call overwrites them
  fresh from its own arguments; nothing reads them back into a computation.
  Not correctness-relevant; not restored.
- **Decoder** (`InstructionDecoder.currentInstruction`): fully derived from
  `pc.output()` the same way `microOpBatch` is (set inside
  `controlUnit.generateMicroOps` → `decoder.decode(instruction)`), so it
  self-corrects on re-prime and needs no separate snapshot slot.
- **Timing counters** (`stallCycles, queueFlushes, bytesFetched,
  bytesConsumed, maxQueueOccupancy, totalOverlapCycles, busActiveCycles,
  biuFetchEvents, totalCyclesRun`): plain `int`/`long` fields on `CPU`,
  reachable via existing getters, cosmetic/metric only but explicitly
  requested for snapshot fidelity by the task — captured and restored via
  reflection-free direct getter reads (no setters exist, see "one CPU
  addition" below for how they get restored).

## `CPU.step()` / instruction-boundary semantics

`CPU.step()` dispatches on `timingModel`, which is **never null** in current
usage (`TimingModel.FUNCTIONAL` by default) — the "legacy" body at the top of
`step()` (raw BIU/EU tick simulation, lines ~237-308) is therefore dead code
today. The two real paths are `stepFunctional()` and `stepTimed()`. Both:

1. Execute exactly one `MicroOperation` (`op.execute()`), which is the
   simulator's true atomic unit — **"micro-op stepping" = one call to
   `CPU.step()`**.
2. Compute `retired = batchIndex + 1 >= microOpBatch.size() || op.getType()
   == HALT` *before* executing — this is the authoritative instruction
   retirement boundary.
3. On retirement, `batchIndex` resets to 0 and `primeNextInstruction()` fires
   for the next `pc.output()`.

Given this, **"instruction" = the span of `CPU.step()` calls between two
successive `batchIndex == 0` observations** (captured via the *existing*
public `getBatchIndex()`, `getRegister("IP").output()`, `isHalted()` — no
new API needed for this). "Instruction stepping" in the debugger therefore
means: call `cpu.step()` in a loop until a step both started at
`batchIndex == 0` and completes with the instruction retired (or the CPU
halts) — i.e., run all micro-ops of exactly one instruction.

Existing hooks already usable without touching `CPU.java`'s execution logic:
`CPU.setOnMicroOpExecuted(Consumer<MicroOperation>)`, `CPU.setOnHalt(Runnable)`,
`getCurrentMicroOp()`, `getLastExecutedMicroOp()`, `getCurrentInstruction()`,
`getCycleTrace()`/`getLastCycleSnapshot()` (typed `CycleSnapshot` +
`MicroarchitectureEvent`/`MicroarchitectureEventType` — this **is** the
existing Phase 3 trace architecture Step 10 asks to reuse).

## The one necessary CPU.java addition

Restoring registers/memory/flags to a checkpoint is fully possible through
existing public setters. The one gap: nothing public can force
`microOpBatch`/`batchIndex`/`halted` to re-derive from a restored `IP`
(`primeNextInstruction()` is private, `halted` has no setter). Rather than
reach for the private field with reflection, `CPU` gets exactly one small
additive method:

```java
/** Debugger-only: after externally restoring registers/memory/flags to a
 *  checkpoint, re-derive the pending micro-op batch for the (now-restored)
 *  PC and clear any halt latched after that point. Does not change any
 *  existing execution path. */
public void resyncAfterExternalRestore() {
    halted = false;
    primeNextInstruction();
}
```

This is additive only — every existing caller and every existing test is
unaffected. It is not a second execution engine; it calls the *same*
`primeNextInstruction()` every normal instruction boundary already calls.

## Memory watch/checkpoint strategy (Step 9)

Polling all `0x100000` cells is explicitly forbidden and would also be
wrong today (`MemoryCell` is lazily allocated — most cells are `null`).
Instead:

- **Watchpoints**: a new `MemoryAccessListener` interface, wired into
  `Memory.busWrite()`/`directWrite()` (write) and `busRead()` (read) behind a
  single nullable-listener check — zero cost when no watchpoint is active.
- **Checkpoints**: an **undo journal**, not a full copy. `DebugSession`
  installs itself as the `Memory` listener and, while a session is
  "recording" (i.e., at least one checkpoint has ever been taken), appends
  `(address, hadPriorValue, priorValue)` to an append-only log on every
  write. A checkpoint is just `{registers, flags, IP-derived state, BIU
  state, timing counters, journal-length-at-this-point}` — O(1) plus the
  fixed small state. Restoring replays the journal **backwards** from the
  current length down to the checkpoint's recorded length, undoing each
  write (`memory.getCell(addr).clear()` if it had no prior value, else
  `.write(priorValue)`), then truncates the journal to that length (so new
  writes after a restore start a fresh forward timeline — this is
  intentionally an "undo/redo-style" divergent timeline, not a branching
  DAG of independent snapshots; documented as such rather than oversold).
  This is O(changes-since-checkpoint) for both memory and time, never O(1MB).

## Conclusion

No redesign of `ControlUnit`/`MicroOperationExecutor`/the Phase 4 codec is
needed or performed. The debugger is an external observer/controller: one
new `Memory` listener hook, one new additive `CPU` method, and everything
else built as new classes in a new `debugger` package that drive the
existing `CPU` through its existing public `step()`/register/memory API.

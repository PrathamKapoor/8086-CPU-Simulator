# Phase 6 forensic baseline audit

Baseline: Phase 5 complete (`aa0795a`). This records what already exists
before any Phase 6 code was written, per Step 1.

## Reusable components found (and used, not duplicated)

- **`simulator.experiment.{ExperimentDefinition,ExperimentResult,ExperimentRunner,
  BenchmarkCatalog}`** — a *minimal* existing experiment concept from Phase 3
  (source workload, `TimingModel`, expected-register assertions, a
  `PerformanceSnapshot`). Kept exactly as-is (still used by
  `MainSimulator --benchmark`); Phase 6 does not touch it. Its naming
  (`Experiment*`) is the precedent Phase 6's richer model in the new
  `research` package follows and extends conceptually, not by inheritance
  (different package, no collision, but the same vocabulary).
- **`simulator.profiler.PerformanceProfiler` / `PerformanceSnapshot`** — a
  *pure, deterministic reduction over `CPU.getCycleTrace()`* (Phase 3's
  typed `CycleSnapshot`/`MicroarchitectureEvent` trace). Already computes
  totalCycles, instructionsRetired, CPI, bytesFetched, bytesConsumed,
  averageQueueOccupancy, queueEmptyCycles, queueFullCycles,
  biuActiveCycles, euActiveCycles, overlapCycles, euStallCycles,
  biuStallCycles, memoryReads, memoryWrites, controlTransferFlushes,
  flushedBytes. This **is** the metrics engine Step 4/5 ask for, for every
  BIU/EU/BUS/CONTROL-FLOW/TIMING/MEMORY category except branch taken-vs-
  not-taken and instruction/micro-op mix. Phase 6 wraps this unchanged.
- **`cpu.microarchitecture.MicroarchitectureEventType.CONTROL_TRANSFER`** —
  already the precise, non-heuristic event Step 5 requires flush/branch
  counts to come from ("not from heuristic instruction patterns"). Emitted
  by `CPU.stepTimed()` exactly when a retiring instruction's `IP` doesn't
  land on `previousIndex + 1`.
- **`debugger.DebugSession` / `debugger.TraceEvent`** (Phase 5) — the typed,
  per-instruction trace with real `ControlTransfer`, `MemoryWriteEvent`,
  `MemoryReadEvent`, `RegisterMutation` events tied to actual `Opcode`
  values (not string-parsed). Used, unmodified, as the *second observation
  pass* for metrics `PerformanceSnapshot` doesn't carry (branches taken vs.
  not-taken, exact per-instruction memory addresses, breakpoint/watchpoint
  hit counts) — see "Two observation passes" below for why this is a
  second *pass*, not a second *engine*.
- **`simulator.verify.{ArchVector,GoldenReference,VectorJson,VectorRunner,
  AluPropertyTest}`** (Phase 2) — golden-vector and property-test
  infrastructure. Phase 6's golden experiments (Step 18) and invariant
  tests (Step 19) are new JUnit classes following this project's existing
  test-class-per-concern convention; they do not reuse `VectorJson`'s
  parser directly (it is `private static final`, schema-specific to
  `ArchVector`), but follow the same "hand-rolled, no external JSON
  library" convention as everywhere else in this codebase.
- **`instruction.InstructionParser` / `machinecode.Intel8086Decoder`** —
  unchanged; workloads are either source assembly (parsed the same way
  `CPU.loadProgram` already expects) or raw machine-code bytes (loaded via
  `CPU.loadMachineCode`, same as Phase 4/5).

## Architectural constants Phase 6 cannot make configurable (and doesn't fake)

- **Prefetch queue capacity** (`cpu.biu.PrefetchQueue.CAPACITY = 6`) is a
  compile-time constant backing a fixed-size array. It is not an
  instance-configurable parameter anywhere in `BusInterfaceUnit`. Step 3
  says "configurable queue size **where the existing architecture
  permits**" — it doesn't, so Phase 6 does not add a queue-size experiment
  axis, and documents this explicitly rather than modifying frozen BIU
  code to add one.
- **Program/memory placement**: `CPU.loadProgram`/`loadMachineCode` always
  place the program starting at physical address 0. There is no base-offset
  parameter. Phase 6 does not add a placement axis for the same reason.
- **No randomness anywhere** in `src/` outside `test/` (`grep -r
  java.util.Random` returns nothing in main source) — so "deterministic
  seed if randomness exists" is documented as not applicable rather than
  invented.

## Two observation passes, not two execution engines

`PerformanceSnapshot`'s richest metrics (BIU/EU/bus/queue/flush) are only
populated when `CPU.step()` runs under `TimingModel.SIMPLIFIED_8086` (only
`stepTimed()` emits `FETCH_BYTE`/`QUEUE_*`/`BIU_STALL`/`CONTROL_TRANSFER`
events — `stepFunctional()`, which `DebugSession` deliberately and
permanently uses per the Phase 5 design note, does not). `DebugSession`'s
own typed trace, conversely, is only available by running through it
(`FUNCTIONAL` only, for the boundary-detection reasons documented in
`phase-5-debugger-design-note.md` — a Phase 5 architectural decision Phase
6 must not revisit).

Since Phase 6 wants *both* categories of metric and cannot get both from
one pass without editing frozen Phase 3/5 code, `research.ExperimentRunner`
executes the identical, deterministic workload **twice** when the richer
metrics are requested: once through a bare `CPU` + `SIMPLIFIED_8086` (the
existing Phase 3 path, reusing `PerformanceProfiler` verbatim) for
timing/BIU/EU/bus/flush metrics, and — only when
`ExperimentConfiguration.tracingEnabled()`/`debuggerEnabled()` is true
(default: false, per Step 3) — once more through `DebugSession` (the
existing Phase 5 path, unmodified) for branch-taken/not-taken and
debugger-trace metrics. Both passes execute the exact same `ControlUnit`/
`MicroOperationExecutor` code; nothing about instruction semantics is
reimplemented. This is documented plainly in
`phase-6-research-platform.md` rather than glossed over.

## Conclusion

No new CPU, timing, or trace implementation is created. Phase 6 adds a new
`research` package of pure, deterministic domain types and analysis
functions that call `PerformanceProfiler`, `DebugSession`, `CPU`,
`InstructionParser`, and `Intel8086Decoder` exactly as they already exist.

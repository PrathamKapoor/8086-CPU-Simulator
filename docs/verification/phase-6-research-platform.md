# Phase 6 — Research / Experimentation Platform

Baseline: Phase 5 complete (`aa0795a`). See
`phase-6-research-platform-audit.md` for the forensic inventory of what
already existed before this phase's code was written, and for the
architectural constants this phase deliberately does not make
configurable (prefetch queue capacity, program placement).

## What this is, and what it is not

**These are measurements of the simulator's documented microarchitectural
model. They are NOT measurements of physical Intel 8086 silicon.** Every
number this platform reports comes from executing this simulator's
`ControlUnit`/`MicroOperationExecutor` under its existing, frozen Phase 3
timing model (`simulator.profiler.TimingModel.SIMPLIFIED_8086`) or its
frozen Phase 5 debugger (`debugger.DebugSession`, which always runs
`TimingModel.FUNCTIONAL`). No cycle count, stall count, or bus-contention
figure in this document or produced by this platform should be read as a
claim about real 8086 hardware timing.

## Architecture

The new `research` package (`src/research/`) is a pure, deterministic
analysis layer over the existing frozen subsystems. It creates no second
CPU, no second timing model, and no second trace system:

```
research.Workload               -- a program: source assembly or raw machine-code bytes
research.ExperimentConfiguration -- TimingModel, micro-op limit, tracing/debugger toggles, expected registers
research.Experiment              -- id + description + Workload + ExperimentConfiguration
research.ExperimentRunner        -- executes an Experiment (see "Two observation passes" below)
research.ArchitecturalState      -- captured final register/flag/halted state
research.MetricProvenance        -- MEASURED | DERIVED | UNAVAILABLE, attached to every Metric
research.Metric / MetricSet      -- ~30 named metrics, each with an inline formula string
research.BusMetrics              -- bus-owner aggregation (new: not previously computed by PerformanceProfiler)
research.BranchMetrics           -- taken/not-taken/unconditional counts (requires tracing)
research.DebuggerMetrics         -- trace/breakpoint/watchpoint counts (requires tracing/debugger)
research.ResultHasher            -- deterministic SHA-256 over a fixed-order canonical string
research.ExperimentRun           -- one experiment's full, hashed result
research.Comparison / EvidenceReport -- A/B diff and a fact-backed textual summary
research.RepetitionResult / Stats    -- N repeated runs, hash-identity check, per-metric min/max/mean/median/spread
research.ExperimentArtifact / MiniJson -- JSON export/import/round-trip verification
research.BatchMatrix              -- workload axis x configuration axis grid of runs
research.Timeline                 -- cycle-by-cycle reconstruction from CPU.getCycleTrace()
research.ExperimentCatalog        -- the curated, versioned initial experiment set (Step 6)
research.SimulatorMetadata        -- commit/version string (env var, then VERSION file, never git-shelling)
```

### Two observation passes, not two execution engines

`PerformanceSnapshot`'s BIU/EU/bus/queue/flush metrics are only populated
when `CPU.step()` runs under `SIMPLIFIED_8086` (only `stepTimed()` emits
the `FETCH_BYTE`/`QUEUE_*`/`BIU_STALL`/`CONTROL_TRANSFER` events).
`DebugSession`'s typed trace, conversely, is only available by running
through it, which always uses `TimingModel.FUNCTIONAL` (a permanent Phase
5 design decision this phase must not revisit). Since Phase 6 wants both
categories of metric without editing either frozen subsystem,
`ExperimentRunner.run` executes the identical, deterministic workload
**twice** when the richer metrics are requested:

1. Always: a bare `CPU` under the experiment's configured `TimingModel`
   (default `SIMPLIFIED_8086`), reduced through the unmodified
   `PerformanceProfiler` — this is the source of every `execution.*`,
   `biu.*`, `eu.*`, `bus.*`, `memory.*`, and `timing.*` metric, plus the
   always-measured `controlFlow.queueFlushes`/`flushedBytes`.
2. Only when `ExperimentConfiguration.tracingEnabled()` or
   `debuggerEnabled()` is true (default: **false**): a fresh
   `DebugSession` over the same workload, reused unmodified — this is the
   source of `controlFlow.conditionalBranchesRetired` /
   `takenBranches` / `notTakenBranches` / `unconditionalTransfers` and
   every `debugger.*` metric. Without tracing, those metrics report
   `MetricProvenance.UNAVAILABLE` rather than a fabricated value.

Both passes execute the exact same `ControlUnit`/`MicroOperationExecutor`
code; nothing about instruction semantics is reimplemented anywhere in
`research`.

## Metrics: precise definitions

Every `Metric` carries its own formula as a string (see
`MetricSet.build`), so the definition always travels with the number.
Summary by category:

| Category | Metric | Provenance | Formula |
|---|---|---|---|
| execution | instructionsRetired | MEASURED | count of cycles with `instructionRetired == true` |
| execution | microOpsExecuted | MEASURED | == totalCycles (one micro-op per simulator cycle) |
| execution | totalCycles | MEASURED | length of the cycle trace |
| execution | cpi | DERIVED | totalCycles / instructionsRetired (0 if none retired) |
| execution | instructionsPerCycle | DERIVED | instructionsRetired / totalCycles |
| biu | bytesFetched | MEASURED | count of FETCH_BYTE events |
| biu | queueOccupancyAverage | MEASURED | mean of per-cycle queue occupancy |
| biu | queueStarvationCycles | MEASURED | cycles where EU state == EU_WAITING_FOR_QUEUE |
| biu | queueFullCycles | MEASURED | cycles where BIU state == QUEUE_FULL |
| biu | activeCycles | MEASURED | cycles where BIU state == BIU_ACTIVE |
| eu | activeCycles | MEASURED | cycles where EU state == EU_ACTIVE |
| eu | stallCycles | MEASURED | count of EU_STALL events |
| eu | biuOverlapCycles | MEASURED | cycles where BIU_ACTIVE and EU_ACTIVE both hold |
| bus | grantedToBiu / grantedToEu / idleCycles | MEASURED | count of cycles by `CycleSnapshot.busOwner()` |
| bus | requests | DERIVED | grantedToBiu + grantedToEu |
| bus | contentionCycles | MEASURED | == biuStallCycles: BIU fetch deferred because EU held the bus |
| controlFlow | conditionalBranchesRetired / takenBranches / notTakenBranches / unconditionalTransfers | MEASURED, requires tracing | classified from real `Opcode` values correlated against the trace event immediately following each `InstructionRetired` |
| controlFlow | queueFlushes / flushedBytes | MEASURED (always) | count/sum of QUEUE_FLUSH events, emitted only for a real `CONTROL_TRANSFER` (`IP != previousIndex+1`) |
| memory | reads / writes | MEASURED | count of MEM_READ / MEM_WRITE events |
| memory | cellsTransferred | DERIVED | reads + writes |
| timing | totalCycles / overlapCycles | MEASURED | same as execution.totalCycles / eu.biuOverlapCycles |
| timing | stalledCycles | DERIVED | eu.stallCycles + bus.contentionCycles |
| timing | usefulCycles | DERIVED | totalCycles - stalledCycles |
| debugger | traceEventCount / breakpointHits / watchpointHits | MEASURED, requires tracing/debugger | direct counts from `DebugSession.trace()` |

### Branch classification: event-adjacency, not index membership

`BranchMetrics.from(DebugSession)` classifies each `InstructionRetired`
trace event by looking at the **very next** trace event: if it is a
`ControlTransfer` originating from the same instruction index, that
retirement took the branch. This was deliberately chosen over a
`Set<Integer>` of "indices that ever branched", which would wrongly mark
*every* retirement of a repeatedly-executed conditional branch as taken
even on loop iterations where it did not take the branch.

## Workload catalog (Step 6)

`research.ExperimentCatalog` defines 12 curated, versioned experiments,
distinct from `simulator.experiment.BenchmarkCatalog` (Phase 3's separate,
untouched, minimal benchmark set still used by `MainSimulator
--benchmark`). Three pairs (`ExperimentCatalog.PAIRS`) are deliberately
constructed so exactly one variable differs, so an A/B comparison's
conclusion is causally attributable:

- `taken-branch` vs. `not-taken-branch` — whether the `CMP` makes the `JZ` take the branch.
- `string-operation` vs. `rep-workload` — one `MOVSB` per instruction vs. a `REP`-driven loop.
- `source-representation` vs. `machine-code-representation` — source-token program vs. its byte-identical machine-code encoding.

Two entries, `queue-starvation` and `bus-contention`, exist specifically
to exercise an elevated value of one metric; each is checked in
`GoldenExperimentsTest` against `sequential-alu` (the plain baseline) to
confirm the elevation is real and large (queue-starvation's
`biu.queueStarvationCycles` measures 8 vs. baseline's 1;
bus-contention's `bus.contentionCycles` measures 12 vs. baseline's 2) —
not merely a workload whose name promises a condition it doesn't actually
measurably produce.

`control-transfer-flush` inserts a `NOP` between each `JMP` and its
target label specifically so the jump target is not the textually-next
instruction — otherwise `IP == previousIndex+1` even though a `JMP`
executed, and `CPU.stepTimed()`'s flush condition (which checks exactly
that) would never fire.

## Determinism, reproducibility, and hashing

This simulator has no randomness anywhere in its execution path (`grep -r
java.util.Random src/` outside `test/` returns nothing) — running the
same `Experiment` any number of times executes the identical sequence of
deterministic operations and must produce identical results.
`RepetitionResult.run(experiment, n)` runs an experiment `n` times and
asserts every run's `resultHash` is identical (verified for every catalog
entry by `InvariantPropertyTest.repeatedRunsOfTheSameExperimentAreHashIdentical`).

`ResultHasher.hash` builds a canonical string —
`"id=...;workload=...;config=...;state=...;metrics=..."` — from the
experiment id, workload content fingerprint, configuration description,
final architectural state, and the full metric set in their fixed
declaration order, then SHA-256-hexes it via `java.security.MessageDigest`.
The canonical string deliberately excludes wall-clock timestamps,
object identity, and anything iteration-order-dependent from unordered
collections — nothing in the hash depends on when or how many times the
JVM has run.

`ExperimentArtifact.toJson()`/`fromJson()` round-trip an experiment
through JSON by reconstructing the `Experiment` and re-running it via
`ExperimentRunner.run()` (not deserializing the prior `ExperimentRun`
field by field), so `verifyReproducible()` is a genuine end-to-end replay
check, not a JSON-equality check.

## Trace-to-metric and timeline analysis

`research.Timeline.from(cpu)` reconstructs a cycle-by-cycle entry list
directly from the existing `CPU.getCycleTrace()` (Phase 3's typed
`CycleSnapshot`/`MicroarchitectureEvent` trace) — the same data
`PerformanceProfiler` reduces into aggregate metrics, viewed instead as
an ordered sequence. `Timeline.explainCycleCount()` produces a
deterministic, evidence-backed breakdown
(`total=... useful=... euQueueStarvation=... biuBusContention=...
controlTransferFlushCycles=...`) whose categories are cross-checked
against `MetricSet`'s own tallies in `CrossLayerConsistencyTest`, so the
aggregate metrics and the per-cycle timeline can never silently disagree.

## Evidence-backed comparison

`Comparison.of(runA, runB)` reports exactly what configuration fields
differ and every metric's numeric delta — never a manufactured
significance claim (these are two deterministic executions, not a
statistical sample). `EvidenceReport.from(comparison)` builds a headline
from the `timing.totalCycles` delta specifically and lists supporting
facts sorted by `|absoluteDelta|` descending; every sentence traces back
to a specific, named, numeric `Comparison.MetricDelta` or
`Comparison.ConfigChange`.

## CLI

`simulator.ResearchCli` (`experiment list|show|run|compare|repeat|export|
analyze|batch`) follows `simulator.DebuggerCli`'s precedent: a testable
`executeCommand`/`runScript` core plus a thin `main()`, no JavaFX
dependency. `run --json` and `export <file>.json` emit the same JSON
shape (`ExperimentArtifact.toJson()`/`ExperimentRun.toJson()`);
`export <file>.csv` emits a fixed-header CSV
(`experimentId,resultHash,passed`, then every metric name in
`MetricSet`'s declared order, blank for `UNAVAILABLE`).

## GUI

`gui.ResearchPane`, wired into `MainGUI` as a "Research" tab alongside
"Debugger", drives the same `research.*` engine the CLI and tests use —
catalog browsing, run, compare, repeat, batch, timeline analysis, and
JSON export via a file chooser. It does not touch or replace the
architecture dashboard, machine-code inspector, cycle timeline, or
`DebuggerPane`; it is entirely independent of the CPU instance those
views visualize.

## Testing

- `ExperimentRunnerTest`, `ExperimentCatalogTest` — the runner and every
  catalog entry, including that declared `expectedRegisters` are actually
  satisfied.
- `ComparisonAndReproducibilityTest` — A/B comparison, evidence reports,
  repetition/hash-identity, JSON artifact round-trips.
- `TimelineTest` — per-cycle reconstruction and `explainCycleCount()`.
- `CrossLayerConsistencyTest` — CPU execution, the Phase 5 debugger
  trace, the Phase 3 timing trace, and Phase 6 metrics never silently
  disagree about the same workload (six independent double-derivations).
- `GoldenExperimentsTest` — exact pinned values (instruction/micro-op/
  cycle counts, queue events, flush counts, memory operations, result
  hash) for seven stable catalog workloads.
- `InvariantPropertyTest` — structural properties across every catalog
  entry: no negative metric, cycles ≥ instructions retired, micro-ops ≥
  instructions retired, CPI only defined when instructions > 0,
  flushedBytes zero when there were no flushes, queue occupancy never
  exceeds the compile-time capacity of 6, 64-character SHA-256 hashes,
  JSON round-trip and repeated-run hash identity, and declared
  expectations actually satisfied.
- `ResearchCliTest` — the CLI's testable core, one test per subcommand.
- `ResearchPerformanceTest` — bounded overhead relative to raw CPU
  execution, and between the plain single-pass runner and the traced/
  debugger two-pass runner.

## Limitations

- Metrics reflect this simulator's documented, simplified 8086 timing
  model (`SIMPLIFIED_8086`/`FUNCTIONAL`), not a cycle-accurate hardware
  model and not physical silicon.
- The prefetch queue's capacity (6 bytes) and program placement (always
  physical address 0) are compile-time constants of the frozen
  architecture and are not experiment axes — see the audit note.
- Branch/debugger metrics require opting into `tracingEnabled`/
  `debuggerEnabled`, which runs a second execution pass; they report
  `UNAVAILABLE` (never a fabricated value) otherwise.

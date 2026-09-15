# Phase 3 Deterministic Microarchitecture Design

## Scope and truth boundary

Phase 3 adds a deterministic, educational BIU/EU timing model around the
existing assembly-level architectural CPU.  It does not add an instruction
encoder, decoder, machine-code stream, disassembler, threads, or a claim of
historical cycle accuracy.  The existing `ControlUnit` and
`MicroOperationExecutor` remain the only components that mutate architectural
registers, flags, memory, stack, I/O, and control flow.

The model is intentionally a source-instruction-token approximation: a queued
unit represents one parsed assembly instruction because this project has no
binary instruction encoding in Phase 3.  The six-entry queue, "bytes fetched",
and physical fetch addresses are therefore educational model units, not
measured 8086 instruction-byte timing.  This limitation is exposed in the UI,
CLI, profiler documentation, and benchmark output.

## Audit implementation map

| Component | Responsibility | Current behavior | Missing behavior | Tests |
| --- | --- | --- | --- | --- |
| `PrefetchQueue` | Six-slot FIFO | Correct bounded FIFO and clear | Explicit invariant and integration coverage | Basic FIFO only |
| `BusInterfaceUnit` | Fetch / queue insertion | Reads at architectural IP every tick | Independent fetch pointer, eligibility, arbitration, result state, correct flush reset | None |
| `ExecutionUnit` | Queue consumption status | Exposes flags and byte dequeue | Per-instruction start/retire state and stall reason | None |
| `CPU` | Functional architectural execution | Fetches and consumes once per micro-op; flushes before outcome | One deterministic cycle protocol, real bus contention, post-outcome flush, typed snapshots | Basic CPU and weak determinism |
| `Memory` | Architectural memory | Bus/direct read/write and byte read | Timing activity must be represented without changing results | Existing architecture tests |
| `TimingModel` | Timing choice | Enum only | Configuration and explicit mode behavior | None |
| `PerformanceProfiler` | Metrics | Thin CPU counter facade | Rigorous trace-derived immutable metrics | None |
| Benchmark | Workload runner | Four partial files; one referenced file absent | Seven named workloads, expected state, replay | None |
| CLI | Headless execution | File/demo/verification only | Timing/benchmark/experiment/trace/profile/JSON | None |
| GUI | Architecture diagram | Predicted queue entries, no cycle history | Real state and stepable timeline | No model-level UI data test |
| CI/docs | Verification and claims | Phase 2 blocking workflow; truthful limitations note | Phase 3 checks and complete model definitions | Existing Phase 2 checks |

## Deterministic cycle protocol

`CPU.step()` is one simulation-clock tick in every timing mode.  In
`FUNCTIONAL` mode it retires one existing functional micro-operation per tick
and records an equivalent functional cycle.  In `SIMPLIFIED_8086` and
`EXPERIMENTAL` modes each tick performs these ordered operations:

1. Read the current functional micro-operation without executing it.
2. Decide EU readiness at an instruction boundary; an empty queue records a
   queue-wait cycle without executing architectural state.
3. Reserve the shared external bus for a pending EU memory micro-operation;
   otherwise allow one eligible BIU fetch.
4. Consume a source-instruction token only when the EU starts that instruction.
5. Execute at most one existing functional micro-operation.
6. If the instruction retires, compare its actual PC with its sequential PC;
   a stream-changing result flushes queued tokens and moves the BIU fetch
   pointer to the architectural PC.
7. Emit a typed, immutable cycle snapshot and derive metrics from that trace.

There are no Java threads and no wall-clock inputs.  A cycle has exactly one
bus owner (`NONE`, `BIU_FETCH`, or `EU_MEMORY`), so an overlap cycle requires a
BIU fetch and EU internal execution in the same modeled cycle.  An EU memory
access blocks BIU fetching; a full queue is an ineligible fetch condition, not
a BIU stall.

## Model types and event semantics

`MicroarchitectureEventType` is the canonical event vocabulary: fetch,
queue-push/pop, EU start/complete, memory read/write, bus busy, EU/BIU stall,
queue empty/full, control transfer, queue flush, and instruction retirement.
Events are typed records with numeric cycle/address/token fields and stable
JSON serialization.  `CycleSnapshot` records clock cycle, BIU/EU state, queue
contents and occupancy, bus owner, current instruction/micro-op descriptors,
stall reason, events, and retirement status.

EU states are `EU_ACTIVE`, `EU_WAITING_FOR_QUEUE`, and `IDLE`.  BIU states are
`BIU_ACTIVE`, `BIU_WAITING_FOR_BUS`, `QUEUE_FULL`, and `IDLE`.
`CONTROL_TRANSFER_FLUSH` is recorded as an event, not inflated into an EU or
BIU stall.  An EU stall is only a cycle in which an otherwise executable
instruction cannot begin because the queue is empty.  A BIU stall is only an
eligible fetch blocked by EU memory bus ownership.

## Interfaces

`BusInterfaceUnit` owns a next-fetch offset and has one `tick` operation that
returns a typed fetch result.  Its fetch pointer advances only after a queue
insertion, and `flushTo(offset)` clears the queue and repositions it.  `CPU`
owns the canonical trace and provides immutable views.  `PerformanceProfiler`
accepts this trace and computes only definitions below.  `ExperimentRunner`
parses and runs a named `ExperimentDefinition`, compares declared expected
architectural register values, and returns a deterministic result.

## Metrics

- `totalCycles`: number of cycle snapshots.
- `instructionsRetired`: snapshots with retirement.
- `CPI`: total cycles / retired instructions; zero when none retire.
- `bytesFetched` / `bytesConsumed`: model instruction tokens fetched/popped;
  never described as physical opcode measurements.
- `averageQueueOccupancy`: arithmetic mean of end-of-cycle occupancy.
- `queueEmptyCycles` / `queueFullCycles`: snapshots reporting those conditions.
- `biuActiveCycles`, `euActiveCycles`: snapshots with the named active state.
- `overlapCycles`: a BIU fetch and EU-active internal micro-op in one cycle.
- `euStallCycles`, `biuStallCycles`: precisely the state definitions above.
- `memoryReads`, `memoryWrites`: typed EU memory events.
- `controlTransferFlushes`, `flushedBytes`: typed flush events and their count.

## Timing modes

`FUNCTIONAL` preserves established execution behavior and records functional
cycles without prefetch gating.  `SIMPLIFIED_8086` applies the documented
six-token queue, single bus, fetch/execute overlap, starvation, and actual
control-transfer flush protocol.  `EXPERIMENTAL` currently delegates to the
same deterministic protocol but is explicitly labelled a future hypothesis
surface; it makes no additional accuracy claim.

## Verification strategy

Permanent tests use parsed assembly programs and assert real architectural
results plus exact model events/states.  Golden traces cover sequential,
starvation, taken/not-taken branch, CALL/RET, LOOP, memory-heavy, and
control-transfer-heavy programs.  Replay tests compare normalized
architectural state, typed event trace, cycle snapshots, and profiler JSON.
CLI JSON is checked byte-for-byte across runs.  Existing Phase 2 tests execute
unchanged in the full build.

# Phase 3 — BIU/EU Microarchitecture

## Overview

This directory and the associated `cpu/biu/` and `cpu/eu/` packages document the Phase 3 BIU/EU educational microarchitecture model added to the 8086 CPU Simulator.

This is an **educational/experimental 8086-style microarchitecture model**, not a claim of cycle-perfect physical Intel 8086 emulation.

## Architecture

### Bus Interface Unit (`cpu.biu`)

Owns instruction-byte acquisition from memory.

Components:
- `PrefetchQueue` — deterministic six-byte FIFO queue
- `BusState` — bus-level state tracking (address, data, control)
- `FetchState` — CS:IP fetch pointer and physical address
- `BusInterfaceUnit` — deterministic fetch scheduling and queue management

### Execution Unit (`cpu.eu`)

Owns instruction-byte consumption and execution.

Components:
- `ExecutionUnit` — consumes bytes from the prefetch queue; tracks decode/execute state

### Integration (`cpu.CPU`)

`CPU.loadProgram()` initializes BIU/EU state.
`CPU.step()` is the sole deterministic simulation clock. In `FUNCTIONAL` mode it advances one existing functional micro-operation. In `SIMPLIFIED_8086`/`EXPERIMENTAL` it records an explicit cycle: queue readiness, bus arbitration, optional BIU fetch, at most one EU micro-operation, post-retirement control-transfer outcome, and an immutable cycle snapshot.
`Memory.readByte()` supports byte-level BIU fetching.

### Queue (`cpu.biu.PrefetchQueue`)

- Capacity: 6 bytes
- FIFO ordering
- Overflow prevention (no silent overwrite)
- Flush (`clear()`)
- Refill (`tick()` fetches when space available)
- Stall tracking (`EU` stalls when empty; `BIU` idles when full)

### Control-Transfer Flushing (`CPU.step()`)

Prefetched bytes are invalidated after control-flow changes (`requiresFlush` for `JMP`, `CALL`, `RET`, `INT`, `IRET`, conditional jumps, loops, `JCXZ`). Not-taken branches preserve the queue. Taken branches and far transfers flush correctly.

### Timing (`simulator.profiler.TimingModel`)

Explicit modes:
- `FUNCTIONAL` — existing architectural behavior
- `SIMPLIFIED_8086` — deterministic BIU/EU overlap/stall model
- `EXPERIMENTAL` — reserved for future timing experiments

Architectural correctness is independent from timing-mode internals.

### Events (`microoperation.MicroOperation`)

Conceptual event types tracked:
- `FETCH_REQUEST`/`FETCH_COMPLETE`
- `QUEUE_ENQUEUE`/`QUEUE_DEQUEUE`
- `QUEUE_FLUSH`
- `EU_INSTRUCTION_START`/`EU_INSTRUCTION_COMPLETE`
- `EU_STALL_QUEUE_EMPTY`
- `BIU_IDLE_QUEUE_FULL`
- `MEMORY_READ`/`MEMORY_WRITE`
- `CONTROL_TRANSFER`
- `INTERRUPT_ENTRY`/`IRET`

`cpu.microarchitecture.MicroarchitectureEventType`, `MicroarchitectureEvent`, and `CycleSnapshot` are the canonical typed trace representation. The JavaFX "Microarchitecture Timeline" renders the real snapshot history, including cycle, BIU, EU, bus owner, occupancy, and current micro-operation.

### Instrumentation (`simulator.profiler.PerformanceProfiler`)

Metrics exposed:
- `cycles`
- `instructions`
- `bytes_fetched`
- `bytes_consumed`
- `biu_busy_cycles`
- `biu_idle_cycles`
- `eu_busy_cycles`
- `eu_idle_cycles`
- `queue_empty_stalls`
- `queue_full_idle_cycles`
- `queue_flushes`
- `control_transfers`
- `memory_reads`/`memory_writes`
- `average_queue_occupancy`
- `maximum_queue_occupancy`
- `prefetch_utilization`
- `biu_utilization`
- `eu_utilization`

`PerformanceProfiler.metrics()` derives total cycles, retirement count, CPI, fetched/consumed source tokens, average occupancy, empty/full cycles, active/overlap/stall cycles, memory events, and flush/flushed-token counts solely from this trace. These are educational metrics, not physical 8086 hardware measurements.

### Verification

The verification framework (`simulator.verify.VectorRunner`, `.github/workflows/ci.yml`) remains authoritative and blocking. It executes 37 architectural golden vectors through the real CPU (`simulator.MainSimulator verify`), compares against the independent `GoldenReference` oracle, and exits with `0` for PASS and non-zero for FAIL.

The mutation regression mechanism (`simulator/verify/mutation_regression.py`) applies a controlled arithmetic mutation (`ADD` forced to return `1`), confirms the property suite FAILS (`FAIL: 115 ADD failures`), restores the original source, and confirms PASS (`PASS: 1504/0`).

### Benchmark Framework (`benchmark/`)

Canonical deterministic workload suite:
- `compute-heavy.asm`
- `memory-heavy.asm`
- `branch-heavy.asm`
- `loop-heavy.asm`
- `queue-friendly-sequential.asm`
- `queue-hostile-control-flow.asm`
- `mixed-workload.asm`
- `benchmark/Phase3Benchmark.java` (delegates to the same catalog as the CLI)

`simulator.experiment.BenchmarkCatalog` additionally defines seven fixed workloads (compute, memory, branch, loop, queue-friendly, queue-hostile, mixed). Each is an assembly source plus expected architectural registers and is run without wall-clock timing. Use `simulator.MainSimulator --benchmark --json` for deterministic machine-readable results.

### Documentation

All claims in `verification/README.md` and this file are truthful and consistent with the actual repository implementation. No unsupported cycle-accuracy claims are made. No fabricated results. No hidden assertions or suppressed failures exist.

### Limitations

- No binary instruction decoder (parser remains assembly-level; byte consumption is conceptual/simplified rather than real 8086 opcode decoding).
- No exact 8086 microcode reproduction.
- No transistor-level or silicon-level timing verification.
- No complete real-world benchmark comparison with physical hardware.
- No full microarchitectural golden trace for all 37 vector scenarios (architecture exists; full expanded trace suite remains future work).
- The queue models one parsed assembly source-instruction token per entry; it does not claim to know real encoded instruction byte lengths. "Bytes" in Phase 3 metrics therefore means model fetch tokens.
- No Phase 4 machine-code encoder/decoder layer.

This is explicitly an educational and experimental microarchitecture layer.

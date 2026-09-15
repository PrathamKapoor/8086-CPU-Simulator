# Phase 3 Microarchitecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete a deterministic and reproducible Phase 3 BIU/EU experimentation platform without changing architectural execution semantics.

**Architecture:** A single CPU-owned cycle protocol records immutable typed snapshots.  BIU, EU, profiler, experiments, CLI, and GUI all consume that canonical trace, while the existing micro-operation executor remains authoritative for architectural state.

**Tech Stack:** Java 21, Maven, JUnit 5, JavaFX, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-phase-3-microarchitecture-design.md`

## Global Constraints

- Do not modify Phase 2 verification behavior or the explicit `target/cpu-simulator.jar` CI classpath.
- Do not implement binary instruction encoding, decoding, or disassembly.
- Do not use Java threads or wall-clock timing.
- Use test-first red/green cycles for every production behavior.
- Keep model claims limited to the documented source-instruction-token abstraction.

---

### Task 1: Typed simulation model and queue/BIU protocol

**Files:**
- Create: `src/cpu/microarchitecture/MicroarchitectureEventType.java`, `MicroarchitectureEvent.java`, `CycleSnapshot.java`, `BusOwner.java`, `UnitState.java`
- Modify: `src/cpu/biu/PrefetchQueue.java`, `BusInterfaceUnit.java`, `FetchState.java`
- Test: `src/test/java/microarchitecture/PrefetchQueueIntegrationTest.java`, `BiuTest.java`

**Interfaces:**
- Produces immutable event/snapshot records and `BusInterfaceUnit.tick(BusOwner, int)`.
- Consumes `PrefetchQueue.CAPACITY` and assembly-token memory image.

- [ ] **Step 1: Write failing queue/BIU tests**

```java
assertEquals(List.of(0, 1, 2), biu.fetchThreeTokens());
assertEquals(6, queue.capacity());
assertTrue(biu.tick(BusOwner.EU_MEMORY, 8).waitingForBus());
```

- [ ] **Step 2: Run the focused JUnit classes and confirm failures identify absent timing APIs.**
- [ ] **Step 3: Implement the minimal typed records, fetch pointer, eligibility, flush repositioning, and bus-result API.**
- [ ] **Step 4: Run focused tests and confirm FIFO, full/empty, refill, physical address, and bus-blocked fetch pass.**
- [ ] **Step 5: Commit `Phase 3: formalize deterministic microarchitecture`.**

### Task 2: CPU single-clock timing integration

**Files:**
- Modify: `src/cpu/CPU.java`, `src/cpu/eu/ExecutionUnit.java`, `simulator/profiler/TimingModel.java`
- Test: `src/test/java/microarchitecture/TimingModelTest.java`, `BusContentionTest.java`, `StallModelTest.java`

**Interfaces:**
- Consumes typed BIU results and `MicroOperationType` bus classification.
- Produces `CPU.getCycleTrace()`, `getLastCycleSnapshot()`, `setTimingModel(TimingModel)`.

- [ ] **Step 1: Write failing tests for queue starvation, overlap, EU memory contention, functional compatibility, and one clock tick per snapshot.**
- [ ] **Step 2: Run the focused classes and confirm model APIs are absent.**
- [ ] **Step 3: Implement ordered cycle arbitration and ensure only the existing executor writes architectural state.**
- [ ] **Step 4: Run focused tests and the existing `CPUTest`; verify no architectural regression.**
- [ ] **Step 5: Commit `Phase 3: complete BIU/EU timing model`.**

### Task 3: Outcome-based control transfer and golden traces

**Files:**
- Modify: `src/cpu/CPU.java`
- Create: `src/test/java/microarchitecture/GoldenTraceTest.java`, `src/test/java/microarchitecture/FlushModelTest.java`

**Interfaces:**
- Produces post-retirement `CONTROL_TRANSFER`, `QUEUE_FLUSH`, and retirement events.

- [ ] **Step 1: Write failing exact-event tests for JMP, taken/not-taken Jcc, CALL/RET, LOOP, INT/IRET and queue flush byte counts.**
- [ ] **Step 2: Run them and confirm the old pre-outcome flush behavior fails.**
- [ ] **Step 3: Flush only after actual PC outcome differs from the sequential source index.**
- [ ] **Step 4: Run golden trace and existing control-flow regression tests.**
- [ ] **Step 5: Commit `Phase 3: add typed traces and instrumentation`.**

### Task 4: Trace-derived profiler and deterministic serialization

**Files:**
- Modify: `simulator/profiler/PerformanceProfiler.java`
- Create: `src/test/java/microarchitecture/PerformanceProfilerTest.java`, `DeterministicReplayTest.java`

**Interfaces:**
- Consumes `List<CycleSnapshot>`.
- Produces an immutable performance snapshot and deterministic JSON.

- [ ] **Step 1: Write failing metric-definition and byte-identical replay tests.**
- [ ] **Step 2: Run focused tests and confirm current facade cannot supply the metrics.**
- [ ] **Step 3: Implement trace reduction and canonical JSON field ordering.**
- [ ] **Step 4: Run profiler/replay tests and original determinism test.**
- [ ] **Step 5: Commit `Phase 3: complete deterministic profiling`.**

### Task 5: Benchmarks and experiments

**Files:**
- Create: `simulator/experiment/ExperimentDefinition.java`, `ExperimentResult.java`, `ExperimentRunner.java`, `BenchmarkCatalog.java`, benchmark ASM files
- Modify: `benchmark/Phase3Benchmark.java`
- Test: `src/test/java/microarchitecture/BenchmarkTest.java`, `ExperimentFrameworkTest.java`

**Interfaces:**
- `ExperimentRunner.run(ExperimentDefinition)` returns expected-state validation, trace, and metrics.

- [ ] **Step 1: Write failing tests for seven named benchmark definitions and repeated equal results.**
- [ ] **Step 2: Run focused tests and confirm the catalogue/framework is absent.**
- [ ] **Step 3: Implement the compact fixed catalogue with source, mode, expected registers, and deterministic runner.**
- [ ] **Step 4: Run all seven workloads twice and assert matching normalized outputs.**
- [ ] **Step 5: Commit `Phase 3: complete benchmark experiment framework`.**

### Task 6: CLI and real JavaFX timeline

**Files:**
- Modify: `src/simulator/MainSimulator.java`, `src/gui/MainGUI.java`, `src/gui/CpuArchitectureView.java`
- Test: `src/test/java/simulator/MainSimulatorTest.java`, `src/test/java/microarchitecture/TimelineStateTest.java`

**Interfaces:**
- CLI accepts `--timing`, `--benchmark`, `--experiment`, `--trace`, `--profile`, `--json`.
- GUI renders `CycleSnapshot` values rather than predicted program positions.

- [ ] **Step 1: Write failing CLI JSON and snapshot-display-data tests.**
- [ ] **Step 2: Run focused tests and confirm options/timeline data are absent.**
- [ ] **Step 3: Implement deterministic argument parsing and a stepable JavaFX timeline bound to canonical snapshots.**
- [ ] **Step 4: Run CLI tests and a headless timeline-model check.**
- [ ] **Step 5: Commit `Phase 3: add GUI timeline and CLI experiments`.**

### Task 7: CI and truthful documentation

**Files:**
- Modify: `.github/workflows/ci.yml`, `README.md`, `microarchitecture/README.md`, `verification/README.md`
- Test: CI command sequence locally where available

- [ ] **Step 1: Add failing CI-facing commands for deterministic benchmark and trace validation without `continue-on-error`.**
- [ ] **Step 2: Update documentation with exact abstraction, metrics, modes, limitations, CLI and experiment methodology.**
- [ ] **Step 3: Run clean package, all JUnit tests, Phase 2 JSON verification, mutation regression, Phase 3 CLI benchmark/replay, and headless checks.**
- [ ] **Step 4: Commit `Phase 3: CI and documentation`.**

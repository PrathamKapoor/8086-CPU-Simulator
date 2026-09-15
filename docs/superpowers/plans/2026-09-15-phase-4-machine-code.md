# Phase 4 Machine-Code Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic 8086 assembly encoding, byte decoding, canonical disassembly and machine-code execution while retaining the existing semantic executor.

**Architecture:** `machinecode` owns cursor, ModR/M and codec data objects.  It reconstructs existing `instruction.Instruction` objects, and CPU machine mode stores real bytes in Memory but calls the existing ControlUnit.  Existing Phase 3 source-token execution remains unchanged.

**Tech Stack:** Java 21, JUnit 5, Maven, existing JavaFX GUI and GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-phase-4-machine-code-design.md`

## Global Constraints

* Support Intel 8086 encodings only; do not add later x86 architectural features.
* Preserve Phase 2 and Phase 3 behavior, APIs and explicit `target/cpu-simulator.jar` CI invocation.
* Use typed failures for malformed/unsupported byte forms; do not silently decode an unknown opcode.
* Record authoritative encoding-vector provenance before adding expected byte assertions.
* Use deterministic test input and trace-derived results only.

---

### Task 1: Domain objects and checked byte cursor

**Files:**
- Create: `src/machinecode/{EncodedInstruction,DecodedInstruction,ByteCursor,MachineCodeException,DecodeException,EncodeException}.java`
- Test: `src/test/java/machinecode/ByteCursorTest.java`

**Interfaces:**
- Produces `ByteCursor(byte[] bytes, int offset)`, `readU8()`, `readU16LE()`, `position()`, `remaining()` and immutable decode/encode values.

- [ ] Write tests that read byte/word little endian and verify a missing byte throws `DecodeException` containing the starting offset.
- [ ] Run `mvn -Dtest=machinecode.ByteCursorTest test`; observe compilation failure because the API is absent.
- [ ] Implement only the bounds-checked cursor and immutable value objects needed by the test.
- [ ] Re-run that test, then `mvn test`.
- [ ] Commit the domain-model milestone after its full regression passes.

### Task 2: ModR/M address representation

**Files:**
- Create: `src/machinecode/ModRm.java`
- Test: `src/test/java/machinecode/ModRmTest.java`

**Interfaces:**
- Consumes `ByteCursor`; produces `ModRm.decode(cursor, width)` and `ModRm.encode(memoryOrRegister, regField, width)`.
- Produces an explicit register-direct or 8086 base/index/direct-displacement address record.

- [ ] Write tests for all four `mod` values, direct `00/110`, BP zero displacement, and all eight r/m forms.
- [ ] Run the specific JUnit class and verify failure for missing `ModRm`.
- [ ] Implement explicit tables for register code, r/m code, displacement width and little-endian fields.
- [ ] Re-run ModR/M and all JUnit tests.
- [ ] Commit the ModR/M milestone after regression.

### Task 3: Codec families and golden vectors

**Files:**
- Create: `src/machinecode/{Intel8086Encoder,Intel8086Decoder,CanonicalDisassembler}.java`
- Create: `docs/verification/phase-4-machine-code-vectors.md`
- Test: `src/test/java/machinecode/{EncoderGoldenVectorTest,DecoderGoldenVectorTest,InvalidEncodingTest,InstructionLengthTest}.java`

**Interfaces:**
- `encode(Instruction instruction, int instructionAddress)` returns `EncodedInstruction`.
- `decode(byte[] bytes, int offset)` returns `DecodedInstruction`.
- `disassemble(DecodedInstruction decoded)` returns canonical assembly.

- [ ] Add a cited, failing golden-vector test for each newly supported 8086 family before its encoder/decoder code.
- [ ] Add failing truncation and unknown-opcode tests for each family.
- [ ] Implement only the family required by those vectors, including prefixes, ModR/M, immediates and length accounting.
- [ ] Run focused tests and `mvn test` after each family.
- [ ] Commit coherent codec-family milestones.

### Task 4: CPU byte program integration and equivalence

**Files:**
- Modify: `src/cpu/CPU.java`, `src/cpu/biu/BusInterfaceUnit.java`
- Test: `src/test/java/machinecode/MachineCodeExecutionTest.java`

**Interfaces:**
- Adds a byte-program loading method that decodes a stream into existing semantic instructions and stores raw bytes in `Memory`.

- [ ] Write a failing source-versus-machine execution test using a program supported by the codec.
- [ ] Implement the smallest isolated machine-mode program representation and actual byte memory load.
- [ ] Verify equivalent registers, flags, memory and final control state; verify queue values are raw bytes and instruction boundaries remain coherent.
- [ ] Run the Phase 2 CLI vector suite and full Maven regression.
- [ ] Commit integration only after all evidence is green.

### Task 5: CLI, GUI, documentation and CI release gate

**Files:**
- Modify: `src/simulator/MainSimulator.java`, `src/gui/MainGUI.java`, `README.md`, Phase 4 docs, `.github/workflows/*`
- Test: `src/test/java/simulator/MachineCodeCliTest.java`, GUI/headless integration test where practical.

- [ ] Write failing CLI tests for encode, decode, disassemble, assemble, machine-code and deterministic JSON/invalid arguments.
- [ ] Implement CLI on codec façades, then GUI inspection from real decoded snapshots only.
- [ ] Reconcile documentation, benchmark/CI commands, unsupported forms and known limitations.
- [ ] Run Maven clean test/package, Phase 2 verification, ALU/mutation, all codec/property/integration tests and secret/release scans.
- [ ] Push coherent commits, wait for a blocking green CI run, verify `HEAD == origin/main`, and report evidence only.

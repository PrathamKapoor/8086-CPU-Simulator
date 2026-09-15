# Phase 4 Machine-Code Layer Design

## Baseline

Phase 4 begins at `f1890ddc64b0f6fb1cbb457d5faf91965fa8c84a`.  The source-token
execution path, deterministic BIU/EU timing trace, profiler, experiments, and
Phase 2/3 verification are frozen compatibility surfaces.  `mvn clean test`
on this baseline completed with 132 tests and no failures.

## Goal

Add a real 8086 byte-code translation layer without introducing a second
instruction-semantics implementation:

`assembly parser -> Instruction -> encoder -> bytes -> decoder -> Instruction
-> existing ControlUnit`.

The decoder's output is the existing immutable `instruction.Instruction`
type.  Its operands and formats remain the only objects supplied to the
ControlUnit and MicroOperationExecutor.

## Chosen boundary

The new `machinecode` package owns byte-oriented concerns only:

* `EncodedInstruction` is immutable output (raw bytes, length and semantic
  instruction).
* `DecodedInstruction` captures start offset, raw bytes, length, prefixes and
  the reconstructed semantic instruction.
* `ByteCursor` performs bounds-checked little-endian stream consumption.
* `ModRm` represents the four `mod` classes and the eight 8086 r/m forms; it
  never derives addressing from a display string.
* `Intel8086Encoder`, `Intel8086Decoder` and `CanonicalDisassembler` are the
  bidirectional codec façade.  Typed encode/decode exceptions reject unsupported
  or malformed input; unknown bytes are never converted to NOP.

The initial machine-code loading API will retain a byte array and decoded
instruction boundaries in CPU program state.  It writes actual bytes into
existing `Memory`, while source-token `loadProgram` retains its existing
behavior.  The BIU may therefore fetch real byte values in machine mode, and
the EU receives the already-decoded existing semantic instruction.  This
preserves Phase 3's deterministic single-clock model while avoiding a duplicate
execution engine.

## Encoding policy

Encodings are selected by operand kinds, width and ModR/M form, not by raw
assembly text.  Direct memory uses 8086 `mod=00,r/m=110`; BP with no explicit
displacement uses the required `mod=01,disp8=0` form.  Little-endian words and
signed short relative displacement are explicit helpers.  All short branches
calculate `target - addressAfterInstruction` and reject values outside
`[-128,127]`.

Only implemented forms are encodable.  Unsupported forms fail with a typed,
actionable diagnostic; they are not given invented bytes.  The canonical
disassembler emits one deterministic spelling for each implemented encoding.
Canonical byte round trips are byte-identical.

## Verification and provenance

Every codec family starts with a failing JUnit test.  Golden byte vectors will
be traced to Intel's 8086 instruction-set reference material and cited in the
machine-code verification documentation.  Tests cover exact bytes, ModR/M
enumeration, cursor boundaries, truncated/invalid input, deterministic property
generation, decode/encode round trips, source/machine architectural equivalence,
CLI output and BIU queue behavior for variable-length instructions.

No historical timing claims are added: Phase 3 remains a documented simplified
timing model.  Machine mode changes instruction bytes and boundaries, not the
architectural effect of an instruction.

## Non-goals

This is an 8086 real-mode educational codec.  It does not add 80186-or-later
instructions, 32-bit registers, protected mode, paging, x86-64, or a parallel
CPU execution engine.  GUI inspection follows only after codec and execution
integration are verified.

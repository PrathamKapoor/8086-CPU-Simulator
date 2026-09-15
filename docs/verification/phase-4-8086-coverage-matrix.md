# Phase 4 8086 codec coverage matrix

Status is per *encoding form*, not merely per mnemonic. `SUPPORTED` means the
encoder, decoder, canonical disassembler, and existing semantic executor have
an automated test. `UNSUPPORTED` means the codec rejects the form with a
typed error (`EncodeException`/`DecodeException`), and — where the reason is
not simply "not yet built" — the reason is documented in this table. This is
an educational 8086 subset, not a claim of complete hardware emulation.

The opcode forms are derived from Intel's *8086 Family User's Manual* and the
Intel SDM instruction reference (links in `phase-4-machine-code-vectors.md`).

This is the state after the Phase 4 closure pass: every row below was
re-audited against the actual encoder/decoder/ControlUnit code (not inferred
from mnemonic-level support), and several execution-layer gaps found during
that audit were fixed rather than worked around — see "Execution-layer fixes"
below the table.

| Family / legal 8086 form | Encoder | Decoder | Execution | Disassembly | Vectors / round-trip / negative / equivalence | Status |
|---|---|---|---|---|---|---|
| MOV general reg <-> r/m; reg immediate | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| MOV accumulator <-> direct memory (A0-A3), canonical over 8A/8B when no base/index | yes | yes | yes | yes | automated / automated / automated / partial | SUPPORTED |
| MOV segment register <-> general register, register-direct (8C/8E) | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| MOV segment register <- memory (8E, memory source) | yes | yes | yes | yes | automated / n/a / n/a / partial | SUPPORTED |
| MOV memory <- segment register (8C, memory destination) | no | typed rejection | no | no | no | UNSUPPORTED — the source-level model never implemented storing a segment register to memory; only the load direction (8E) exists |
| MOV CS as a MOV destination (8E, reg=CS) | no | typed rejection | n/a | no | negative only | UNSUPPORTED — semantic limitation, not a gap: CS can only change via a control transfer on real 8086, matching the source parser's existing rule |
| MOV r/m, immediate (C6/C7) | yes | yes | yes | yes | automated / automated / n/a / n/a | SUPPORTED |
| ALU ADD/OR/ADC/SBB/AND/SUB/XOR/CMP r/m<->reg, imm r/m (incl. memory operands) | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| TEST accumulator imm (A8/A9), register/memory imm (F6/F7 /0), r/m<->reg (84/85) | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| XCHG reg/reg, AX/reg canonical form, and memory (86/87) | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| INC/DEC/NOT/NEG register | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| INC/DEC memory, word only (FF /0,/1) | yes | yes | yes | yes | automated / automated / n/a / automated | SUPPORTED |
| INC/DEC memory, byte (FE /0,/1 with mod != 11) | no | typed rejection | no | no | negative only | UNSUPPORTED — the byte-width memory RMW path was not built; register and word-memory forms cover the family's realistic test surface |
| NOT/NEG memory, byte and word (F6/F7 /2,/3) | yes | yes | yes | yes | automated / automated / n/a / automated | SUPPORTED |
| shift/rotate by literal 1, register (D0/D1) | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| shift/rotate by CL (D2/D3) | no | typed rejection | no | no | negative only | UNSUPPORTED — semantic/execution limitation: `MicroOperationExecutor.shift_reg`'s count parameter is a literal baked in at micro-op construction time; it never re-reads CL at execution time, so encoding this form would silently execute the wrong count instead of failing loudly |
| shift/rotate, memory operand | no | not attempted | no | no | no | UNSUPPORTED — no source-level or execution support exists for this form; out of scope for this pass |
| MUL/IMUL/DIV/IDIV register (F6/F7 /4../7) | yes | yes | yes | yes | automated / automated / n/a / n/a | SUPPORTED |
| MUL/IMUL/DIV/IDIV, memory operand | no | typed rejection | no | no | negative only | UNSUPPORTED — semantic/execution limitation: `mul_reg`/`imul_reg`/`div_reg`/`idiv_reg` only ever resolve a register by name |
| LEA/LDS/LES, memory operand | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| IN/OUT, accumulator/DX fixed forms (E4/E5/EC/ED/E6/E7/EE/EF) | yes | yes | yes | yes | automated / n/a / n/a / automated | SUPPORTED |
| PUSH/POP general register | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| PUSH/POP segment register (06/0E/16/1E/07/17/1F) | yes | yes | yes | yes | automated / n/a / n/a / automated | SUPPORTED |
| POP CS (0x0F) | no | typed rejection | n/a | no | negative only | UNSUPPORTED — semantic limitation, not a gap: 0x0F was reused as the two-byte-opcode escape from the 80286 onward, and the source parser already forbids "POP CS" for the same reason |
| PUSH/POP memory (FF /6, 8F /0) | yes | yes | yes | yes | automated / n/a / n/a / automated | SUPPORTED |
| short Jcc/LOOP/JCXZ, short/near JMP, near CALL, RET | yes | yes | yes | yes | automated / automated / automated / automated | SUPPORTED |
| RET imm16 (0xC2) | yes | yes | yes | yes | automated / automated / n/a / n/a | SUPPORTED |
| indirect JMP/CALL via register or memory (FF /2, FF /4) | no | typed rejection | no | no | negative only | UNSUPPORTED — semantic/execution limitation: this simulator's control-transfer model resolves JMP/CALL/RET targets to indices into a decode-time-translated instruction list (see `CPU.translateMachineControlTargets`); ControlUnit's JMP/CALL dispatch only ever reads that static, precomputed target, never a runtime register or memory value, so a target only known at execution time has no correct path without a different addressing model entirely |
| far JMP/CALL, direct (EA/9A) and indirect (FF /3, FF /5); RETF imm16 (0xCA) | no | typed rejection | no | no | negative only | UNSUPPORTED — semantic/execution limitation: on top of the indirect-target problem above, far transfers require segment:offset semantics this simulator does not model for control flow; RETF already executes identically to RET (a pre-existing, documented simplification) rather than genuinely restoring CS |
| fixed flags, adjust, strings, INT imm8, INT3, INTO, IRET, WAIT, XLAT | yes | yes | yes | yes | automated / automated / n/a / partial | SUPPORTED |
| LOCK prefix | no | typed rejection | n/a (marker only) | no | negative only | UNSUPPORTED — semantic limitation: LOCK is architecturally a bus-lock qualifier that must immediately precede a read-modify-write opcode; a single-CPU simulator has no bus arbitration to model, and the source-level model's existing "LOCK" is a bare marker instruction, not a genuine prefix-fusion — encoding it as a standalone byte would misrepresent real hardware semantics (0xF0 immediately before another opcode qualifies that opcode, it is not two separate instructions) |
| ESC (coprocessor escape, 0xD8-0xDF) | no | typed rejection | n/a (marker only) | no | negative only | UNSUPPORTED — semantic limitation: real ESC always carries a ModR/M-addressed operand for the coprocessor; the source-level model's bare "ESC" marker carries no such operand, and x87/coprocessor state is explicitly out of scope for this integer 8086 subset |
| REP/REPNE and ES/CS/SS/DS segment prefixes | yes | yes | yes | yes | automated / automated / automated / n/a (no source syntax for segment overrides) | SUPPORTED |

## Execution-layer fixes found during this audit

The audit in this pass did not stop at "does the encoder/decoder exist" —
several forms the matrix previously called SUPPORTED (or that looked
supportable) turned out to have no correct execution path once decoded, and
were fixed rather than left as a silent trap:

* `ControlUnit`'s ALU (ADD/SUB/ADC/SBB/AND/OR/XOR), CMP, TEST, XCHG, PUSH/POP
  and unary (INC/DEC/NEG/NOT) dispatch treated every operand as a register
  name. Memory-operand forms of these families — which the encoder/decoder
  already round-tripped, and which the parser already accepted as syntax —
  either threw `IllegalArgumentException: Unknown register` or read a stale
  value. `MicroOperationExecutor` gained MDR-resident variants of each
  operation (mirroring the existing MOV/LEA load-effective-address idiom),
  and `ControlUnit` now dispatches on `InstructionFormat` for all of them.
* `InstructionParser`'s ALU-family case validated the destination as a
  register *before* checking whether it was a memory reference, so
  `"ADD [BX], AX"`-shaped source lines were rejected outright.
* Byte-immediate ALU/CMP/TEST forms decoded from machine code (opcode 0x80,
  register-direct) produced `InstructionFormat.REG_IMM8`, which
  `ControlUnit`'s ADD/CMP/TEST dispatch did not recognize (only `REG_IMM`),
  so decoding then re-executing such an instruction would have failed.
* `Intel8086Encoder`'s byte/word-size detection for memory-destination
  immediate forms checked `rawText.startsWith("BYTE")`, which can never be
  true once the mnemonic is prepended (`"ADD BYTE [BX], 5"` does not start
  with `"BYTE"`) — memory-immediate ALU/MOV/TEST forms always encoded as
  word-width. Changed to a `contains` check.
* `TEST`'s memory-source direction reused the same `base + (byteWidth?2:3)`
  arithmetic as ADD/SUB/etc, which for TEST's base opcode (0x84) produces
  0x86/0x87 — XCHG's opcodes, not TEST's. TEST does not have a direction bit
  at all (0x84/0x85 always); this is now encoded via its own `encodeTest`.
* `ModRm.memory()`'s `IllegalArgumentException` for an illegal base/index
  combination (e.g. `SI+DI`, neither of which is ever a base register) was
  not caught anywhere in the encoder, violating the "typed error only"
  contract; `Intel8086Encoder.memoryOperand()` now wraps it as
  `EncodeException`.

None of these required a second execution implementation — every fix reuses
`ControlUnit`/`MicroOperationExecutor`, extending the same
load-effective-address/MDR pattern already established for MOV, LEA, LDS and
LES.

## Canonical choices

* `XCHG AX, r16` emits `90+rw`; ordinary register exchange emits `86/87 /r`.
* `INT 3` emits the dedicated `CC` opcode; `CD 03` decodes to the same
  semantic instruction but is a non-canonical alternate encoding.
* MOV/TEST with a direct-memory accumulator operand (no base/index register)
  emits the short `A0-A3`/`A8-A9` forms; the general ModR/M forms remain
  legal, non-canonical alternate encodings the decoder still accepts.
* The encoder emits the segment-override prefix before the repeat prefix.
  The decoder accepts either order and preserves both prefix bytes, and
  rejects duplicate segment or repeat prefixes.

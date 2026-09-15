# Phase 4 Machine-Code Golden Vectors

## Provenance

The encoding notation and ModR/M field rules used by these tests are from
Intel's *Intel 64 and IA-32 Architectures Software Developer's Manual*, Volume
2A, Appendix A, "Instruction Format" and the Volume 2 opcode maps:

* https://www.intel.com/content/dam/www/public/us/en/documents/manuals/64-ia-32-architectures-software-developer-vol-2a-manual.pdf
* https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html

Those manuals describe the legacy instruction forms retained from the 8086,
including an opcode-following ModR/M byte, the `reg` operand/opcode-extension
field, immediate operands and relative offsets.  The simulator intentionally
uses only 8086 forms; it does not adopt later operand-size or REX extensions.

## Initial corpus

| Assembly | Bytes | Decoder result |
| --- | --- | --- |
| `NOP` | `90` | `NOP`, length 1 |
| `HLT` | `F4` | `HLT`, length 1 |
| `MOV AX, BX` | `89 D8` | register-to-register `MOV` |
| `MOV AL, 127` | `B0 7F` | byte immediate `MOV` |
| `MOV AX, 1234H` | `B8 34 12` | word immediate `MOV` |
| `MOV AX, [BX+SI-2]` | `8B 40 FE` | ModR/M `01:000:000`, signed disp8 |

`machinecode.MovCodecTest` owns these executable vectors.  Later rows are
added only alongside a permanent test and the corresponding decoder family.

## Extended corpus (Phase 4 closure pass)

| Assembly | Bytes | Decoder result |
| --- | --- | --- |
| `MOV AL, [1234H]` | `A0 34 12` | accumulator direct-memory `MOV`, canonical over `8A`/`8B` |
| `MOV [1234H], AX` | `A3 34 12` | accumulator direct-memory `MOV` (store direction) |
| `MOV DS, AX` | `8E D8` | register-direct segment `MOV` |
| `MOV AX, DS` | `8C D8` | register-direct segment `MOV` (read direction) |
| `MOV WORD [BX], 1234H` | `C7 07 34 12` | `MOV` r/m16, immediate |
| `TEST AL, 5` | `A8 05` | accumulator immediate `TEST` |
| `TEST CX, 5` | `F7 C1 05 00` | register immediate `TEST` (Group 3 /0) |
| `TEST [BX], AX` | `85 07` | memory-operand `TEST` |
| `XCHG AX, [BX]` | `87 07` | memory-operand `XCHG` |
| `SHL AX, 1` | `D1 E0` | shift-by-1 register form |
| `MUL BX` | `F7 E3` | Group 3 /4, register operand |
| `LEA AX, [BX+SI]` | `8D 00` | load effective address |
| `LDS AX, [BX]` | `C5 07` | load far pointer into DS |
| `LES BX, [SI]` | `C4 1C` | load far pointer into ES |
| `IN AL, 40H` | `E4 40` | fixed accumulator/immediate-port `IN` |
| `OUT DX, AX` | `EF` | fixed accumulator/DX-port `OUT` |
| `PUSH ES` | `06` | segment-register `PUSH` |
| `PUSH [BX]` | `FF 37` | Group 5 /6, memory operand |
| `POP [BX]` | `8F 07` | memory-operand `POP` |
| `INT 3` | `CC` | dedicated breakpoint opcode, canonical over `CD 03` |
| `RET 4` (decode-only; no source syntax) | `C2 04 00` | `RET` with a stack-adjusting immediate |
| `INC WORD [BX]` | `FF 07` | Group 5 /0, memory operand |
| `NOT WORD [BX]` | `F7 17` | Group 3 /2, memory operand |

`machinecode.ExtendedFormsCodecTest` owns these executable vectors, along
with the canonical-encoding assertions (e.g. that a ModR/M-encoded direct
address re-encodes to the accumulator short form, and that `CD 03`
re-encodes to `CC`). See `docs/verification/phase-4-8086-coverage-matrix.md`
for which legal 8086 forms remain intentionally unsupported and why.

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

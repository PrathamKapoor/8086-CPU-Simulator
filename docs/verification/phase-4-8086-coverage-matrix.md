# Phase 4 8086 codec coverage matrix

Status is per *encoding form*, not merely per mnemonic. `SUPPORTED` means the
encoder, decoder, canonical disassembler, and existing semantic executor have
an automated test. `PARTIAL` means only the listed forms are supported.
`UNSUPPORTED` means the codec rejects the form with a typed error. This is an
educational 8086 subset, not a claim of complete hardware emulation.

The opcode forms are derived from Intel's *8086 Family User's Manual* and the
Intel SDM instruction reference (links in `phase-4-machine-code-vectors.md`).

| Family / legal 8086 form | Encoder | Decoder | Execution | Disassembly | Vectors / round-trip / negative / equivalence | Status |
|---|---|---|---|---|---|---|
| MOV general reg <-> r/m; reg immediate | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| MOV accumulator/direct-memory, segment registers, immediate r/m | no | no | partial | no | no | PARTIAL |
| ALU ADD/OR/ADC/SBB/AND/SUB/XOR/CMP r/m<->reg, imm r/m | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| TEST r/m,reg | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| TEST immediate forms | no | no | yes | no | no | PARTIAL |
| XCHG reg/reg and AX/reg canonical form | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| XCHG memory forms | no | typed rejection | partial | no | no | UNSUPPORTED |
| INC/DEC register; NEG/NOT register | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| shift/rotate, MUL/DIV, LEA/LDS/LES, IN/OUT | no | no | semantic layer only | no | no | UNSUPPORTED |
| PUSH/POP general register | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| segment/memory PUSH/POP | no | no | partial | no | no | UNSUPPORTED |
| short Jcc/LOOP/JCXZ, short/near JMP, near CALL | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| far / indirect JMP/CALL and RET immediate | no | no | partial | no | no | UNSUPPORTED |
| fixed flags, adjust, strings, INT imm8, INTO, IRET, WAIT, XLAT | yes | yes | yes | yes | automated / automated / partial / partial | SUPPORTED |
| INT3 dedicated opcode, LOCK, ESC | no | no | partial | no | no | UNSUPPORTED |
| REP/REPNE and ES/CS/SS/DS segment prefixes | yes | yes | yes | yes | automated / automated / duplicate-prefix rejection / partial | SUPPORTED |

Canonical choices: `XCHG AX, r16` emits `90+rw`; ordinary register exchange
emits `86/87 /r`. The encoder emits segment prefix before repeat prefix. The
decoder preserves both prefix bytes and rejects duplicate segment or repeat
prefixes.

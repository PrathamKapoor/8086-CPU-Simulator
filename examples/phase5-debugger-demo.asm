; Phase 5 CI smoke program: a small loop, a memory write/read, and a
; CALL/RET subroutine, deterministic and self-contained.
MOV SP, 0100H
MOV CX, 0003H
MOV AX, 0000H
back: INC AX
LOOP back
MOV BX, 0020H
MOV WORD [BX], 1234H
CALL double_bx
HLT
double_bx: SHL BX, 1
RET

; Demo 01: Basic Data Transfer
; Demonstrates: MOV reg,reg | MOV reg,imm | MOV reg,[addr] | XCHG | PUSH/POP

MOV AX, 0x1234
MOV BX, 0x5678
MOV CX, AX
MOV DX, BX

MOV [0x100], AX
MOV [0x102], BX
LOAD SI, [0x100]
LOAD DI, [0x102]

MOV AH, 0xAA
MOV AL, 0xBB

XCHG AX, BX

PUSH AX
PUSH BX
POP CX
POP DX

HLT

; Demo 07: Segment Addressing
; Demonstrates: DS, SS, ES segment registers

MOV AX, 0x1000
MOV DS, AX
MOV SS, AX
MOV ES, AX

MOV AX, 0xABCD
MOV [0x200], AX

PUSH 0x1234
POP BX

HLT

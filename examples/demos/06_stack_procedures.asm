; Demo 06: Stack and Procedures
; Demonstrates: PUSH/POP | CALL/RET

MOV AX, 10
CALL 6
ADD AX, 100
HLT

PUSH AX
MOV AX, 42
POP BX
RET

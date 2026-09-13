; String workload (simulated MOVSB loop with small string)
MOV AX, 0x0100
MOV BX, 0x0200
MOV CX, 5
CLD
STRING_LOOP:
MOV AL, [AX]
MOV [BX], AL
INC AX
INC BX
DEC CX
JNZ STRING_LOOP
HLT

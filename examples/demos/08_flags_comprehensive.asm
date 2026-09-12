; Demo 08: Comprehensive Flag Behavior
; Demonstrates: All 9 flags: CF, PF, AF, ZF, SF, TF, IF, DF, OF

MOV AX, 0xFFFF
ADD AX, 1

MOV AX, 5
SUB AX, 5

MOV AX, 0x8000
ADD AX, 0

MOV AX, 0x7FFF
ADD AX, 1

CLD
STD
CLI
STI

STC
CLC
CMC

HLT

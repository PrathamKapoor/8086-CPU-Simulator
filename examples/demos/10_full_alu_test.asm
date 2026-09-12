; Demo 10: Full ALU Test
; Comprehensive test of all ALU operations and flags

; --- ADD ---
MOV AX, 0x1234
MOV BX, 0x5678
ADD AX, BX       ; AX = 0x68AC

; --- SUB ---
SUB AX, 0x1111   ; AX = 0x579B

; --- ADC ---
STC
MOV AX, 0xAAAA
ADC AX, 0x5555   ; AX = 0xFFFF, CF=1

; --- SBB ---
STC
MOV AX, 0
SBB AX, 0        ; AX = 0xFFFF

; --- AND/OR/XOR ---
MOV AX, 0xF0F0
AND AX, 0xFF00   ; AX = 0xF000
OR  AX, 0x00FF   ; AX = 0xF0FF
XOR AX, 0xFFFF   ; AX = 0x0F00

; --- NOT ---
NOT AX            ; AX = 0xF0FF

; --- INC/DEC ---
MOV AX, 100
INC AX            ; AX = 101
DEC AX            ; AX = 100

; --- NEG ---
MOV AX, 5
NEG AX            ; AX = -5 (0xFFFB)

; --- CMP ---
CMP AX, 0xFFFB   ; ZF=1

; --- SHL/SHR/SAR ---
MOV AX, 0x0001
SHL AX, 15        ; AX = 0x8000
SHR AX, 8         ; AX = 0x0080
SAR AX, 4         ; AX = 0x0008

; --- ROL/ROR ---
MOV AX, 0x8001
ROL AX, 1         ; AX = 0x0003
ROR AX, 1         ; AX = 0x8001

; --- MUL ---
MOV AX, 100
MOV BX, 100
MUL BX            ; DX:AX = 10000

; --- DIV ---
MOV DX, 0
MOV AX, 1000
MOV CX, 7
DIV CX            ; AX = 142, DX = 6

HLT
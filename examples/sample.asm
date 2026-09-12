; ============================================================================
; sample.asm - Sample Programs for 8086 CPU Simulator
; ============================================================================
; This file contains multiple sample programs separated by comments.
; Each program is self-contained and demonstrates different features.
; Copy any section into its own file to run individually.
; ============================================================================

; ============================================================================
; Program 1: Hello Registers
; Initialize and inspect general-purpose registers
; ============================================================================

MOV AX, 0x1234
MOV BX, 0x5678
MOV CX, 0x9ABC
MOV DX, 0xDEF0
HLT

; ============================================================================
; Program 2: Basic Arithmetic
; Perform addition and subtraction with flag checks
; ============================================================================

MOV AX, 1000
MOV BX, 250
ADD AX, BX       ; AX = 1250
SUB AX, 100      ; AX = 1150
CMP AX, 1150     ; ZF=1
HLT

; ============================================================================
; Program 3: Bitwise Manipulation
; Masking, setting, and clearing specific bits
; ============================================================================

MOV AX, 0xABCD

; Clear high nibble (AND with mask)
AND AX, 0x0FFF   ; AX = 0x0BCD

; Set bit 15 (OR with mask)
OR  AX, 0x8000   ; AX = 0x8BCD

; Toggle low byte (XOR with mask)
XOR AX, 0x00FF   ; AX = 0x8B32

; Test if bit 0 is set
TEST AX, 0x0001  ; ZF=0 (bit 0 is 0)

HLT

; ============================================================================
; Program 4: Loop - Count Down
; Count from 10 to 0 using LOOP instruction
; ============================================================================

MOV CX, 10
MOV AX, 0

COUNTDOWN:
    INC AX
    LOOP COUNTDOWN

; AX should be 10 when done
HLT

; ============================================================================
; Program 5: Nested Procedures
; Demonstrate CALL/RET with stack frame
; ============================================================================

; Main
MOV AX, 5
CALL DOUBLE_AND_ADD_10
HLT

; Procedure: doubles AX and adds 10
DOUBLE_AND_ADD_10:
    PUSH BX
    MOV BX, AX
    ADD AX, BX      ; AX = AX * 2
    ADD AX, 10      ; AX = AX * 2 + 10
    POP BX
    RET

; ============================================================================
; Program 6: Shift and Rotate Demo
; Visualize bit movement through register
; ============================================================================

; Shift left chain
MOV AX, 0x0001
SHL AX, 1         ; AX = 0x0002
SHL AX, 1         ; AX = 0x0004
SHL AX, 1         ; AX = 0x0008

; Rotate through 8 positions
MOV AX, 0x00FF
ROL AX, 4         ; AX = 0x0FF0
ROL AX, 4         ; AX = 0xFF00

; Arithmetic shift preserves sign
MOV AX, 0x8000
SAR AX, 4         ; AX = 0xF800 (sign extended)

HLT

; ============================================================================
; Program 7: Memory Block Copy
; Copy 8 bytes from one location to another
; ============================================================================

MOV AX, 0x1000
MOV DS, AX
MOV ES, AX

; Store source data
MOV BYTE [0x100], 0x41   ; 'A'
MOV BYTE [0x101], 0x42   ; 'B'
MOV BYTE [0x102], 0x43   ; 'C'
MOV BYTE [0x103], 0x44   ; 'D'
MOV BYTE [0x104], 0x45   ; 'E'
MOV BYTE [0x105], 0x46   ; 'F'
MOV BYTE [0x106], 0x47   ; 'G'
MOV BYTE [0x107], 0x48   ; 'H'

; Copy using MOVSB (simplified)
MOV SI, 0x100
MOV DI, 0x200
MOVSB
MOVSB
MOVSB
MOVSB
MOVSB
MOVSB
MOVSB
MOVSB

HLT

; ============================================================================
; Program 8: Fibonacci (10 numbers)
; Compute and store first 10 Fibonacci numbers
; ============================================================================

MOV AX, 0
MOV BX, 1
MOV CX, 8
MOV SI, 0x500

; Store F(0) and F(1)
MOV [SI], AX
INC SI
INC SI
MOV [SI], BX
INC SI
INC SI

FIB:
    MOV DX, AX
    ADD DX, BX
    MOV [SI], DX
    INC SI
    INC SI
    MOV AX, BX
    MOV BX, DX
    LOOP FIB

HLT

; ============================================================================
; Program 9: Signed vs Unsigned Arithmetic
; Show difference between MUL/IMUL and DIV/IDIV
; ============================================================================

; Unsigned: 255 * 2 = 510
MOV AX, 255
MOV BX, 2
MUL BX           ; DX:AX = 510

; Signed: -1 * 2 = -2
MOV AX, 0xFFFF   ; -1
MOV BX, 2
IMUL BX          ; DX:AX = -2 (0xFFFE)

; Unsigned: 100 / 7 = 14 remainder 2
MOV DX, 0
MOV AX, 100
MOV CX, 7
DIV CX           ; AX=14, DX=2

; Signed: -100 / 7 = -14 remainder -2
MOV DX, 0xFFFF   ; -1
MOV AX, 0xFF9C   ; -100
MOV CX, 7
IDIV CX          ; AX=-14, DX=-2

HLT

; ============================================================================
; Program 10: Complex Calculator
; Compute (A * B + C) / D where A=10, B=20, C=30, D=7
; ============================================================================

MOV AX, 10
MOV BX, 20
MUL BX           ; DX:AX = 200 (result in AX since < 65535)

MOV CX, 30
ADD AX, CX       ; AX = 230

MOV CX, 7
MOV DX, 0
DIV CX           ; AX = 32, DX = 6

; Store results
MOV [0x600], AX  ; quotient
MOV [0x602], DX  ; remainder

HLT
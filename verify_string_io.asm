; ============================================================================
; verify_string_io.asm — Test MOVSB, LODSB, STOSB, CMPSB and basic IN/OUT
; ============================================================================

; Initialize segments (use 0 so physical = offset)
MOV AX, 0
MOV DS, AX
MOV ES, AX

; Set up a small array at byte address 0x0100
MOV SI, 0x0100
MOV AL, 0x41        ; 'A'
MOV [SI], AL
INC SI
MOV AL, 0x42        ; 'B'
MOV [SI], AL
INC SI
MOV AL, 0x43        ; 'C'
MOV [SI], AL

; ---- MOVSB: copy 3 bytes from 0x0100 -> 0x0200 ----
MOV SI, 0x0100
MOV DI, 0x0200
MOVSB
MOVSB
MOVSB

; ---- LODSB: load byte from 0x0100 into AL ----
MOV SI, 0x0100
LODSB              ; AL should be 0x41

; ---- STOSB: store AL to 0x0300 ----
MOV DI, 0x0300
STOSB              ; writes AL (0x41) to 0x0300

; ---- CMPSB: compare byte at 0x0100 with byte at 0x0100 ----
MOV SI, 0x0100
MOV DI, 0x0100
CMPSB              ; should set ZF (equal bytes)

; ---- Basic IN/OUT test ----
OUT 0x10, AL       ; write AL to simulated I/O port 0x10
IN AL, 0x10        ; read back into AL

HLT

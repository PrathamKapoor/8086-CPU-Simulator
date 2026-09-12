; Demo 11: Complex Program - Sum of Array
; Calculates sum of 5 words stored in memory

; Initialize array at [0x200]
MOV AX, 0x10
MOV DS, AX
MOV WORD [0x200], 10
MOV WORD [0x202], 20
MOV WORD [0x204], 30
MOV WORD [0x206], 40
MOV WORD [0x208], 50

; Sum = 0
MOV AX, 0
MOV CX, 5
MOV SI, 0x200

; Loop: add each element
LOOP_START:
    LOAD BX, [SI]
    ADD AX, BX
    INC SI
    INC SI          ; 2 bytes per word
    LOOP LOOP_START

; Store result at [0x210]
MOV [0x210], AX    ; AX = 150

HLT
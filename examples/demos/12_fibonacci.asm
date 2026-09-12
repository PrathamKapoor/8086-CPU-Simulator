; Demo 12: Fibonacci Sequence
; Computes first 10 Fibonacci numbers

MOV AX, 0
MOV BX, 1
MOV CX, 8         ; 8 iterations (we already have F(0)=0, F(1)=1)
MOV SI, 0x300     ; store starting at 0x300

; Store first two
MOV [SI], AX
INC SI
INC SI
MOV [SI], BX
INC SI
INC SI

FIB_LOOP:
    MOV DX, AX
    ADD DX, BX     ; DX = AX + BX (next fib)
    MOV [SI], DX   ; store it
    INC SI
    INC SI
    MOV AX, BX     ; shift: AX = old BX
    MOV BX, DX     ; BX = new fib
    LOOP FIB_LOOP

HLT
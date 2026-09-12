; Demo 09: String Operations (simplified)
; Demonstrates: MOVSB/MOVSW | CMPSB/CMPSW | SCASB/SCASW
;               LODSB/LODSW | STOSB/STOSW

; Set up source and destination
MOV AX, 0x1000
MOV DS, AX
MOV ES, AX

; Store values
MOV AX, 0x4142    ; "BA"
STOSB             ; store AL at ES:DI, DI++
STOSB             ; store AL again

; Load values
LODSB             ; load DS:SI into AL, SI++

; String operations (simplified single-step)
MOVSB
MOVSW
CMPSB
SCASB

HLT
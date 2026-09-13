; Branch-heavy loop workload
MOV AX, 0
MOV CX, 10
LABEL:
ADD AX, 1
CMP AX, CX
JZ END
JMP LABEL
END:
HLT

package instruction;

/**
 * InstructionFormat — operand layout of an instruction.
 * Expanded to cover all 8086 addressing modes.
 */
public enum InstructionFormat {
    // No operands
    NO_OPERAND,         // NOP, HLT, RET, IRET, CLC, STC, etc.

    // Register-only
    REG_ONLY,           // INC AX, DEC AX, NOT AX, PUSH AX, POP AX
    REG_REG,            // ADD AX, BX — reg to reg
    REG_IMM,            // MOV AX, 5 — register with immediate
    REG_IMM8,           // MOV AL, 5 — register with byte immediate
    REG_REG_IMM,        // SHL AX, 1 — register, register/immediate count

    // Memory addressing
    REG_ADDR,           // LOAD AX, [100] — direct addressing
    REG_REG_INDIRECT,   // LOAD AX, [BX] — register indirect
    REG_REG_INDIRECT_DISP, // LOAD AX, [BX+10] — register indirect + displacement
    REG_BASE_INDEX,     // MOV AX, [BX+SI] — base + index
    REG_BASE_INDEX_DISP,// MOV AX, [BX+SI+10] — base + index + displacement

    // Store to memory
    ADDR_REG,           // STORE [100], AX — direct addressing
    REG_INDIRECT_REG,   // STORE [BX], AX — register indirect
    REG_INDIRECT_DISP_REG, // STORE [BX+10], AX — register indirect + displacement
    BASE_INDEX_REG,     // STORE [BX+SI], AX — base + index
    BASE_INDEX_DISP_REG,// STORE [BX+SI+10], AX — base + index + displacement

    // Immediate to memory
    ADDR_IMM,           // ADD WORD [100], 5
    REG_INDIRECT_IMM,   // ADD WORD [BX], 5

    // Branch
    ADDR_ONLY,          // JMP 5, CALL 5
    REL_ONLY,           // JMP SHORT label, Jcc label

    // Fixed operands
    FIXED_AX_IMM,       // IN AX, imm8 / OUT imm8, AX
    FIXED_AL_IMM,       // IN AL, imm8 / OUT imm8, AL
    FIXED_AX_DX,        // IN AX, DX / OUT DX, AX
    FIXED_AL_DX,        // IN AL, DX / OUT DX, AL

    // String operations
    STRING_ONLY,        // MOVSB, CMPSB, LODSB, etc. — no explicit operands

    // Interrupt
    IMM_ONLY,           // INT 21h

    // Segment register operations
    SEG_REG_ONLY,       // PUSH DS, POP ES
    SEG_REG,            // MOV DS, AX (segment register operations)
    REG_SEG,            // MOV DS, AX (same as SEG_REG)
    SEG_REG_REG,        // LDS AX, [100] — load far pointer
}

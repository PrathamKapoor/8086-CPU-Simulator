package microoperation;

/**
 * MicroOperationType — categorises every possible RTL-level data transfer or computation.
 * One type maps to exactly one kind of register/bus/memory action.
 */
public enum MicroOperationType {
    // ---- Fetch phase ----
    MAR_LOAD_PC,        // MAR <- PC
    MDR_LOAD_MEMORY,    // MDR <- Memory[MAR]
    IR_LOAD_MDR,        // IR <- MDR  +  PC <- PC+1

    // ---- Decode phase ----
    DECODE,             // Decode IR -> opcode + operands

    // ---- Register transfers ----
    MAR_LOAD_ADDR,      // MAR <- literal address
    MDR_LOAD_REG,       // MDR <- Register[name]
    MDR_LOAD_IMM,       // MDR <- immediate value
    REG_LOAD_MDR,       // Register[name] <- MDR
    REG_LOAD_IMM,       // Register[name] <- immediate value
    REG_LOAD_REG,       // Register[dst] <- Register[src]
    MEMORY_WRITE_MDR,   // Memory[MAR] <- MDR
    PC_LOAD_ADDR,       // PC <- address (JMP / JZ / CALL)
    SP_DECREMENT,       // SP <- SP - 2 (PUSH)
    SP_INCREMENT,       // SP <- SP + 2 (POP)
    REG_LOAD_REG_BYTE,  // byte register <- byte register
    REG_LOAD_IMM_BYTE,  // byte register <- immediate

    // ---- Effective address computation ----
    EA_LOAD_REG_IND,    // MAR <- Register[base] + Register[index] + disp
    EA_LOAD_BASE,       // MAR <- Register[base] + disp
    EA_LOAD_INDEX,      // MAR <- Register[index] + disp

    // ---- ALU operations ----
    ALU_ADD,
    ALU_SUB,
    ALU_ADC,
    ALU_SBB,
    ALU_INC,
    ALU_DEC,
    ALU_NEG,
    ALU_AND,
    ALU_OR,
    ALU_XOR,
    ALU_NOT,
    ALU_CMP,
    ALU_TEST,
    ALU_MUL,
    ALU_IMUL,
    ALU_DIV,
    ALU_IDIV,
    ALU_SHL,
    ALU_SHR,
    ALU_SAR,
    ALU_ROL,
    ALU_ROR,
    ALU_RCL,
    ALU_RCR,
    ALU_IMM,            // ALU with immediate operand
    ALU_ADJUST,         // BCD/ASCII adjust: AAA, AAS, AAM, AAD

    // ---- Control flow ----
    HALT,
    NOP_OP,
    INT_OP,
    IRET_OP,

    // ---- I/O ----
    IO_READ_BYTE,       // IN AL, port
    IO_READ_WORD,       // IN AX, port
    IO_WRITE_BYTE,      // OUT port, AL
    IO_WRITE_WORD,      // OUT port, AX

    // ---- String operations ----
    STRING_MOVS,        // MOVSB/MOVSW
    STRING_CMPS,        // CMPSB/CMPSW
    STRING_SCAS,        // SCASB/SCASW
    STRING_LODS,        // LODSB/LODSW
    STRING_STOS,        // STOSB/STOSW

    // ---- Memory reads ----
    MEM_READ,           // MEM/BUS read: AL <- Memory[...] (XLAT table lookup)

    // ---- Segment operations ----
    PUSH_SEG,           // PUSH seg_reg
    POP_SEG,            // POP seg_reg
    LOAD_SEG,           // MOV seg, reg/mem

    // ---- Flag operations ----
    FLAGS_LOAD,         // Load flags from AH (LAHF) / Store AH to flags (SAHF)
    FLAGS_CLEAR_CF,     // CLC
    FLAGS_SET_CF,       // STC
    FLAGS_COMPLEMENT_CF,// CMC
    FLAGS_CLEAR_DF,     // CLD
    FLAGS_SET_DF,       // STD
    FLAGS_CLEAR_IF,     // CLI
    FLAGS_SET_IF,       // STI
    PUSH_FLAGS,         // PUSHF
    POP_FLAGS,          // POPF
}

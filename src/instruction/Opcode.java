package instruction;

/**
 * Opcode — all supported 8086 instruction types, organized by architectural category.
 */
public enum Opcode {
    // ===== Data Transfer =====
    MOV,        // MOV dst, src — Move
    LOAD,       // LOAD reg, [addr] — alias for MOV reg, [addr]
    STORE,      // STORE [addr], reg — alias for MOV [addr], reg
    PUSH,       // PUSH src — Push onto stack
    POP,        // POP dst — Pop from stack
    XCHG,       // XCHG dst, src — Exchange
    IN,         // IN AL/AX, port — Input from port
    OUT,        // OUT port, AL/AX — Output to port
    XLAT,       // XLAT — Table lookup translation
    LEA,        // LEA reg, mem — Load effective address
    LDS,        // LDS reg, mem — Load DS:mem
    LES,        // LES reg, mem — Load ES:mem
    LAHF,       // LAHF — Load AH from flags
    SAHF,       // SAHF — Store AH into flags
    PUSHF,      // PUSHF — Push flags
    POPF,       // POPF — Pop flags

    // ===== Arithmetic =====
    ADD,        // ADD dst, src — Add
    ADC,        // ADC dst, src — Add with carry
    INC,        // INC dst — Increment
    AAA,        // AAA — ASCII adjust for addition
    DAA,        // DAA — Decimal adjust for addition
    SUB,        // SUB dst, src — Subtract
    SBB,        // SBB dst, src — Subtract with borrow
    DEC,        // DEC dst — Decrement
    NEG,        // NEG dst — Two's complement negation
    CMP,        // CMP dst, src — Compare
    AAS,        // AAS — ASCII adjust for subtraction
    DAS,        // DAS — Decimal adjust for subtraction
    MUL,        // MUL src — Unsigned multiply (AL/AX × src)
    IMUL,       // IMUL src — Signed multiply
    AAM,        // AAM — ASCII adjust for multiplication
    DIV,        // DIV src — Unsigned divide
    IDIV,       // IDIV src — Signed divide
    AAD,        // AAD — ASCII adjust for division
    CBW,        // CBW — Convert byte to word
    CWD,        // CWD — Convert word to doubleword

    // ===== Logic / Bit =====
    AND,        // AND dst, src — Logical AND
    TEST,       // TEST dst, src — Logical AND (result discarded, flags set)
    OR,         // OR dst, src — Logical OR
    XOR,        // XOR dst, src — Logical XOR
    NOT,        // NOT dst — One's complement

    // ===== Shifts / Rotates =====
    SHL_SAL,    // SHL/SAL dst, count — Shift left / Shift arithmetic left
    SHR,        // SHR dst, count — Shift right logical
    SAR,        // SAR dst, count — Shift right arithmetic
    ROL,        // ROL dst, count — Rotate left
    ROR,        // ROR dst, count — Rotate right
    RCL,        // RCL dst, count — Rotate through carry left
    RCR,        // RCR dst, count — Rotate through carry right

    // ===== Control Transfer =====
    JMP,        // JMP dst — Unconditional jump
    CALL,       // CALL dst — Call procedure
    RET,        // RET — Return from procedure
    RETF,       // RETF — Far return
    JZ_JE,      // JZ/JE — Jump if equal (ZF=1)
    JNZ_JNE,    // JNZ/JNE — Jump if not equal (ZF=0)
    JC_JB,      // JC/JB — Jump if carry / below (CF=1)
    JNC_JNB,    // JNC/JNB — Jump if not carry / not below (CF=0)
    JO,         // JO — Jump if overflow (OF=1)
    JNO,        // JNO — Jump if not overflow (OF=0)
    JS,         // JS — Jump if sign negative (SF=1)
    JNS,        // JNS — Jump if sign positive (SF=0)
    JP_JPE,     // JP/JPE — Jump if parity even (PF=1)
    JNP_JPO,    // JNP/JPO — Jump if parity odd (PF=0)
    JL_JNGE,    // JL/JNGE — Jump if less (SF!=OF)
    JNL_JGE,    // JNL/JGE — Jump if not less (SF=OF)
    JLE_JNG,    // JLE/JNG — Jump if less or equal (ZF=1 or SF!=OF)
    JNLE_JG,    // JNLE/JG — Jump if not less or equal (ZF=0 and SF=OF)
    JB_JNAE,    // JB/JNAE — Jump if below (CF=1) — same as JC
    JBE_JNA,    // JBE/JNA — Jump if below or equal (CF=1 or ZF=1)
    JNBE_JA,    // JNBE/JA — Jump if not below or equal (CF=0 and ZF=0)
    LOOP,       // LOOP — Decrement CX, jump if CX!=0
    LOOPZ,      // LOOPZ/LOOPE — Decrement CX, jump if CX!=0 and ZF=1
    LOOPNZ,     // LOOPNZ/LOOPNE — Decrement CX, jump if CX!=0 and ZF=0
    JCXZ,       // JCXZ — Jump if CX=0

    // ===== String Operations =====
    MOVSB,      // MOVSB — Move string byte
    MOVSW,      // MOVSW — Move string word
    CMPSB,      // CMPSB — Compare string byte
    CMPSW,      // CMPSW — Compare string word
    SCASB,      // SCASB — Scan string byte
    SCASW,      // SCASW — Scan string word
    LODSB,      // LODSB — Load string byte
    LODSW,      // LODSW — Load string word
    STOSB,      // STOSB — Store string byte
    STOSW,      // STOSW — Store string word
    REP,        // REP — Repeat prefix (with MOVSB/MOVSW/STOSB/STOSW/LODSB/LODSW)
    REPE,       // REPE/REPZ — Repeat while equal (with CMPSB/CMPSW/SCASB/SCASW)
    REPNE,      // REPNE/REPNZ — Repeat while not equal

    // ===== Processor Control =====
    CLC,        // CLC — Clear carry flag
    STC,        // STC — Set carry flag
    CMC,        // CMC — Complement carry flag
    CLD,        // CLD — Clear direction flag
    STD,        // STD — Set direction flag
    CLI,        // CLI — Clear interrupt flag
    STI,        // STI — Set interrupt flag
    HLT,        // HLT — Halt processor
    NOP,        // NOP — No operation
    WAIT,       // WAIT — Wait for external event
    LOCK,       // LOCK — Bus lock prefix
    ESC,        // ESC — Escape to coprocessor

    // ===== Interrupts =====
    INT,        // INT n — Software interrupt
    INTO,       // INTO — Interrupt on overflow
    IRET        // IRET — Interrupt return
}

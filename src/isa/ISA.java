package isa;

import instruction.Opcode;

import java.util.*;

/**
 * ISA — the complete 8086 instruction set architecture definition.
 *
 * Every instruction is registered with:
 * - Valid operand types and widths
 * - Encoding format
 * - Affected flags
 * - Possible exceptions
 * - Description
 * - Approximate cycle count
 *
 * The ISA validates whether a parsed instruction is architecturally legal.
 */
public class ISA {

    private static final ISA INSTANCE = new ISA();
    public static ISA get() { return INSTANCE; }

    // Primary opcode -> list of possible definitions (different operand forms)
    private final Map<Opcode, List<InstructionDefinition>> definitions = new EnumMap<>(Opcode.class);

    // Legal operand combinations per opcode (dst, src) pairs
    private final Map<Opcode, Set<List<OperandType>>> legalOperands = new EnumMap<>(Opcode.class);

    // Segment registers that CANNOT be destination of MOV
    private static final Set<String> NON_MODIFIABLE_SEG = Set.of("CS");

    // All general-purpose 16-bit registers
    public static final Set<String> GPR16 = Set.of("AX","BX","CX","DX","SP","BP","SI","DI");

    // All 8-bit registers
    public static final Set<String> GPR8 = Set.of("AH","AL","BH","BL","CH","CL","DH","DL");

    // All segment registers
    public static final Set<String> SEG_REGS = Set.of("CS","DS","SS","ES");

    // Base registers (for addressing)
    public static final Set<String> BASE_REGS = Set.of("BX","BP");

    // Index registers (for addressing)
    public static final Set<String> INDEX_REGS = Set.of("SI","DI");

    private ISA() {
        initDefinitions();
        initLegalOperands();
    }

    // ========================================================================
    //  ISA Definitions — every legal instruction form
    // ========================================================================

    private void initDefinitions() {
        // ===== Data Transfer =====
        def(Opcode.MOV, "MOV", 0x88, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Move data", 2);
        def(Opcode.MOV, "MOV", 0xB8, EncodingFormat.REG_IMM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Move immediate to register", 2);
        def(Opcode.MOV, "MOV", 0xC6, EncodingFormat.MEM_IMM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Move immediate to memory", 3);
        def(Opcode.PUSH, "PUSH", 0x50, EncodingFormat.REG_OPCODE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_OVERFLOW, "Push register", 10);
        def(Opcode.PUSH, "PUSH", 0xFF, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_OVERFLOW, "Push memory/rm16", 10);
        def(Opcode.PUSH, "PUSH", 0x68, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_OVERFLOW, "Push immediate", 10);
        def(Opcode.POP, "POP", 0x58, EncodingFormat.REG_OPCODE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_UNDERFLOW, "Pop to register", 8);
        def(Opcode.POP, "POP", 0x8F, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_UNDERFLOW, "Pop to memory/rm16", 8);
        def(Opcode.XCHG, "XCHG", 0x86, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Exchange", 3);
        def(Opcode.LEA, "LEA", 0x8D, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Load effective address", 2);
        def(Opcode.LDS, "LDS", 0xC5, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Load DS:mem", 16);
        def(Opcode.LES, "LES", 0xC4, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Load ES:mem", 16);
        def(Opcode.LAHF, "LAHF", 0x9F, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{}, ExceptionType.NONE, "Load AH from flags", 2);
        def(Opcode.SAHF, "SAHF", 0x9E, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY}, ExceptionType.NONE, "Store AH to flags", 2);
        def(Opcode.PUSHF, "PUSHF", 0x9C, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_OVERFLOW, "Push flags", 10);
        def(Opcode.POPF, "POPF", 0x9D, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY,
                         FlagEffect.DF_MODIFY, FlagEffect.IF_MODIFY, FlagEffect.TF_MODIFY},
            ExceptionType.NONE, "Pop flags", 8);
        def(Opcode.IN, "IN", 0xE4, EncodingFormat.FIXED_AL_IMM, OperandWidth.BYTE,
            new FlagEffect[]{}, ExceptionType.NONE, "Input byte from port", 10);
        def(Opcode.IN, "IN", 0xEC, EncodingFormat.FIXED_AL_DX, OperandWidth.BYTE,
            new FlagEffect[]{}, ExceptionType.NONE, "Input byte from DX port", 8);
        def(Opcode.OUT, "OUT", 0xE6, EncodingFormat.FIXED_AL_IMM, OperandWidth.BYTE,
            new FlagEffect[]{}, ExceptionType.NONE, "Output byte to port", 10);
        def(Opcode.OUT, "OUT", 0xEE, EncodingFormat.FIXED_AL_DX, OperandWidth.BYTE,
            new FlagEffect[]{}, ExceptionType.NONE, "Output byte to DX port", 8);
        def(Opcode.XLAT, "XLAT", 0xD7, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{}, ExceptionType.NONE, "Table lookup translation", 11);

        // ===== Arithmetic =====
        def(Opcode.ADD, "ADD", 0x00, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Add", 3);
        def(Opcode.ADD, "ADD", 0x04, EncodingFormat.FIXED_AX_IMM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Add immediate to AL/AX", 2);
        def(Opcode.ADC, "ADC", 0x10, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Add with carry", 3);
        def(Opcode.INC, "INC", 0x40, EncodingFormat.REG_OPCODE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY, FlagEffect.ZF_MODIFY,
                         FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            // Note: INC does NOT modify CF
            ExceptionType.NONE, "Increment", 2);
        def(Opcode.SUB, "SUB", 0x28, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Subtract", 3);
        def(Opcode.SUB, "SUB", 0x2C, EncodingFormat.FIXED_AX_IMM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Subtract immediate from AL/AX", 2);
        def(Opcode.SBB, "SBB", 0x18, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Subtract with borrow", 3);
        def(Opcode.DEC, "DEC", 0x48, EncodingFormat.REG_OPCODE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY, FlagEffect.ZF_MODIFY,
                         FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Decrement", 2);
        def(Opcode.NEG, "NEG", 0xF6, OpcodeExtension.NONE, 3, EncodingFormat.MODRM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Two's complement negation", 3);
        def(Opcode.CMP, "CMP", 0x38, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Compare", 3);
        def(Opcode.CMP, "CMP", 0x3C, EncodingFormat.FIXED_AX_IMM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Compare AL/AX with immediate", 2);
        def(Opcode.MUL, "MUL", 0xF6, OpcodeExtension.NONE, 4, EncodingFormat.MODRM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Unsigned multiply", 130);
        def(Opcode.IMUL, "IMUL", 0xF6, OpcodeExtension.NONE, 5, EncodingFormat.MODRM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Signed multiply", 130);
        def(Opcode.DIV, "DIV", 0xF6, OpcodeExtension.NONE, 6, EncodingFormat.MODRM, OperandWidth.WORD,
            new FlagEffect[]{},
            ExceptionType.DIVIDE_BY_ZERO, "Unsigned divide", 160);
        def(Opcode.IDIV, "IDIV", 0xF6, OpcodeExtension.NONE, 7, EncodingFormat.MODRM, OperandWidth.WORD,
            new FlagEffect[]{},
            ExceptionType.DIVIDE_BY_ZERO, "Signed divide", 160);
        def(Opcode.AAA, "AAA", 0x37, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.AF_MODIFY},
            ExceptionType.NONE, "ASCII adjust for addition", 4);
        def(Opcode.DAA, "DAA", 0x27, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.AF_MODIFY, FlagEffect.ZF_MODIFY,
                         FlagEffect.SF_MODIFY, FlagEffect.PF_MODIFY},
            ExceptionType.NONE, "Decimal adjust for addition", 4);
        def(Opcode.AAS, "AAS", 0x3F, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.AF_MODIFY},
            ExceptionType.NONE, "ASCII adjust for subtraction", 4);
        def(Opcode.DAS, "DAS", 0x2F, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.AF_MODIFY, FlagEffect.ZF_MODIFY,
                         FlagEffect.SF_MODIFY, FlagEffect.PF_MODIFY},
            ExceptionType.NONE, "Decimal adjust for subtraction", 4);
        def(Opcode.AAM, "AAM", 0xD4, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.PF_MODIFY, FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY},
            ExceptionType.DIVIDE_BY_ZERO, "ASCII adjust for multiplication", 8);
        def(Opcode.AAD, "AAD", 0xD5, EncodingFormat.SIMPLE, OperandWidth.BYTE,
            new FlagEffect[]{FlagEffect.PF_MODIFY, FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY},
            ExceptionType.NONE, "ASCII adjust for division", 8);
        def(Opcode.CBW, "CBW", 0x98, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Convert byte to word", 2);
        def(Opcode.CWD, "CWD", 0x99, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Convert word to doubleword", 2);

        // ===== Logic =====
        def(Opcode.AND, "AND", 0x20, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_CLEAR, FlagEffect.OF_CLEAR, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.AF_MODIFY},
            ExceptionType.NONE, "Logical AND", 3);
        def(Opcode.TEST, "TEST", 0x84, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_CLEAR, FlagEffect.OF_CLEAR, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.AF_MODIFY},
            ExceptionType.NONE, "Test (AND without storing result)", 2);
        def(Opcode.OR, "OR", 0x08, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_CLEAR, FlagEffect.OF_CLEAR, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.AF_MODIFY},
            ExceptionType.NONE, "Logical OR", 3);
        def(Opcode.XOR, "XOR", 0x30, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_CLEAR, FlagEffect.OF_CLEAR, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.AF_MODIFY},
            ExceptionType.NONE, "Logical XOR", 3);
        def(Opcode.NOT, "NOT", 0xF6, OpcodeExtension.NONE, 2, EncodingFormat.MODRM, OperandWidth.WORD,
            new FlagEffect[]{},
            ExceptionType.NONE, "One's complement", 3);

        // ===== Shifts/Rotates =====
        def(Opcode.SHL_SAL, "SHL", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY},
            ExceptionType.NONE, "Shift left", 2);
        def(Opcode.SHR, "SHR", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY},
            ExceptionType.NONE, "Shift right logical", 2);
        def(Opcode.SAR, "SAR", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY, FlagEffect.PF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY},
            ExceptionType.NONE, "Shift right arithmetic", 2);
        def(Opcode.ROL, "ROL", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Rotate left", 2);
        def(Opcode.ROR, "ROR", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Rotate right", 2);
        def(Opcode.RCL, "RCL", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Rotate through carry left", 2);
        def(Opcode.RCR, "RCR", 0xD0, EncodingFormat.REG_MEM, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.OF_MODIFY},
            ExceptionType.NONE, "Rotate through carry right", 2);

        // ===== Control Transfer =====
        def(Opcode.JMP, "JMP", 0xE9, EncodingFormat.NEAR_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Near jump", 15);
        def(Opcode.JMP, "JMP", 0xEB, EncodingFormat.SHORT_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Short jump", 15);
        def(Opcode.CALL, "CALL", 0xE8, EncodingFormat.NEAR_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_OVERFLOW, "Near call", 19);
        def(Opcode.RET, "RET", 0xC3, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_UNDERFLOW, "Near return", 16);
        def(Opcode.RETF, "RETF", 0xCB, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.STACK_UNDERFLOW, "Far return", 22);

        // Conditional jumps (all SHORT_JMP format)
        for (Opcode cc : new Opcode[]{Opcode.JZ_JE, Opcode.JNZ_JNE, Opcode.JC_JB, Opcode.JNC_JNB,
                Opcode.JO, Opcode.JNO, Opcode.JS, Opcode.JNS, Opcode.JP_JPE, Opcode.JNP_JPO,
                Opcode.JL_JNGE, Opcode.JNL_JGE, Opcode.JLE_JNG, Opcode.JNLE_JG,
                Opcode.JBE_JNA, Opcode.JNBE_JA}) {
            def(cc, cc.name(), 0x70, EncodingFormat.SHORT_JMP, OperandWidth.WORD,
                new FlagEffect[]{}, ExceptionType.NONE, "Conditional jump: " + cc, 4);
        }

        def(Opcode.LOOP, "LOOP", 0xE2, EncodingFormat.SHORT_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Loop while CX!=0", 5);
        def(Opcode.LOOPZ, "LOOPZ", 0xE1, EncodingFormat.SHORT_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Loop while CX!=0 and ZF=1", 5);
        def(Opcode.LOOPNZ, "LOOPNZ", 0xE0, EncodingFormat.SHORT_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Loop while CX!=0 and ZF=0", 5);
        def(Opcode.JCXZ, "JCXZ", 0xE3, EncodingFormat.SHORT_JMP, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Jump if CX=0", 6);

        // ===== Processor Control =====
        def(Opcode.NOP, "NOP", 0x90, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "No operation", 1);
        def(Opcode.HLT, "HLT", 0xF4, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{}, ExceptionType.NONE, "Halt processor", 2);
        def(Opcode.CLC, "CLC", 0xF8, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_CLEAR}, ExceptionType.NONE, "Clear carry", 2);
        def(Opcode.STC, "STC", 0xF9, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_SET}, ExceptionType.NONE, "Set carry", 2);
        def(Opcode.CMC, "CMC", 0xF5, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_COMPLEMENT}, ExceptionType.NONE, "Complement carry", 2);
        def(Opcode.CLD, "CLD", 0xFC, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.DF_CLEAR}, ExceptionType.NONE, "Clear direction", 2);
        def(Opcode.STD, "STD", 0xFD, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.DF_SET}, ExceptionType.NONE, "Set direction", 2);
        def(Opcode.CLI, "CLI", 0xFA, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.IF_CLEAR}, ExceptionType.NONE, "Clear interrupt enable", 2);
        def(Opcode.STI, "STI", 0xFB, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.IF_SET}, ExceptionType.NONE, "Set interrupt enable", 2);

        // ===== Interrupts =====
        def(Opcode.INT, "INT", 0xCD, EncodingFormat.INT_BYTE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.IF_CLEAR, FlagEffect.TF_CLEAR}, ExceptionType.NONE, "Software interrupt", 51);
        def(Opcode.INTO, "INTO", 0xCE, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.IF_CLEAR, FlagEffect.TF_CLEAR}, ExceptionType.NONE, "Interrupt on overflow", 53);
        def(Opcode.IRET, "IRET", 0xCF, EncodingFormat.SIMPLE, OperandWidth.WORD,
            new FlagEffect[]{FlagEffect.CF_MODIFY, FlagEffect.PF_MODIFY, FlagEffect.AF_MODIFY,
                         FlagEffect.ZF_MODIFY, FlagEffect.SF_MODIFY, FlagEffect.OF_MODIFY,
                         FlagEffect.DF_MODIFY, FlagEffect.IF_MODIFY, FlagEffect.TF_MODIFY},
            ExceptionType.NONE, "Interrupt return", 32);

        // ===== String Operations =====
        for (Opcode str : new Opcode[]{Opcode.MOVSB, Opcode.MOVSW, Opcode.CMPSB, Opcode.CMPSW,
                Opcode.SCASB, Opcode.SCASW, Opcode.LODSB, Opcode.LODSW, Opcode.STOSB, Opcode.STOSW}) {
            def(str, str.name(), 0xA4, EncodingFormat.STRING_OP, OperandWidth.WORD,
                new FlagEffect[]{}, ExceptionType.NONE, "String operation: " + str, 2);
        }
    }

    // ========================================================================
    //  Legal operand combinations
    // ========================================================================

    private void initLegalOperands() {
        // MOV: many legal forms
        putLegal(Opcode.MOV,
            List.of(OperandType.REG16, OperandType.REG16),        // MOV AX, BX
            List.of(OperandType.REG16, OperandType.MEM16),        // MOV AX, [100]
            List.of(OperandType.MEM16, OperandType.REG16),        // MOV [100], AX
            List.of(OperandType.REG16, OperandType.IMM16),        // MOV AX, 5
            List.of(OperandType.MEM16, OperandType.IMM16),        // MOV [100], 5
            List.of(OperandType.REG8, OperandType.REG8),          // MOV AH, BL
            List.of(OperandType.REG8, OperandType.MEM8),          // MOV AL, [100]
            List.of(OperandType.MEM8, OperandType.REG8),          // MOV [100], AL
            List.of(OperandType.REG8, OperandType.IMM8),          // MOV AL, 5
            List.of(OperandType.MEM8, OperandType.IMM8),          // MOV [100], 5
            // Segment register MOVs (restricted!)
            List.of(OperandType.SEG_REG, OperandType.REG16),      // MOV DS, AX (legal)
            List.of(OperandType.SEG_REG, OperandType.MEM16),      // MOV DS, [100] (legal)
            List.of(OperandType.REG16, OperandType.SEG_REG),      // MOV AX, DS (legal)
            List.of(OperandType.MEM16, OperandType.SEG_REG)       // MOV [100], DS (legal)
        );

        // ADD/SUB/AND/OR/XOR/CMP: reg,reg | reg,mem | mem,reg | reg,imm | mem,imm
        for (Opcode op : new Opcode[]{Opcode.ADD, Opcode.SUB, Opcode.AND, Opcode.OR, Opcode.XOR, Opcode.CMP}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.REG16),
                List.of(OperandType.REG16, OperandType.MEM16),
                List.of(OperandType.MEM16, OperandType.REG16),
                List.of(OperandType.REG16, OperandType.IMM16),
                List.of(OperandType.MEM16, OperandType.IMM16),
                List.of(OperandType.REG8, OperandType.REG8),
                List.of(OperandType.REG8, OperandType.MEM8),
                List.of(OperandType.MEM8, OperandType.REG8),
                List.of(OperandType.REG8, OperandType.IMM8),
                List.of(OperandType.MEM8, OperandType.IMM8)
            );
        }

        // ADC/SBB: same as ADD
        for (Opcode op : new Opcode[]{Opcode.ADC, Opcode.SBB}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.REG16),
                List.of(OperandType.REG16, OperandType.MEM16),
                List.of(OperandType.MEM16, OperandType.REG16),
                List.of(OperandType.REG16, OperandType.IMM16),
                List.of(OperandType.MEM16, OperandType.IMM16)
            );
        }

        // INC/DEC: reg16 | mem16
        for (Opcode op : new Opcode[]{Opcode.INC, Opcode.DEC}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.NONE),
                List.of(OperandType.MEM16, OperandType.NONE)
            );
        }

        // NEG/NOT: reg16 | mem16
        for (Opcode op : new Opcode[]{Opcode.NEG, Opcode.NOT}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.NONE),
                List.of(OperandType.MEM16, OperandType.NONE)
            );
        }

        // TEST: same as AND but doesn't store
        putLegal(Opcode.TEST,
            List.of(OperandType.REG16, OperandType.REG16),
            List.of(OperandType.REG16, OperandType.MEM16),
            List.of(OperandType.MEM16, OperandType.REG16),
            List.of(OperandType.REG16, OperandType.IMM16)
        );

        // MUL/IMUL/DIV/IDIV: reg16 | mem16
        for (Opcode op : new Opcode[]{Opcode.MUL, Opcode.IMUL, Opcode.DIV, Opcode.IDIV}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.NONE),
                List.of(OperandType.MEM16, OperandType.NONE)
            );
        }

        // Shifts: reg16, imm8 | reg16, CL | mem16, imm8 | mem16, CL
        for (Opcode op : new Opcode[]{Opcode.SHL_SAL, Opcode.SHR, Opcode.SAR, Opcode.ROL, Opcode.ROR, Opcode.RCL, Opcode.RCR}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.IMM8),
                List.of(OperandType.REG16, OperandType.FIXED_CL),
                List.of(OperandType.MEM16, OperandType.IMM8),
                List.of(OperandType.MEM16, OperandType.FIXED_CL)
            );
        }

        // PUSH/POP: reg16 | mem16 | seg_reg
        for (Opcode op : new Opcode[]{Opcode.PUSH, Opcode.POP}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.NONE),
                List.of(OperandType.MEM16, OperandType.NONE),
                List.of(OperandType.SEG_REG, OperandType.NONE)
            );
        }

        // XCHG: reg16, reg16 | reg16, mem16 | mem16, reg16
        putLegal(Opcode.XCHG,
            List.of(OperandType.REG16, OperandType.REG16),
            List.of(OperandType.REG16, OperandType.MEM16),
            List.of(OperandType.MEM16, OperandType.REG16)
        );

        // LEA/LDS/LES: reg16, mem16
        for (Opcode op : new Opcode[]{Opcode.LEA, Opcode.LDS, Opcode.LES}) {
            putLegal(op,
                List.of(OperandType.REG16, OperandType.MEM16)
            );
        }

        // JMP/CALL: addr16
        for (Opcode op : new Opcode[]{Opcode.JMP, Opcode.CALL}) {
            putLegal(op,
                List.of(OperandType.REL8, OperandType.NONE),
                List.of(OperandType.REL16, OperandType.NONE),
                List.of(OperandType.REG16, OperandType.NONE),
                List.of(OperandType.MEM16, OperandType.NONE)
            );
        }

        // Conditional jumps: REL8 only
        for (Opcode op : new Opcode[]{Opcode.JZ_JE, Opcode.JNZ_JNE, Opcode.JC_JB, Opcode.JNC_JNB,
                Opcode.JO, Opcode.JNO, Opcode.JS, Opcode.JNS, Opcode.JP_JPE, Opcode.JNP_JPO,
                Opcode.JL_JNGE, Opcode.JNL_JGE, Opcode.JLE_JNG, Opcode.JNLE_JG,
                Opcode.JBE_JNA, Opcode.JNBE_JA, Opcode.JCXZ}) {
            putLegal(op,
                List.of(OperandType.REL8, OperandType.NONE)
            );
        }

        // LOOP/LOOPZ/LOOPNZ: REL8
        for (Opcode op : new Opcode[]{Opcode.LOOP, Opcode.LOOPZ, Opcode.LOOPNZ}) {
            putLegal(op,
                List.of(OperandType.REL8, OperandType.NONE)
            );
        }

        // RET/RETF/IRET/HLT/NOP/CLC/STC/CMC/CLD/STD/CLI/STI: no operands
        for (Opcode op : new Opcode[]{Opcode.RET, Opcode.RETF, Opcode.IRET, Opcode.HLT, Opcode.NOP,
                Opcode.CLC, Opcode.STC, Opcode.CMC, Opcode.CLD, Opcode.STD, Opcode.CLI, Opcode.STI,
                Opcode.PUSHF, Opcode.POPF, Opcode.LAHF, Opcode.SAHF, Opcode.CBW, Opcode.CWD,
                Opcode.XLAT, Opcode.INTO, Opcode.WAIT, Opcode.LOCK, Opcode.ESC}) {
            putLegal(op,
                List.of(OperandType.NONE, OperandType.NONE)
            );
        }

        // INT: IMM8
        putLegal(Opcode.INT,
            List.of(OperandType.IMM8, OperandType.NONE)
        );

        // String ops: no operands
        for (Opcode op : new Opcode[]{Opcode.MOVSB, Opcode.MOVSW, Opcode.CMPSB, Opcode.CMPSW,
                Opcode.SCASB, Opcode.SCASW, Opcode.LODSB, Opcode.LODSW, Opcode.STOSB, Opcode.STOSW,
                Opcode.REP, Opcode.REPE, Opcode.REPNE}) {
            putLegal(op,
                List.of(OperandType.NONE, OperandType.NONE)
            );
        }
    }

    // ========================================================================
    //  Public API
    // ========================================================================

    /**
     * Get all definitions for an opcode.
     */
    public List<InstructionDefinition> getDefinitions(Opcode opcode) {
        return definitions.getOrDefault(opcode, List.of());
    }

    /**
     * Check if a specific (dst, src) combination is legal for an opcode.
     */
    public boolean isLegalOperands(Opcode opcode, OperandType dst, OperandType src) {
        Set<List<OperandType>> legal = legalOperands.get(opcode);
        if (legal == null) return false;
        return legal.contains(List.of(dst, src));
    }

    /**
     * Check if a MOV to a segment register is legal.
     * MOV CS, reg is illegal on 8086.
     */
    public boolean isLegalSegmentMov(String dstSeg, OperandType srcType) {
        if (dstSeg == null) return false;
        String upper = dstSeg.toUpperCase();
        if (NON_MODIFIABLE_SEG.contains(upper)) return false;
        return SEG_REGS.contains(upper);
    }

    /**
     * Get the default segment for a base register.
     * BX, SI, DI -> DS
     * BP -> SS
     */
    public static String getDefaultSegment(String baseReg) {
        if (baseReg == null) return "DS";
        return switch (baseReg.toUpperCase()) {
            case "BP" -> "SS";
            default -> "DS";
        };
    }

    /**
     * Get all valid register names (any type).
     */
    public static Set<String> allRegisters() {
        Set<String> all = new HashSet<>();
        all.addAll(GPR16);
        all.addAll(GPR8);
        all.addAll(SEG_REGS);
        return all;
    }

    /**
     * Check if a register name is valid (any type).
     */
    public static boolean isValidRegister(String name) {
        String upper = name.toUpperCase();
        return GPR16.contains(upper) || GPR8.contains(upper) || SEG_REGS.contains(upper);
    }

    /**
     * Check if a register name is a 16-bit general-purpose register.
     */
    public static boolean isGPR16(String name) {
        return GPR16.contains(name.toUpperCase());
    }

    /**
     * Check if a register name is an 8-bit register.
     */
    public static boolean isGPR8(String name) {
        return GPR8.contains(name.toUpperCase());
    }

    /**
     * Check if a register name is a segment register.
     */
    public static boolean isSegReg(String name) {
        return SEG_REGS.contains(name.toUpperCase());
    }

    // ========================================================================
    //  Implementation status — single source of truth for modeling fidelity.
    //  SUPPORTED: parses and executes with full 8086 architectural effect.
    //  PARTIAL:   parses and retires, but effect is scoped by the model
    //             (see note). The execution trace always states the scope,
    //             so a PARTIAL instruction never silently lies.
    //  Every Opcode enum value defaults to SUPPORTED; only the scoped
    //  instructions are listed here. There is intentionally no third state:
    //  anything the parser accepts must have a documented status.
    // ========================================================================

    public enum SupportStatus { SUPPORTED, PARTIAL }

    private static final Map<Opcode, String> PARTIAL_NOTES = new EnumMap<>(Opcode.class);
    static {
        PARTIAL_NOTES.put(Opcode.WAIT,
            "No x87 coprocessor modeled; retires with no effect.");
        PARTIAL_NOTES.put(Opcode.LOCK,
            "Single CPU, no external bus master; prefix retires with no effect.");
        PARTIAL_NOTES.put(Opcode.ESC,
            "No external coprocessor modeled; retires with no effect.");
        PARTIAL_NOTES.put(Opcode.REP,
            "Only meaningful as a prefix on a string instruction (REP MOVSB).");
        PARTIAL_NOTES.put(Opcode.REPE,
            "Only meaningful as a prefix on a string instruction (REPE CMPSB).");
        PARTIAL_NOTES.put(Opcode.REPNE,
            "Only meaningful as a prefix on a string instruction (REPNE SCASB).");
    }

    public static SupportStatus statusOf(Opcode op) {
        return PARTIAL_NOTES.containsKey(op) ? SupportStatus.PARTIAL : SupportStatus.SUPPORTED;
    }

    public static String statusNote(Opcode op) {
        return PARTIAL_NOTES.getOrDefault(op, "Full architectural effect.");
    }

    /** Opcodes whose execution effect is scoped (see statusNote). */
    public static Set<Opcode> partialOpcodes() {
        return Collections.unmodifiableSet(PARTIAL_NOTES.keySet());
    }

    private void def(Opcode op, String mnemonic, int opcodeByte, EncodingFormat enc,
                     OperandWidth w, FlagEffect[] flags, ExceptionType ex, String desc, int cycles) {
        def(op, mnemonic, opcodeByte, OpcodeExtension.NONE, 0, enc, w, flags, ex, desc, cycles);
    }

    private void def(Opcode op, String mnemonic, int opcodeByte, OpcodeExtension ext,
                     int opcodeGroup, EncodingFormat enc, OperandWidth w,
                     FlagEffect[] flags, ExceptionType ex, String desc, int cycles) {
        InstructionDefinition def = new InstructionDefinition.Builder(mnemonic)
            .opcode(opcodeGroup, opcodeByte)
            .ext(ext)
            .encoding(enc)
            .width(w)
            .flags(flags.length > 0 ? flags : new FlagEffect[]{})
            .exception(ex)
            .desc(desc)
            .cycles(cycles)
            .build();
        definitions.computeIfAbsent(op, k -> new ArrayList<>()).add(def);
    }

    private void putLegal(Opcode op, List<OperandType>... combos) {
        Set<List<OperandType>> set = new HashSet<>();
        for (List<OperandType> combo : combos) set.add(combo);
        legalOperands.put(op, set);
    }
}

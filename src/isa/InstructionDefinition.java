package isa;

import java.util.EnumSet;
import java.util.Set;

/**
 * InstructionDefinition — complete metadata for one instruction form.
 *
 * Each definition describes:
 * - What the instruction does
 * - What operands it accepts
 * - What encoding it uses
 * - What flags it affects
 * - What exceptions it can raise
 * - What micro-operations it generates
 *
 * One instruction (e.g. ADD) may have multiple definitions (ADD r/m16,r16 and ADD r/m16,imm16).
 */
public class InstructionDefinition {

    private final String mnemonic;
    private final int opcodeGroup;     // Primary opcode byte (high nibble group)
    private final int opcodeByte;      // Full primary opcode byte
    private final OpcodeExtension ext; // /digit extension (0-7 or NONE)
    private final OperandType dstType;
    private final OperandType srcType;
    private final OperandWidth width;
    private final EncodingFormat encoding;
    private final Set<FlagEffect> flagEffects;
    private final ExceptionType possibleException;
    private final String description;
    private final int baseCycles;      // Approximate cycle count

    private InstructionDefinition(Builder b) {
        this.mnemonic = b.mnemonic;
        this.opcodeGroup = b.opcodeGroup;
        this.opcodeByte = b.opcodeByte;
        this.ext = b.ext;
        this.dstType = b.dstType;
        this.srcType = b.srcType;
        this.width = b.width;
        this.encoding = b.encoding;
        this.flagEffects = b.flagEffects;
        this.possibleException = b.possibleException;
        this.description = b.description;
        this.baseCycles = b.baseCycles;
    }

    // ---- Accessors ----
    public String getMnemonic() { return mnemonic; }
    public int getOpcodeByte() { return opcodeByte; }
    public OpcodeExtension getExt() { return ext; }
    public OperandType getDstType() { return dstType; }
    public OperandType getSrcType() { return srcType; }
    public OperandWidth getWidth() { return width; }
    public EncodingFormat getEncoding() { return encoding; }
    public Set<FlagEffect> getFlagEffects() { return flagEffects; }
    public ExceptionType getPossibleException() { return possibleException; }
    public String getDescription() { return description; }
    public int getBaseCycles() { return baseCycles; }

    /**
     * Check if this definition can accept the given operand types.
     */
    public boolean acceptsOperands(OperandType actualDst, OperandType actualSrc) {
        if (dstType == OperandType.NONE && srcType == OperandType.NONE) {
            return actualDst == OperandType.NONE && actualSrc == OperandType.NONE;
        }
        if (dstType == OperandType.NONE) {
            return actualSrc == srcType;
        }
        if (srcType == OperandType.NONE) {
            return actualDst == dstType;
        }
        return dstTypeCompatible(actualDst) && srcTypeCompatible(actualSrc);
    }

    private boolean dstTypeCompatible(OperandType actual) {
        return compatible(dstType, actual);
    }

    private boolean srcTypeCompatible(OperandType actual) {
        return compatible(srcType, actual);
    }

    /**
     * Determine if expected type is compatible with actual type.
     */
    public static boolean compatible(OperandType expected, OperandType actual) {
        if (expected == actual) return true;
        // REG16 accepts any 16-bit general register
        if (expected == OperandType.REG16 && actual == OperandType.REG16) return true;
        // MEM16 accepts any memory form
        if (expected == OperandType.MEM16 && actual == OperandType.MEM16) return true;
        // IMM16 accepts IMM8 (sign/zero extension)
        if (expected == OperandType.IMM16 && actual == OperandType.IMM8) return true;
        if (expected == OperandType.IMM16 && actual == OperandType.IMM16) return true;
        if (expected == OperandType.IMM8 && actual == OperandType.IMM8) return true;
        // REL8/REL16
        if (expected == OperandType.REL8 && actual == OperandType.REL8) return true;
        if (expected == OperandType.REL16 && actual == OperandType.REL16) return true;
        // Fixed operands
        if (expected == OperandType.FIXED_AL && actual == OperandType.FIXED_AL) return true;
        if (expected == OperandType.FIXED_AX && actual == OperandType.FIXED_AX) return true;
        if (expected == OperandType.FIXED_DXAX && actual == OperandType.FIXED_DXAX) return true;
        if (expected == OperandType.FIXED_CL && actual == OperandType.FIXED_CL) return true;
        if (expected == OperandType.VECTOR8 && actual == OperandType.VECTOR8) return true;
        if (expected == OperandType.STRING_SRC && actual == OperandType.STRING_SRC) return true;
        if (expected == OperandType.STRING_DST && actual == OperandType.STRING_DST) return true;
        // MEM16 also accepts REG16 (for [reg] indirect)
        if (expected == OperandType.MEM16 && actual == OperandType.REG16) return true;
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(mnemonic);
        if (dstType != OperandType.NONE) {
            sb.append(" ").append(dstType);
            if (srcType != OperandType.NONE) sb.append(", ").append(srcType);
        }
        sb.append(" [").append(width).append("]");
        return sb.toString();
    }

    // ---- Builder ----

    public static class Builder {
        private final String mnemonic;
        private int opcodeGroup = 0;
        private int opcodeByte = 0;
        private OpcodeExtension ext = OpcodeExtension.NONE;
        private OperandType dstType = OperandType.NONE;
        private OperandType srcType = OperandType.NONE;
        private OperandWidth width = OperandWidth.WORD;
        private EncodingFormat encoding = EncodingFormat.SIMPLE;
        private Set<FlagEffect> flagEffects = EnumSet.noneOf(FlagEffect.class);
        private ExceptionType possibleException = ExceptionType.NONE;
        private String description = "";
        private int baseCycles = 1;

        public Builder(String mnemonic) { this.mnemonic = mnemonic; }
        public Builder opcode(int group, int byte_) { this.opcodeGroup = group; this.opcodeByte = byte_; return this; }
        public Builder ext(OpcodeExtension e) { this.ext = e; return this; }
        public Builder dst(OperandType t) { this.dstType = t; return this; }
        public Builder src(OperandType t) { this.srcType = t; return this; }
        public Builder width(OperandWidth w) { this.width = w; return this; }
        public Builder encoding(EncodingFormat e) { this.encoding = e; return this; }
        public Builder flags(FlagEffect... f) {
            if (f.length == 0) {
                this.flagEffects = EnumSet.noneOf(FlagEffect.class);
            } else {
                this.flagEffects = EnumSet.of(f[0], f);
            }
            return this;
        }
        public Builder noFlags() { this.flagEffects = EnumSet.noneOf(FlagEffect.class); return this; }
        public Builder exception(ExceptionType e) { this.possibleException = e; return this; }
        public Builder desc(String d) { this.description = d; return this; }
        public Builder cycles(int c) { this.baseCycles = c; return this; }
        public InstructionDefinition build() { return new InstructionDefinition(this); }
    }
}

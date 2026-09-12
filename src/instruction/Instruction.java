package instruction;

/**
 * Instruction — an immutable, decoded instruction object.
 * Now includes effective address calculation, segment info, and encoding.
 */
public class Instruction {

    private final Opcode           opcode;
    private final InstructionFormat format;
    private final String            destReg;
    private final String            srcReg;
    private final int               immediate;
    private final int               address;       // Direct address or displacement
    private final String            rawText;

    // Effective address components (for complex addressing modes)
    private final String            baseReg;       // BX or BP
    private final String            indexReg;      // SI or DI
    private final int               displacement;  // Displacement value
    private final String            segmentOverride; // CS/DS/SS/ES override or null

    // Instruction encoding
    private final byte[]            encoded;       // Machine code bytes (null if not encoded)
    private final Opcode            prefix;         // Repeat prefix (REP/REPE/REPNE) or null

    private Instruction(Builder b) {
        this.opcode    = b.opcode;
        this.format    = b.format;
        this.destReg   = b.destReg;
        this.srcReg    = b.srcReg;
        this.immediate = b.immediate;
        this.address   = b.address;
        this.rawText   = b.rawText;
        this.baseReg   = b.baseReg;
        this.indexReg  = b.indexReg;
        this.displacement = b.displacement;
        this.segmentOverride = b.segmentOverride;
        this.encoded   = b.encoded;
        this.prefix    = b.prefix;
    }

    // ---- Accessors ----
    public Opcode            getOpcode()        { return opcode; }
    public InstructionFormat getFormat()        { return format; }
    public String            getDestReg()       { return destReg; }
    public String            getSrcReg()        { return srcReg; }
    public int               getImmediate()     { return immediate; }
    public int               getAddress()       { return address; }
    public String            getRawText()       { return rawText; }
    public String            getBaseReg()       { return baseReg; }
    public String            getIndexReg()      { return indexReg; }
    public int               getDisplacement()  { return displacement; }
    public String            getSegmentOverride(){ return segmentOverride; }
    public byte[]            getEncoded()       { return encoded; }
    public Opcode            getPrefix()        { return prefix; }

    /** Does this instruction have a segment override prefix? */
    public boolean hasSegmentOverride() { return segmentOverride != null; }

    /** Does this instruction use complex addressing (base+index+disp)? */
    public boolean hasEffectiveAddress() {
        return baseReg != null || indexReg != null || displacement != 0;
    }

    @Override
    public String toString() {
        return rawText != null ? rawText : opcode.toString();
    }

    // ---- Builder ---------------------------------------------------------------

    public static class Builder {
        private final Opcode opcode;
        private InstructionFormat format = InstructionFormat.NO_OPERAND;
        private String destReg = null;
        private String srcReg  = null;
        private int    immediate = 0;
        private int    address   = 0;
        private String rawText   = "";
        private String baseReg   = null;
        private String indexReg  = null;
        private int    displacement = 0;
        private String segmentOverride = null;
        private byte[] encoded   = null;
        private Opcode prefix   = null;

        public Builder(Opcode opcode)              { this.opcode = opcode; }
        public Builder format(InstructionFormat f) { this.format = f; return this; }
        public Builder dest(String r)              { this.destReg = r; return this; }
        public Builder src(String r)               { this.srcReg = r; return this; }
        public Builder imm(int v)                  { this.immediate = v; return this; }
        public Builder addr(int a)                 { this.address = a; return this; }
        public Builder raw(String t)               { this.rawText = t; return this; }
        public Builder baseReg(String r)           { this.baseReg = r; return this; }
        public Builder indexReg(String r)          { this.indexReg = r; return this; }
        public Builder disp(int d)                 { this.displacement = d; return this; }
        public Builder segOverride(String s)       { this.segmentOverride = s; return this; }
        public Builder encoded(byte[] e)           { this.encoded = e; return this; }
        public Builder prefix(Opcode p)           { this.prefix = p; return this; }
        public Instruction build()                 { return new Instruction(this); }
    }
}

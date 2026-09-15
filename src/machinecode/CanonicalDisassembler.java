package machinecode;

/** Produces deterministic canonical source spelling from a decoded instruction. */
public final class CanonicalDisassembler {
    public String disassemble(DecodedInstruction decoded) {
        if (decoded == null) throw new IllegalArgumentException("decoded instruction must not be null");
        return decoded.instruction().getRawText();
    }
}

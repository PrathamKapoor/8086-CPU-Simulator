package cpu.registers;

/**
 * IR (Instruction Register) — holds the currently fetched instruction index.
 * During Fetch: IR <- MDR.
 * During Decode: opcode and operands are extracted from this value.
 */
public class IR extends Register {
    public IR() { super("IR"); }
}

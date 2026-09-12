package cpu.registers;

/**
 * PC (Program Counter / IP) — holds the offset of the NEXT instruction to fetch.
 * During Fetch: MAR <- PC, then PC <- PC + 1.
 */
public class PC extends Register {
    public PC() { super("IP"); }
}

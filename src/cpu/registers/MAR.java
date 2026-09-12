package cpu.registers;

/**
 * MAR (Memory Address Register) — holds the physical address for a pending memory access.
 * Connected directly to the Address Bus.
 */
public class MAR extends Register {
    public MAR() { super("MAR"); }
}

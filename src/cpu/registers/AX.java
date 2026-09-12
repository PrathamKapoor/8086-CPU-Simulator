package cpu.registers;

/** AX — 16-bit accumulator register with AH/AL byte access. */
public class AX extends Register {
    public AX() { super("AX"); }
    public int getAH() { return highByte(); }
    public int getAL() { return lowByte(); }
    public void loadAH(int v) { loadHigh(v); }
    public void loadAL(int v) { loadLow(v); }
}

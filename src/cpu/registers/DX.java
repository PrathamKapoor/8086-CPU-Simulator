package cpu.registers;

/** DX — 16-bit data register with DH/DL byte access. */
public class DX extends Register {
    public DX() { super("DX"); }
    public int getDH() { return highByte(); }
    public int getDL() { return lowByte(); }
    public void loadDH(int v) { loadHigh(v); }
    public void loadDL(int v) { loadLow(v); }
}

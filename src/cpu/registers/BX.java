package cpu.registers;

/** BX — 16-bit base register with BH/BL byte access. */
public class BX extends Register {
    public BX() { super("BX"); }
    public int getBH() { return highByte(); }
    public int getBL() { return lowByte(); }
    public void loadBH(int v) { loadHigh(v); }
    public void loadBL(int v) { loadLow(v); }
}

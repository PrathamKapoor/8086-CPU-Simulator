package bus;

/**
 * AddressBus — carries 20-bit physical addresses from MAR to Memory.
 * In real 8086: segment*16 + offset = 20-bit address.
 */
public class AddressBus {
    private int address;
    private boolean active;

    public void drive(int address) {
        this.address = address & 0xFFFFF;  // 20-bit
        this.active = true;
    }

    public int read() { return address; }

    public void clear() {
        this.address = 0;
        this.active = false;
    }

    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return active ? String.format("ADDR=0x%05X", address) : "ADDR=<idle>";
    }
}

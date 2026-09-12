package bus;

/**
 * DataBus — carries data between CPU registers and Memory (bidirectional, 16-bit).
 * Driven by MDR during writes; driven by Memory during reads.
 */
public class DataBus {
    private int data;
    private boolean active;

    public void drive(int data) {
        this.data = data & 0xFFFF;
        this.active = true;
    }

    public int read() { return data; }

    public void clear() {
        this.data = 0;
        this.active = false;
    }

    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return active ? String.format("DATA=0x%04X", data) : "DATA=<idle>";
    }
}

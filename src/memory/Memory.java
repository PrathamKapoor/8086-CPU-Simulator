package memory;

import bus.AddressBus;
import bus.ControlBus;
import bus.DataBus;

/**
 * Memory — Full 1MB simulated RAM (20-bit physical address space, 1,048,576 words).
 * Supports full segment:offset addressing up to 0xFFFFF.
 * Lazy cell allocation minimizes heap footprint.
 */
public class Memory {

    public static final int SIZE = 0x100000; // 1,048,576 words (1MB address space matching 8086 20-bit bus)

    private final MemoryCell[] cells;
    private final AddressBus addressBus;
    private final DataBus    dataBus;
    private final ControlBus controlBus;

    public Memory(AddressBus addressBus, DataBus dataBus, ControlBus controlBus) {
        this.addressBus = addressBus;
        this.dataBus    = dataBus;
        this.controlBus = controlBus;
        this.cells = new MemoryCell[SIZE];
    }

    // ---- Lazy allocation to keep footprint minimal ----
    private MemoryCell getOrCreateCell(int address) {
        rangeCheck(address);
        if (cells[address] == null) {
            cells[address] = new MemoryCell();
        }
        return cells[address];
    }

    // ---- Bus-mediated access (used by micro-operations) -------------------------

    /** Bus read: Memory[AddressBus] -> DataBus. */
    public void busRead() {
        int addr = addressBus.read();
        rangeCheck(addr);
        dataBus.drive(getOrCreateCell(addr).read());
    }

    /** Bus write: DataBus -> Memory[AddressBus]. */
    public void busWrite() {
        int addr = addressBus.read();
        rangeCheck(addr);
        getOrCreateCell(addr).write(dataBus.read());
    }

    // ---- Direct access (program load / GUI display -- bypasses buses) -----------

    public void directWrite(int address, int value) {
        rangeCheck(address);
        getOrCreateCell(address).write(value);
    }

    public int directRead(int address) {
        rangeCheck(address);
        if (cells[address] == null) return 0;
        return cells[address].read();
    }

    public MemoryCell getCell(int address) {
        rangeCheck(address);
        return getOrCreateCell(address);
    }

    public int getSize() { return SIZE; }

    public void reset() {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                cells[i].clear();
            }
        }
    }

    private void rangeCheck(int address) {
        if (address < 0 || address >= SIZE) {
            throw new IndexOutOfBoundsException("Memory address out of range: " + address + " (max=" + (SIZE - 1) + ")");
        }
    }
}

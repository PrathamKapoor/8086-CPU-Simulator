package cpu.biu;

import cpu.registers.CS;
import cpu.registers.PC;

public class FetchState {
    private final CS cs;
    private final PC ip;
    private int fetchPhysicalAddress = 0;

    public FetchState(CS cs, PC ip) {
        this.cs = cs;
        this.ip = ip;
    }

    public int computeFetchPhysicalAddress() {
        int segment = cs.output();
        int offset = ip.output();
        fetchPhysicalAddress = ((segment << 4) + offset) & 0xFFFFF;
        return fetchPhysicalAddress;
    }

    public int getFetchPhysicalAddress() {
        return fetchPhysicalAddress;
    }

    public int getCSPhysicalBase() {
        return (cs.output() << 4) & 0xFFFFF;
    }

    public int getIP() {
        return ip.output();
    }

    public int getCS() {
        return cs.output();
    }

    public void setFetchPhysicalAddress(int addr) {
        this.fetchPhysicalAddress = addr & 0xFFFFF;
    }

    public void reset() {
        fetchPhysicalAddress = 0;
    }

    @Override
    public String toString() {
        return "FetchState(CS=0x" + String.format("%04X", cs.output())
            + ", IP=0x" + String.format("%04X", ip.output())
            + ", physAddr=0x" + String.format("%05X", fetchPhysicalAddress) + ")";
    }
}

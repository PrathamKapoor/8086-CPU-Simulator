package cpu.biu;

public class BusState {
    private boolean addressBusActive = false;
    private boolean dataBusActive = false;
    private boolean controlBusActive = false;
    private int physicalAddress = 0;
    private int cycle = 0;

    public void setAddressBusActive(boolean active) { this.addressBusActive = active; }
    public void setDataBusActive(boolean active) { this.dataBusActive = active; }
    public void setControlBusActive(boolean active) { this.controlBusActive = active; }

    public boolean isAddressBusActive() { return addressBusActive; }
    public boolean isDataBusActive() { return dataBusActive; }
    public boolean isControlBusActive() { return controlBusActive; }

    public void setPhysicalAddress(int addr) { this.physicalAddress = addr & 0xFFFFF; }
    public int getPhysicalAddress() { return physicalAddress; }

    public int getCycle() { return cycle; }
    public void tick() { cycle++; }

    public void reset() {
        addressBusActive = false;
        dataBusActive = false;
        controlBusActive = false;
        physicalAddress = 0;
        cycle = 0;
    }

    @Override
    public String toString() {
        return "BusState(addrActive=" + addressBusActive
            + ", dataActive=" + dataBusActive
            + ", ctrlActive=" + controlBusActive
            + ", physAddr=0x" + String.format("%05X", physicalAddress)
            + ", cycle=" + cycle + ")";
    }
}

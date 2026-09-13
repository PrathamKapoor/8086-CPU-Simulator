package cpu.biu;

import cpu.CPU;
import cpu.registers.CS;
import cpu.registers.PC;
import memory.Memory;
import microoperation.MicroOperationType;

public class BusInterfaceUnit {
    private final PrefetchQueue prefetchQueue;
    private final BusState      busState;
    private final FetchState    fetchState;
    private final Memory        memory;
    private final CS            cs;
    private final PC            ip;

    private boolean halted = false;

    public BusInterfaceUnit(CS cs, PC ip, Memory memory) {
        this.prefetchQueue = new PrefetchQueue();
        this.busState = new BusState();
        this.fetchState = new FetchState(cs, ip);
        this.memory = memory;
        this.cs = cs;
        this.ip = ip;
    }

    public PrefetchQueue getPrefetchQueue() { return prefetchQueue; }
    public BusState getBusState() { return busState; }
    public FetchState getFetchState() { return fetchState; }

    public void tick() {
        if (halted) return;
        busState.tick();
        int physAddr = fetchState.computeFetchPhysicalAddress();
        busState.setPhysicalAddress(physAddr);
        busState.setAddressBusActive(true);

        // Fetch instruction bytes sequentially (simplified 8086-style)
        if (!prefetchQueue.isFull()) {
            int value = memory.readByte(physAddr);
            prefetchQueue.enqueue(value);
            busState.setDataBusActive(true);
        }
    }

    public boolean hasAvailableInstruction() {
        return !prefetchQueue.isEmpty();
    }

    public int consumeInstructionByte() {
        return prefetchQueue.dequeue();
    }

    public int peekInstructionByte() {
        return prefetchQueue.peek();
    }

    public int peekInstructionByte(int offset) {
        return prefetchQueue.peek(offset);
    }

    public int queueSize() {
        return prefetchQueue.size();
    }

    public void flushQueue() {
        prefetchQueue.clear();
    }

    public void setHalted(boolean halted) {
        this.halted = halted;
    }

    public boolean isHalted() {
        return halted;
    }

    public void reset() {
        prefetchQueue.clear();
        busState.reset();
        fetchState.reset();
        halted = false;
    }

    @Override
    public String toString() {
        return "BIU[fetchIP=0x" + String.format("%04X", fetchState.getIP())
            + ", physAddr=0x" + String.format("%05X", fetchState.getFetchPhysicalAddress())
            + ", queueSize=" + prefetchQueue.size()
            + "]";
    }
}

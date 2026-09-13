package simulator.profiler;

import cpu.CPU;

/**
 * PerformanceProfiler — lightweight microarchitecture instrumentation.
 */
public class PerformanceProfiler {
    private final CPU cpu;

    public PerformanceProfiler(CPU cpu) {
        this.cpu = cpu;
    }

    public int getStallCycles() { return cpu.getStallCycles(); }
    public int getQueueFlushes() { return cpu.getQueueFlushes(); }
    public int getBytesFetched() { return cpu.getBytesFetched(); }
    public int getBytesConsumed() { return cpu.getBytesConsumed(); }
    public int getMaxQueueOccupancy() { return cpu.getMaxQueueOccupancy(); }
    public int getTotalOverlapCycles() { return cpu.getTotalOverlapCycles(); }
    public int getBusActiveCycles() { return cpu.getBusActiveCycles(); }
    public int getBiuFetchEvents() { return cpu.getBiuFetchEvents(); }
    public int getTotalCyclesRun() { return (int) cpu.getTotalCyclesRun(); }

    public String snapshot() {
        return "PerformanceSnapshot["
            + "cycles=" + cpu.getTotalCyclesRun()
            + ", stalls=" + cpu.getStallCycles()
            + ", flushes=" + cpu.getQueueFlushes()
            + ", fetched=" + cpu.getBytesFetched()
            + ", consumed=" + cpu.getBytesConsumed()
            + ", overlap=" + cpu.getTotalOverlapCycles()
            + ", maxOcc=" + cpu.getMaxQueueOccupancy()
            + ", biuFetch=" + cpu.getBiuFetchEvents()
            + "]";
    }
}

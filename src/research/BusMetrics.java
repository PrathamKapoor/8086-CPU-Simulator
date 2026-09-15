package research;

import cpu.CPU;
import cpu.microarchitecture.BusOwner;

/**
 * Bus-ownership tallies derived directly from {@code CycleSnapshot.busOwner()}
 * — a field {@code PerformanceProfiler} does not already aggregate, so this
 * is new trace-to-metric analysis (Phase 6 Step 11), not a duplicate of an
 * existing computation. Only meaningful under a timed model
 * (SIMPLIFIED_8086/EXPERIMENTAL); under FUNCTIONAL every cycle reports
 * {@code BusOwner.NONE} since {@code stepFunctional()} never sets an owner.
 */
public record BusMetrics(long grantedToBiu, long grantedToEu, long idleCycles, long contentionCycles) {
    public static BusMetrics from(CPU cpu, long biuStallCycles) {
        var trace = cpu.getCycleTrace();
        long biu = trace.stream().filter(s -> s.busOwner() == BusOwner.BIU_FETCH).count();
        long eu = trace.stream().filter(s -> s.busOwner() == BusOwner.EU_MEMORY).count();
        long idle = trace.stream().filter(s -> s.busOwner() == BusOwner.NONE).count();
        // "Contention" = cycles where the BIU wanted the bus but deferred to an EU
        // memory access already holding it -- exactly what BIU_STALL already
        // measures (see CPU.stepTimed: emitted when fetched.waitingForBus() during
        // a memory operation), reused rather than redefined.
        return new BusMetrics(biu, eu, idle, biuStallCycles);
    }
}

package simulator.profiler;

import cpu.CPU;
import cpu.microarchitecture.*;

/** Metrics are a pure deterministic reduction over CPU cycle snapshots. */
public final class PerformanceProfiler {
    private final CPU cpu;
    public PerformanceProfiler(CPU cpu) { this.cpu = cpu; }
    private long count(MicroarchitectureEventType type) { return cpu.getCycleTrace().stream().flatMap(s -> s.events().stream()).filter(e -> e.type()==type).count(); }
    public PerformanceSnapshot metrics() {
        var trace=cpu.getCycleTrace(); long retired=trace.stream().filter(CycleSnapshot::instructionRetired).count();
        long occupancy=trace.stream().mapToLong(CycleSnapshot::queueOccupancy).sum(); long empty=trace.stream().filter(s->s.euState()==UnitState.EU_WAITING_FOR_QUEUE).count();
        long full=trace.stream().filter(s->s.biuState()==UnitState.QUEUE_FULL).count(); long biu=trace.stream().filter(s->s.biuState()==UnitState.BIU_ACTIVE).count();
        long eu=trace.stream().filter(s->s.euState()==UnitState.EU_ACTIVE).count(); long overlap=trace.stream().filter(s->s.biuState()==UnitState.BIU_ACTIVE&&s.euState()==UnitState.EU_ACTIVE).count();
        long flushed=trace.stream().flatMap(s->s.events().stream()).filter(e->e.type()==MicroarchitectureEventType.QUEUE_FLUSH).mapToLong(MicroarchitectureEvent::count).sum();
        return new PerformanceSnapshot(trace.size(),retired,retired==0?0:(double)trace.size()/retired,count(MicroarchitectureEventType.FETCH_BYTE),count(MicroarchitectureEventType.QUEUE_POP),trace.isEmpty()?0:(double)occupancy/trace.size(),empty,full,biu,eu,overlap,count(MicroarchitectureEventType.EU_STALL),count(MicroarchitectureEventType.BIU_STALL),count(MicroarchitectureEventType.MEM_READ),count(MicroarchitectureEventType.MEM_WRITE),count(MicroarchitectureEventType.QUEUE_FLUSH),flushed);
    }
    public String snapshot() { return metrics().toJson(); }
}

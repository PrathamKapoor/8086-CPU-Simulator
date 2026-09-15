package research;

import cpu.CPU;
import cpu.microarchitecture.BusOwner;
import cpu.microarchitecture.CycleSnapshot;
import cpu.microarchitecture.MicroarchitectureEventType;
import cpu.microarchitecture.UnitState;
import debugger.Json;

import java.util.List;

/**
 * A structured, cycle-by-cycle execution timeline (Phase 6 Step 12), built
 * directly from {@code CPU.getCycleTrace()} — the existing Phase 3 typed
 * trace, not a new event system. Where {@link MetricSet} answers "how many
 * stalls happened", {@code Timeline} answers "show me each one, in order,
 * with what else was happening in that cycle" — the raw material an
 * evidence-backed "why did this take longer" explanation is built from.
 */
public record Timeline(List<Entry> entries) {
    public record Entry(long cycle, int instructionIndex, String instruction, String microOperation,
                        UnitState biuState, UnitState euState, BusOwner busOwner, int queueOccupancy,
                        boolean instructionRetired, boolean euStalled, boolean biuStalled, boolean flushedThisCycle) {
        public String toJson() {
            return "{\"cycle\":" + cycle + ",\"instructionIndex\":" + instructionIndex
                + ",\"instruction\":" + Json.str(instruction) + ",\"microOperation\":" + Json.str(microOperation)
                + ",\"biuState\":\"" + biuState + "\",\"euState\":\"" + euState + "\",\"busOwner\":\"" + busOwner + "\""
                + ",\"queueOccupancy\":" + queueOccupancy + ",\"instructionRetired\":" + instructionRetired
                + ",\"euStalled\":" + euStalled + ",\"biuStalled\":" + biuStalled + ",\"flushedThisCycle\":" + flushedThisCycle + "}";
        }
    }

    public static Timeline from(CPU cpu) {
        List<Entry> entries = cpu.getCycleTrace().stream().map(Timeline::toEntry).toList();
        return new Timeline(entries);
    }

    private static Entry toEntry(CycleSnapshot s) {
        boolean euStalled = s.euState() == UnitState.EU_WAITING_FOR_QUEUE;
        boolean biuStalled = s.biuState() == UnitState.BIU_WAITING_FOR_BUS;
        boolean flushed = s.events().stream().anyMatch(e -> e.type() == MicroarchitectureEventType.QUEUE_FLUSH);
        return new Entry(s.cycle(), s.instructionIndex(), s.instruction(), s.microOperation(),
            s.biuState(), s.euState(), s.busOwner(), s.queueOccupancy(), s.instructionRetired(), euStalled, biuStalled, flushed);
    }

    public List<Entry> stalledCycles() { return entries.stream().filter(e -> e.euStalled() || e.biuStalled()).toList(); }
    public List<Entry> flushCycles() { return entries.stream().filter(Entry::flushedThisCycle).toList(); }

    /**
     * A deterministic, evidence-backed breakdown of where cycles went —
     * exactly the categories {@link MetricSet} already counts, cross-checked
     * against this timeline's own tally so the two can never silently
     * disagree (see research.CrossLayerConsistencyTest).
     */
    public String explainCycleCount() {
        long total = entries.size();
        long euStall = entries.stream().filter(Entry::euStalled).count();
        long biuStall = entries.stream().filter(Entry::biuStalled).count();
        long flush = flushCycles().size();
        long useful = total - euStall - biuStall;
        return "total=" + total + " useful=" + useful + " euQueueStarvation=" + euStall
            + " biuBusContention=" + biuStall + " controlTransferFlushCycles=" + flush;
    }

    public String toJson() { return Json.array(entries, Entry::toJson); }
}

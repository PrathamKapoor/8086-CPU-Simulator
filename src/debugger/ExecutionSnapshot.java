package debugger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything needed to resume deterministic execution from this point, plus
 * the timing/BIU state the task asks to preserve for fidelity. Memory itself
 * is NOT copied here — see {@link MemoryJournal}; a snapshot instead records
 * {@code memoryJournalPosition}, the journal length at capture time, which
 * {@code DebugSession.restore} rewinds the shared undo journal to.
 *
 * A plain record: two snapshots are {@code .equals()} exactly when every
 * piece of state they capture is identical, which is what the determinism/
 * replay tests in Phase 5 compare.
 */
public record ExecutionSnapshot(
    Map<String, Integer> registers,
    int flags,
    boolean halted,
    long totalCyclesRun,
    long totalMicroOpsExecuted,
    long totalInstructionsRetired,
    int stallCycles,
    int queueFlushes,
    int bytesFetched,
    int bytesConsumed,
    int maxQueueOccupancy,
    int totalOverlapCycles,
    int busActiveCycles,
    int biuFetchEvents,
    List<Integer> prefetchQueueContents,
    int fetchPhysicalAddress,
    int nextFetchOffset,
    boolean addressBusActive,
    boolean dataBusActive,
    boolean controlBusActive,
    int busPhysicalAddress,
    String controlUnitPhase,
    long memoryJournalPosition
) {
    public ExecutionSnapshot {
        registers = Map.copyOf(registers);
        prefetchQueueContents = List.copyOf(prefetchQueueContents);
    }

    public String toJson() {
        String regs = registers.entrySet().stream()
            .map(e -> Json.str(e.getKey()) + ":" + e.getValue())
            .collect(Collectors.joining(",", "{", "}"));
        String queue = prefetchQueueContents.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
        return "{\"registers\":" + regs
            + ",\"flags\":" + flags
            + ",\"halted\":" + halted
            + ",\"totalCyclesRun\":" + totalCyclesRun
            + ",\"totalMicroOpsExecuted\":" + totalMicroOpsExecuted
            + ",\"totalInstructionsRetired\":" + totalInstructionsRetired
            + ",\"stallCycles\":" + stallCycles
            + ",\"queueFlushes\":" + queueFlushes
            + ",\"bytesFetched\":" + bytesFetched
            + ",\"bytesConsumed\":" + bytesConsumed
            + ",\"maxQueueOccupancy\":" + maxQueueOccupancy
            + ",\"totalOverlapCycles\":" + totalOverlapCycles
            + ",\"busActiveCycles\":" + busActiveCycles
            + ",\"biuFetchEvents\":" + biuFetchEvents
            + ",\"prefetchQueueContents\":" + queue
            + ",\"fetchPhysicalAddress\":" + fetchPhysicalAddress
            + ",\"nextFetchOffset\":" + nextFetchOffset
            + ",\"addressBusActive\":" + addressBusActive
            + ",\"dataBusActive\":" + dataBusActive
            + ",\"controlBusActive\":" + controlBusActive
            + ",\"busPhysicalAddress\":" + busPhysicalAddress
            + ",\"controlUnitPhase\":" + Json.str(controlUnitPhase)
            + ",\"memoryJournalPosition\":" + memoryJournalPosition + "}";
    }
}

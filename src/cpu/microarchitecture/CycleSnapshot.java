package cpu.microarchitecture;

import java.util.List;

/** Immutable end-of-cycle state used by traces, metrics, CLI JSON, and the GUI. */
public record CycleSnapshot(long cycle, UnitState biuState, UnitState euState,
                            BusOwner busOwner, List<Integer> queueContents,
                            int queueOccupancy, int instructionIndex,
                            String instruction, String microOperation,
                            boolean instructionRetired, List<MicroarchitectureEvent> events) {
    public CycleSnapshot {
        queueContents = List.copyOf(queueContents);
        events = List.copyOf(events);
    }

    public String toJson() {
        String queue = queueContents.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String eventJson = events.stream().map(MicroarchitectureEvent::toJson).collect(java.util.stream.Collectors.joining(","));
        return "{\"cycle\":" + cycle + ",\"biu\":\"" + biuState + "\",\"eu\":\"" + euState
            + "\",\"bus\":\"" + busOwner + "\",\"queue\":[" + queue + "],\"occupancy\":" + queueOccupancy
            + ",\"instructionIndex\":" + instructionIndex + ",\"instruction\":\"" + escape(instruction)
            + "\",\"microOperation\":\"" + escape(microOperation) + "\",\"retired\":" + instructionRetired
            + ",\"events\":[" + eventJson + "]}";
    }

    private static String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }
}

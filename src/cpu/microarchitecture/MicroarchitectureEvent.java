package cpu.microarchitecture;

/** Stable, serializable event.  Numeric fields use -1 when not applicable. */
public record MicroarchitectureEvent(long cycle, MicroarchitectureEventType type,
                                     int address, int value, int count) {
    public String toJson() {
        return "{\"cycle\":" + cycle + ",\"type\":\"" + type + "\",\"address\":"
            + address + ",\"value\":" + value + ",\"count\":" + count + "}";
    }
}

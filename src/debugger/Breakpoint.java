package debugger;

/** A real, evaluated stop condition — never a GUI flag. */
public final class Breakpoint {
    public enum Kind { INSTRUCTION_INDEX, MACHINE_OFFSET }

    private final int id;
    private final Kind kind;
    private final int location;
    private final BreakpointCondition condition;
    private boolean enabled = true;
    private int hitCount = 0;

    Breakpoint(int id, Kind kind, int location, BreakpointCondition condition) {
        this.id = id;
        this.kind = kind;
        this.location = location;
        this.condition = condition;
    }

    public int id() { return id; }
    public Kind kind() { return kind; }
    public int location() { return location; }
    public BreakpointCondition condition() { return condition; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int hitCount() { return hitCount; }
    void recordHit() { hitCount++; }

    /** True if this breakpoint's location matches the given position (condition checked separately). */
    boolean matchesLocation(ExecutionPosition position) {
        return switch (kind) {
            case INSTRUCTION_INDEX -> position.instructionIndex() == location;
            case MACHINE_OFFSET -> position.machineByteOffset() == location;
        };
    }

    public String describeLocation() {
        return switch (kind) {
            case INSTRUCTION_INDEX -> "instruction[" + location + "]";
            case MACHINE_OFFSET -> "offset 0x" + Integer.toHexString(location).toUpperCase(java.util.Locale.ROOT);
        };
    }

    public String toJson() {
        return "{\"id\":" + id
            + ",\"kind\":\"" + kind + "\""
            + ",\"location\":" + location
            + ",\"condition\":" + (condition == null ? "null" : "\"" + Json.escape(condition.rawText()) + "\"")
            + ",\"enabled\":" + enabled
            + ",\"hitCount\":" + hitCount + "}";
    }
}

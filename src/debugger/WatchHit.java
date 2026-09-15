package debugger;

/**
 * A single watchpoint trigger. {@code address} is set for MEMORY watches
 * (and is {@code -1} for REGISTER/FLAG ones, which instead set {@code name}).
 */
public record WatchHit(
    int watchpointId,
    Watchpoint.Kind kind,
    Watchpoint.Access access,
    int address,
    String name,
    int oldValue,
    int newValue,
    ExecutionPosition position,
    String instructionText
) {
    public String toJson() {
        return "{\"watchpointId\":" + watchpointId
            + ",\"kind\":\"" + kind + "\""
            + ",\"access\":\"" + access + "\""
            + ",\"address\":" + address
            + ",\"name\":" + Json.str(name)
            + ",\"oldValue\":" + oldValue
            + ",\"newValue\":" + newValue
            + ",\"position\":" + position.toJson()
            + ",\"instructionText\":" + Json.str(instructionText) + "}";
    }
}

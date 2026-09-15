package debugger;

import java.util.List;

/**
 * The complete externally-visible debugger state at this instant — what the
 * CLI, JSON API, and GUI all render from. No unstable object identities are
 * exposed; breakpoint/watchpoint ids are small session-local sequence
 * numbers assigned at creation time.
 */
public record DebugState(
    ExecutionPosition position,
    StopReason lastStopReason,
    boolean halted,
    String currentInstructionText,
    String currentInstructionBytesHex,
    ExecutionSnapshot snapshot,
    List<Breakpoint> breakpoints,
    List<Watchpoint> watchpoints,
    WatchHit lastWatchHit,
    Integer lastBreakpointId
) {
    public String toJson() {
        return "{\"position\":" + position.toJson()
            + ",\"lastStopReason\":\"" + lastStopReason + "\""
            + ",\"halted\":" + halted
            + ",\"currentInstructionText\":" + Json.str(currentInstructionText)
            + ",\"currentInstructionBytesHex\":" + Json.str(currentInstructionBytesHex)
            + ",\"snapshot\":" + snapshot.toJson()
            + ",\"breakpoints\":" + Json.array(breakpoints, Breakpoint::toJson)
            + ",\"watchpoints\":" + Json.array(watchpoints, Watchpoint::toJson)
            + ",\"lastWatchHit\":" + (lastWatchHit == null ? "null" : lastWatchHit.toJson())
            + ",\"lastBreakpointId\":" + (lastBreakpointId == null ? "null" : lastBreakpointId) + "}";
    }
}

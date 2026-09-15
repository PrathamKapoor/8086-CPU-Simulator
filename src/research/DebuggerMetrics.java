package research;

import debugger.DebugSession;

/** Breakpoint/watchpoint activity and trace volume from the optional Phase 5 observation pass. */
public record DebuggerMetrics(long traceEventCount, long breakpointHits, long watchpointHits, boolean available) {
    static final DebuggerMetrics UNAVAILABLE = new DebuggerMetrics(0, 0, 0, false);

    public static DebuggerMetrics from(DebugSession session) {
        long breakpointHits = session.breakpoints().stream().mapToLong(b -> b.hitCount()).sum();
        long watchpointHits = session.watchpoints().stream().mapToLong(w -> w.hitCount()).sum();
        return new DebuggerMetrics(session.trace().size(), breakpointHits, watchpointHits, true);
    }
}

package research;

import debugger.Json;
import simulator.profiler.PerformanceSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full, ordered, named metric collection for one {@link ExperimentRun}.
 * Every metric's definition is documented at the point it is built, per
 * Phase 6 Step 5 — see the string literals below for the formula backing
 * each one. Built once from {@code PerformanceProfiler.metrics()} (Phase 3,
 * unmodified) plus this phase's new {@link BusMetrics}, and — only when the
 * optional debugger/trace pass ran — {@link BranchMetrics}/{@link DebuggerMetrics}.
 */
public final class MetricSet {
    private final List<Metric> metrics;
    private final Map<String, Metric> byName;

    private MetricSet(List<Metric> metrics) {
        this.metrics = List.copyOf(metrics);
        Map<String, Metric> map = new LinkedHashMap<>();
        for (Metric m : metrics) map.put(m.name(), m);
        this.byName = Map.copyOf(map);
    }

    public static MetricSet build(PerformanceSnapshot perf, BusMetrics bus, BranchMetrics branch, DebuggerMetrics dbg) {
        List<Metric> m = new ArrayList<>();

        // EXECUTION
        m.add(measured("execution.instructionsRetired", "count of cycles with instructionRetired == true", perf.instructionsRetired()));
        m.add(measured("execution.microOpsExecuted", "== totalCycles: exactly one micro-op executes per simulator cycle in this architecture", perf.totalCycles()));
        m.add(measured("execution.totalCycles", "length of the cycle trace (one entry per CPU.step() call)", perf.totalCycles()));
        m.add(derived("execution.cpi", "totalCycles / instructionsRetired; 0 when instructionsRetired == 0 (never divides by zero)",
            perf.instructionsRetired() == 0 ? 0 : perf.cpi()));
        m.add(derived("execution.instructionsPerCycle", "instructionsRetired / totalCycles; 0 when totalCycles == 0",
            perf.totalCycles() == 0 ? 0 : (double) perf.instructionsRetired() / perf.totalCycles()));

        // BIU
        m.add(measured("biu.bytesFetched", "count of FETCH_BYTE events", perf.bytesFetched()));
        m.add(measured("biu.queueOccupancyAverage", "sum(queueOccupancy per cycle) / totalCycles; 0 when totalCycles == 0", perf.averageQueueOccupancy()));
        m.add(measured("biu.queueStarvationCycles", "count of cycles where EU state == EU_WAITING_FOR_QUEUE (EU had no byte to consume)", perf.queueEmptyCycles()));
        m.add(measured("biu.queueFullCycles", "count of cycles where BIU state == QUEUE_FULL (fetch withheld, queue at capacity)", perf.queueFullCycles()));
        m.add(measured("biu.activeCycles", "count of cycles where BIU state == BIU_ACTIVE", perf.biuActiveCycles()));

        // EU
        m.add(measured("eu.activeCycles", "count of cycles where EU state == EU_ACTIVE", perf.euActiveCycles()));
        m.add(measured("eu.stallCycles", "count of EU_STALL events", perf.euStallCycles()));
        m.add(measured("eu.biuOverlapCycles", "count of cycles where BIU_ACTIVE and EU_ACTIVE hold simultaneously", perf.overlapCycles()));

        // BUS
        m.add(measured("bus.grantedToBiu", "count of cycles where CycleSnapshot.busOwner() == BIU_FETCH", bus.grantedToBiu()));
        m.add(measured("bus.grantedToEu", "count of cycles where CycleSnapshot.busOwner() == EU_MEMORY", bus.grantedToEu()));
        m.add(measured("bus.idleCycles", "count of cycles where CycleSnapshot.busOwner() == NONE", bus.idleCycles()));
        m.add(derived("bus.requests", "bus.grantedToBiu + bus.grantedToEu", bus.grantedToBiu() + bus.grantedToEu()));
        m.add(measured("bus.contentionCycles", "== eu.biuStallCycles: cycles the BIU deferred a fetch because EU held the bus for a memory access", perf.biuStallCycles()));

        // CONTROL FLOW
        if (branch.available()) {
            m.add(measured("controlFlow.conditionalBranchesRetired", "count of retired Jcc/LOOP-family instructions", branch.conditionalBranches()));
            m.add(measured("controlFlow.takenBranches", "conditional branches immediately followed by a ControlTransfer event for the same instruction occurrence", branch.takenBranches()));
            m.add(measured("controlFlow.notTakenBranches", "conditionalBranchesRetired - takenBranches", branch.notTakenBranches()));
            m.add(measured("controlFlow.unconditionalTransfers", "count of retired JMP/CALL instructions", branch.unconditionalTransfers()));
        } else {
            m.add(unavailable("controlFlow.conditionalBranchesRetired", "requires ExperimentConfiguration.tracingEnabled() -- the typed per-instruction trace is needed to classify opcodes"));
            m.add(unavailable("controlFlow.takenBranches", "requires ExperimentConfiguration.tracingEnabled()"));
            m.add(unavailable("controlFlow.notTakenBranches", "requires ExperimentConfiguration.tracingEnabled()"));
            m.add(unavailable("controlFlow.unconditionalTransfers", "requires ExperimentConfiguration.tracingEnabled()"));
        }
        m.add(measured("controlFlow.queueFlushes", "count of QUEUE_FLUSH events, emitted only for an actual CONTROL_TRANSFER (IP != previousIndex+1) -- never a heuristic instruction-pattern match", perf.controlTransferFlushes()));
        m.add(measured("controlFlow.flushedBytes", "sum of QUEUE_FLUSH event counts: prefetch-queue bytes discarded on a control transfer", perf.flushedBytes()));

        // MEMORY
        m.add(measured("memory.reads", "count of MEM_READ events", perf.memoryReads()));
        m.add(measured("memory.writes", "count of MEM_WRITE events", perf.memoryWrites()));
        m.add(derived("memory.cellsTransferred", "memory.reads + memory.writes (each event moves one architectural memory cell -- 16 bits in this word-addressable simulator model)",
            perf.memoryReads() + perf.memoryWrites()));

        // TIMING
        m.add(measured("timing.totalCycles", "same as execution.totalCycles, repeated under the timing category for report grouping", perf.totalCycles()));
        m.add(measured("timing.overlapCycles", "same as eu.biuOverlapCycles", perf.overlapCycles()));
        m.add(derived("timing.stalledCycles", "eu.stallCycles + bus.contentionCycles (biuStallCycles): cycles lost to queue starvation or bus contention",
            perf.euStallCycles() + perf.biuStallCycles()));
        m.add(derived("timing.usefulCycles", "totalCycles - timing.stalledCycles (a cycle not spent stalled actually retired progress toward an instruction)",
            perf.totalCycles() - (perf.euStallCycles() + perf.biuStallCycles())));

        // DEBUGGER
        if (dbg.available()) {
            m.add(measured("debugger.traceEventCount", "size of the DebugSession typed trace for this run", dbg.traceEventCount()));
            m.add(measured("debugger.breakpointHits", "sum of Breakpoint.hitCount() across all configured breakpoints", dbg.breakpointHits()));
            m.add(measured("debugger.watchpointHits", "sum of Watchpoint.hitCount() across all configured watchpoints", dbg.watchpointHits()));
        } else {
            m.add(unavailable("debugger.traceEventCount", "requires ExperimentConfiguration.tracingEnabled() or debuggerEnabled()"));
            m.add(unavailable("debugger.breakpointHits", "requires ExperimentConfiguration.debuggerEnabled()"));
            m.add(unavailable("debugger.watchpointHits", "requires ExperimentConfiguration.debuggerEnabled()"));
        }

        return new MetricSet(m);
    }

    private static Metric measured(String name, String def, long value) { return new Metric(name, def, MetricProvenance.MEASURED, value); }
    private static Metric measured(String name, String def, double value) { return new Metric(name, def, MetricProvenance.MEASURED, value); }
    private static Metric derived(String name, String def, long value) { return new Metric(name, def, MetricProvenance.DERIVED, value); }
    private static Metric derived(String name, String def, double value) { return new Metric(name, def, MetricProvenance.DERIVED, value); }
    private static Metric unavailable(String name, String def) { return new Metric(name, def, MetricProvenance.UNAVAILABLE, Double.NaN); }

    public List<Metric> all() { return metrics; }
    public Metric get(String name) { return byName.get(name); }
    public boolean has(String name) { return byName.containsKey(name); }

    public String toJson() { return Json.array(metrics, Metric::toJson); }

    public List<String> describe() { return metrics.stream().map(Metric::describe).toList(); }

    /** Deterministic canonical text for {@link ResultHasher}: fixed insertion order, name=value pairs only. */
    String canonicalText() {
        StringBuilder sb = new StringBuilder();
        for (Metric m : metrics) sb.append(m.name()).append('=').append(m.provenance() == MetricProvenance.UNAVAILABLE ? "NA" : m.value()).append(';');
        return sb.toString();
    }
}

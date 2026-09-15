package research;

/**
 * Every {@link Metric} is labeled with exactly how its value came to be, per
 * Phase 6 Step 4: never report a number the underlying model can't actually
 * produce.
 */
public enum MetricProvenance {
    /** Read directly from a typed trace/cycle-snapshot event count or field — no arithmetic beyond tallying. */
    MEASURED,
    /** Computed from one or more MEASURED metrics by a documented formula (e.g. CPI). */
    DERIVED,
    /** The configuration that would produce this metric was not enabled (e.g. tracing off), or the architecture cannot measure it at all. */
    UNAVAILABLE
}

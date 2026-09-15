package memory;

/**
 * Optional observer for {@link Memory} mutations and reads, used by the
 * debugger's watchpoints and undo-journal checkpoints. A single nullable
 * listener check gates every call site, so attaching no listener costs
 * nothing beyond that check.
 */
public interface MemoryAccessListener {
    /** Called after a write, with the value the cell held immediately before it. */
    void onWrite(int address, boolean hadPriorValue, int priorValue, int newValue);

    /** Called after a read (bus-mediated reads only; direct/GUI reads are not observed). */
    void onRead(int address, int value);
}

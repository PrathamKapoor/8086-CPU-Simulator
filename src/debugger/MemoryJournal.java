package debugger;

import memory.Memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An append-only undo log of memory writes, used to make checkpoints O(1)
 * and restores O(changes-since-checkpoint) instead of copying the whole
 * 1 MB address space. See docs/verification/phase-5-debugger-design-note.md
 * for the rationale.
 *
 * This is deliberately NOT a branching snapshot DAG: restoring to an earlier
 * position truncates everything recorded after it, so execution from that
 * point diverges onto a fresh timeline (like undo/redo in an editor), rather
 * than keeping every explored future alive forever.
 */
public final class MemoryJournal {
    private record Entry(int address, boolean hadPriorValue, int priorValue, int newValue) { }

    private final Memory memory;
    private final List<Entry> entries = new ArrayList<>();
    private boolean recording = true;

    public MemoryJournal(Memory memory) {
        this.memory = memory;
    }

    /** Current length — this is what an {@link ExecutionSnapshot} pins as its restore target. */
    public long position() { return entries.size(); }

    /** Called by the {@code Memory} listener installed by {@code DebugSession}. */
    void recordWrite(int address, boolean hadPriorValue, int priorValue, int newValue) {
        if (recording) entries.add(new Entry(address, hadPriorValue, priorValue, newValue));
    }

    /**
     * Net per-address change across a forward range of the journal
     * {@code [fromPosition, toPosition)}: the value each touched address had
     * immediately before {@code fromPosition} and the value it holds at
     * {@code toPosition}. Requires {@code fromPosition <= toPosition <=} the
     * journal's current length (i.e. no restore truncated the range away).
     */
    public Map<Integer, int[]> netChanges(long fromPosition, long toPosition) {
        if (fromPosition < 0 || toPosition < fromPosition || toPosition > entries.size()) {
            throw new IllegalArgumentException("invalid journal diff range [" + fromPosition + "," + toPosition
                + "] against length " + entries.size());
        }
        Map<Integer, int[]> changes = new LinkedHashMap<>();
        for (long i = fromPosition; i < toPosition; i++) {
            Entry e = entries.get((int) i);
            int[] existing = changes.get(e.address());
            if (existing == null) {
                changes.put(e.address(), new int[] { e.priorValue(), e.newValue() });
            } else {
                existing[1] = e.newValue();
            }
        }
        return changes;
    }

    /** Undo writes back to an earlier position, in reverse order. O(entries removed). */
    public void rewindTo(long targetPosition) {
        if (targetPosition < 0 || targetPosition > entries.size()) {
            throw new IllegalArgumentException("journal position " + targetPosition + " outside [0," + entries.size() + "]");
        }
        boolean wasRecording = recording;
        recording = false; // undoing writes must not themselves be journaled
        try {
            for (int i = entries.size() - 1; i >= targetPosition; i--) {
                Entry e = entries.remove(i);
                if (e.hadPriorValue()) memory.getCell(e.address()).write(e.priorValue());
                else memory.getCell(e.address()).clear();
            }
        } finally {
            recording = wasRecording;
        }
    }

    /** Number of writes currently retained (i.e. undoable). */
    public int entryCount() { return entries.size(); }
}

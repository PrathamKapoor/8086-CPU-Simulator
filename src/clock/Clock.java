package clock;

import java.util.ArrayList;
import java.util.List;

/**
 * Clock — simulates the CPU clock signal.
 * Each tick represents one clock cycle; one micro-operation executes per tick.
 * GUI components register as ClockListeners to update on every tick.
 */
public class Clock {

    public interface ClockListener {
        void onTick(long cycleCount);
    }

    private long cycleCount = 0;
    private final List<ClockListener> listeners = new ArrayList<>();

    public void addListener(ClockListener l) {
        listeners.add(l);
    }

    public void removeListener(ClockListener l) {
        listeners.remove(l);
    }

    /** Advance one clock cycle and notify all registered listeners. */
    public void tick() {
        cycleCount++;
        for (ClockListener l : listeners) l.onTick(cycleCount);
    }

    public long getCycleCount() { return cycleCount; }

    public void reset() {
        cycleCount = 0;
    }

    @Override
    public String toString() { return "CLK=" + cycleCount; }
}

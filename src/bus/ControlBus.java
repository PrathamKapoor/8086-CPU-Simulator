package bus;

import java.util.EnumSet;
import java.util.Set;

/**
 * ControlBus — carries control signals from the Control Unit to all components.
 * Signals are asserted for one clock cycle then cleared before the next micro-op.
 */
public class ControlBus {
    private final Set<ControlSignal> activeSignals = EnumSet.noneOf(ControlSignal.class);

    public void assert_(ControlSignal signal) {
        activeSignals.add(signal);
    }

    public void deassert(ControlSignal signal) {
        activeSignals.remove(signal);
    }

    public boolean isAsserted(ControlSignal signal) {
        return activeSignals.contains(signal);
    }

    public Set<ControlSignal> getActiveSignals() {
        return activeSignals.isEmpty()
                ? EnumSet.noneOf(ControlSignal.class)
                : EnumSet.copyOf(activeSignals);
    }

    public void clearAll() {
        activeSignals.clear();
    }

    @Override
    public String toString() {
        return activeSignals.isEmpty() ? "CTRL=<idle>" : "CTRL=" + activeSignals;
    }
}

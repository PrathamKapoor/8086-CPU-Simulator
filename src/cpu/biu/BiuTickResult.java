package cpu.biu;

/** Result of one arbitration attempt; no timing claim beyond the documented model. */
public record BiuTickResult(boolean fetched, boolean waitingForBus, boolean queueFull,
                            int physicalAddress, int value) { }

package cpu.microarchitecture;

/** Explicit BIU/EU states; queue conditions are not counted as generic stalls. */
public enum UnitState {
    EU_ACTIVE, EU_WAITING_FOR_QUEUE, BIU_ACTIVE, BIU_WAITING_FOR_BUS,
    QUEUE_EMPTY, QUEUE_FULL, IDLE, CONTROL_TRANSFER_FLUSH
}

package debugger;

/** Why a debug session's run/continue/step call returned control to the caller. */
public enum StopReason {
    BREAKPOINT,
    WATCHPOINT,
    SINGLE_STEP,
    TERMINATION,
    INVALID_INSTRUCTION,
    EXCEPTION_TRAP,
    USER_PAUSE,
    EXECUTION_LIMIT,
    REPLAY_DIVERGENCE,
    NOT_STARTED
}

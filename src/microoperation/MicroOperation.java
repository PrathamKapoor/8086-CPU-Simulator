package microoperation;

/**
 * MicroOperation — a single RTL-level data transfer or computation.
 *
 * Each micro-op encapsulates:
 *   - type          : what category of transfer/computation
 *   - rtlDescription: human-readable RTL notation, e.g. "MAR <- PC"
 *   - action        : Java Runnable that performs the actual state change
 *   - destReg       : name of the destination register/resource (for GUI highlight)
 *   - srcReg        : name of the source register/resource (for GUI highlight)
 *   - busActivity   : which buses are active (for GUI animation)
 *
 * One micro-op executes per clock cycle.
 */
public class MicroOperation {

    public enum BusActivity {
        NONE, ADDRESS_BUS, DATA_BUS, CONTROL_BUS, ADDRESS_AND_DATA
    }

    private final MicroOperationType type;
    private final String             rtlDescription;
    private final Runnable           action;
    private final String             destReg;
    private final String             srcReg;
    private final BusActivity        busActivity;

    public MicroOperation(MicroOperationType type,
                          String             rtlDescription,
                          Runnable           action,
                          String             destReg,
                          String             srcReg,
                          BusActivity        busActivity) {
        this.type           = type;
        this.rtlDescription = rtlDescription;
        this.action         = action;
        this.destReg        = destReg;
        this.srcReg         = srcReg;
        this.busActivity    = busActivity;
    }

    /** Execute this micro-operation (one clock cycle). */
    public void execute() {
        action.run();
    }

    public MicroOperationType getType()         { return type; }
    public String             getRtlDescription(){ return rtlDescription; }
    public String             getDestReg()       { return destReg; }
    public String             getSrcReg()        { return srcReg; }
    public BusActivity        getBusActivity()   { return busActivity; }

    @Override
    public String toString() { return rtlDescription; }
}

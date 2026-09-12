package bus;

/**
 * ControlSignal — every control signal the Control Unit can assert on the Control Bus.
 * Each signal gates a specific data transfer or hardware action for one clock cycle.
 */
public enum ControlSignal {
    MEMORY_READ,             // Memory drives DataBus from Memory[AddressBus]
    MEMORY_WRITE,            // Memory latches DataBus into Memory[AddressBus]
    REGISTER_LOAD,           // Destination register latches DataBus value
    REGISTER_OUTPUT_ENABLE,  // Source register drives DataBus
    ALU_ENABLE,              // ALU performs its operation
    PC_INCREMENT,            // PC <- PC + 1
    IR_LOAD,                 // IR latches MDR value
    MAR_LOAD,                // MAR latches address
    MDR_LOAD,                // MDR latches data
    PC_LOAD,                 // PC <- DataBus (for JMP/JZ/CALL/RET)
    SP_DECREMENT,            // SP <- SP - 1 (for PUSH)
    SP_INCREMENT,            // SP <- SP + 1 (for POP)
    INTERRUPT_ACKNOWLEDGE,   // CPU acknowledges interrupt (INTA cycle)
    SEGMENT_LOAD,            // Segment register latches DataBus value
    SEGMENT_OUTPUT_ENABLE,   // Segment register drives DataBus
    FLAGS_LOAD,              // FLAGS register latches DataBus value
    FLAGS_OUTPUT_ENABLE,     // FLAGS register drives DataBus
    IO_READ,                 // I/O port drives DataBus (IN instruction)
    IO_WRITE,                // DataBus drives I/O port (OUT instruction)
    HALT                     // Processor halted
}

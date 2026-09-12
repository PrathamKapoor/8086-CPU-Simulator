package isa;

/**
 * ExceptionType — architectural exceptions that can occur during execution.
 */
public enum ExceptionType {
    NONE("No exception"),
    DIVIDE_BY_ZERO("Divide by zero"),
    DIVIDE_OVERFLOW("Divide overflow (quotient > operand width)"),
    INVALID_OPCODE("Invalid/undefined opcode"),
    INVALID_INSTRUCTION("Illegal instruction for this processor mode"),
    INVALID_OPERAND("Invalid operand combination"),
    SEGMENT_OVERRIDE("Illegal segment override"),
    STACK_OVERFLOW("Stack overflow (SP wraps around)"),
    STACK_UNDERFLOW("Stack underflow (SP wraps around)"),
    BREAKPOINT("Breakpoint (INT 3)"),
    OVERFLOW("Overflow (INTO)"),
    BOUND_RANGE("Bound range exceeded (BOUND)"),
    INVALID_STRING_OP("Invalid string operation");

    private final String description;
    ExceptionType(String description) { this.description = description; }
    public String getDescription() { return description; }
}

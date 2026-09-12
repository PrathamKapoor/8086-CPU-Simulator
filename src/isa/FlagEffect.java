package isa;

/**
 * FlagEffect — how an instruction affects a specific flag.
 */
public enum FlagEffect {
    CF_MODIFY,    // Carry flag may be modified
    PF_MODIFY,    // Parity flag may be modified
    AF_MODIFY,    // Auxiliary carry flag may be modified
    ZF_MODIFY,    // Zero flag may be modified
    SF_MODIFY,    // Sign flag may be modified
    OF_MODIFY,    // Overflow flag may be modified
    DF_MODIFY,    // Direction flag may be modified (CLD/STD)
    IF_MODIFY,    // Interrupt flag may be modified (CLI/STI)
    TF_MODIFY,    // Trap flag may be modified
    OF_CLEAR,     // OF set to 0
    CF_CLEAR,     // CF set to 0
    CF_SET,       // CF set to 1
    CF_COMPLEMENT,// CF inverted
    DF_CLEAR,     // DF set to 0 (CLD)
    DF_SET,       // DF set to 1 (STD)
    IF_CLEAR,     // IF set to 0 (CLI)
    IF_SET,       // IF set to 1 (STI)
    TF_CLEAR,     // TF set to 0
    UNDEFINED     // Flag value is undefined after this instruction
}

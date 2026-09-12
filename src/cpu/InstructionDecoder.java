package cpu;

import instruction.Instruction;
import instruction.Opcode;

/**
 * InstructionDecoder — extracts opcode and operand fields from the current instruction.
 */
public class InstructionDecoder {

    private Instruction currentInstruction;

    public void decode(Instruction instruction) {
        this.currentInstruction = instruction;
    }

    public Instruction getCurrentInstruction() { return currentInstruction; }

    public Opcode getOpcode() {
        return currentInstruction != null ? currentInstruction.getOpcode() : null;
    }

    public String getDecodeInfo() {
        if (currentInstruction == null) return "---";
        StringBuilder sb = new StringBuilder();
        sb.append("OPCODE=").append(currentInstruction.getOpcode());
        if (currentInstruction.getDestReg() != null)
            sb.append("  DST=").append(currentInstruction.getDestReg());
        if (currentInstruction.getSrcReg() != null)
            sb.append("  SRC=").append(currentInstruction.getSrcReg());
        if (currentInstruction.getFormat().name().contains("IMM"))
            sb.append("  IMM=").append(currentInstruction.getImmediate());
        if (currentInstruction.getFormat().name().contains("ADDR"))
            sb.append("  ADDR=").append(currentInstruction.getAddress());
        return sb.toString();
    }

    public void reset() {
        currentInstruction = null;
    }
}

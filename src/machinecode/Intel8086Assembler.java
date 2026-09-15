package machinecode;

import instruction.Instruction;
import instruction.Opcode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Two-pass assembler bridge from parsed semantic instructions to a byte stream. */
public final class Intel8086Assembler {
    private final Intel8086Encoder encoder = new Intel8086Encoder();

    public AssembledProgram assemble(List<Instruction> source) {
        if (source == null) throw new IllegalArgumentException("source instructions must not be null");
        List<Integer> offsets = initialOffsets(source);
        List<EncodedInstruction> encoded = List.of();
        for (int pass = 0; pass < 8; pass++) {
            encoded = new ArrayList<>(source.size());
            List<Integer> nextOffsets = new ArrayList<>(source.size());
            int offset = 0;
            for (int index = 0; index < source.size(); index++) {
                nextOffsets.add(offset);
                Instruction instruction = source.get(index);
                int target = relativeTarget(instruction) ? targetOffset(instruction, offsets) : instruction.getAddress();
                EncodedInstruction result = encoder.encode(relativeTarget(instruction) ? copyWithAddress(instruction, target) : instruction, offset);
                encoded.add(result);
                offset += result.length();
            }
            if (nextOffsets.equals(offsets)) break;
            offsets = nextOffsets;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (EncodedInstruction instruction : encoded) bytes.writeBytes(instruction.bytes());
        return new AssembledProgram(bytes.toByteArray(), offsets, source);
    }

    private static List<Integer> initialOffsets(List<Instruction> source) {
        List<Integer> offsets = new ArrayList<>(source.size());
        int offset = 0;
        for (Instruction instruction : source) {
            offsets.add(offset);
            offset += initialLength(instruction);
        }
        return offsets;
    }

    private static int initialLength(Instruction instruction) {
        return instruction.getOpcode() == Opcode.CALL ? 3 : (relativeTarget(instruction) ? 2 : 1);
    }

    private static int targetOffset(Instruction instruction, List<Integer> offsets) {
        int index = instruction.getAddress();
        if (index < 0 || index >= offsets.size()) throw new EncodeException("label target instruction index outside program: " + index);
        return offsets.get(index);
    }

    private static boolean relativeTarget(Instruction instruction) {
        return switch (instruction.getOpcode()) {
            case JMP, CALL, JZ_JE, JNZ_JNE, JC_JB, JNC_JNB, JO, JNO, JS, JNS,
                 JP_JPE, JNP_JPO, JL_JNGE, JNL_JGE, JLE_JNG, JNLE_JG, JBE_JNA,
                 JNBE_JA, LOOP, LOOPZ, LOOPNZ, JCXZ -> true;
            default -> false;
        };
    }

    private static Instruction copyWithAddress(Instruction instruction, int address) {
        return new Instruction.Builder(instruction.getOpcode()).format(instruction.getFormat())
            .dest(instruction.getDestReg()).src(instruction.getSrcReg()).imm(instruction.getImmediate()).addr(address)
            .raw(instruction.getRawText()).baseReg(instruction.getBaseReg()).indexReg(instruction.getIndexReg())
            .disp(instruction.getDisplacement()).segOverride(instruction.getSegmentOverride()).prefix(instruction.getPrefix()).build();
    }
}

package research;

import debugger.DebugSession;
import debugger.TraceEvent;
import instruction.Instruction;
import instruction.Opcode;

import java.util.List;
import java.util.Set;

/**
 * Branch taken/not-taken counts derived from the actual typed trace — never
 * from parsing instruction-text strings. A conditional-transfer opcode
 * (Jcc/LOOP family) "retires" (an {@code InstructionRetired} event) and
 * either is followed by a {@code ControlTransfer} event for that same
 * instruction index (taken) or is not (not taken). {@code JMP}/{@code CALL}
 * are unconditional and counted separately, since "taken/not-taken" is not a
 * meaningful distinction for them.
 */
public record BranchMetrics(long conditionalBranches, long takenBranches, long notTakenBranches,
                            long unconditionalTransfers, boolean available) {
    static final BranchMetrics UNAVAILABLE = new BranchMetrics(0, 0, 0, 0, false);

    private static final Set<Opcode> CONDITIONAL = Set.of(
        Opcode.JZ_JE, Opcode.JNZ_JNE, Opcode.JC_JB, Opcode.JNC_JNB, Opcode.JO, Opcode.JNO,
        Opcode.JS, Opcode.JNS, Opcode.JP_JPE, Opcode.JNP_JPO, Opcode.JL_JNGE, Opcode.JNL_JGE,
        Opcode.JLE_JNG, Opcode.JNLE_JG, Opcode.JB_JNAE, Opcode.JBE_JNA, Opcode.JNBE_JA,
        Opcode.LOOP, Opcode.LOOPZ, Opcode.LOOPNZ, Opcode.JCXZ
    );
    private static final Set<Opcode> UNCONDITIONAL = Set.of(Opcode.JMP, Opcode.CALL);

    public static BranchMetrics from(DebugSession session) {
        List<TraceEvent> trace = session.trace();
        List<Instruction> program = session.cpu().getProgram();
        long conditional = 0, taken = 0, unconditional = 0;
        // A ControlTransfer event, when present, is always emitted immediately after
        // the InstructionRetired event for that exact occurrence (see DebugSession.
        // stepMicroOp), so per-occurrence correlation is a simple adjacent-event
        // check -- correlating by index alone would wrongly mark every retirement of
        // a repeatedly-executed branch (e.g. a LOOP) as "taken" once any one of its
        // executions took it.
        for (int i = 0; i < trace.size(); i++) {
            if (!(trace.get(i) instanceof TraceEvent.InstructionRetired retired)) continue;
            int idx = retired.instructionIndex();
            if (idx < 0 || idx >= program.size()) continue;
            Opcode opcode = program.get(idx).getOpcode();
            if (CONDITIONAL.contains(opcode)) {
                conditional++;
                boolean tookIt = i + 1 < trace.size()
                    && trace.get(i + 1) instanceof TraceEvent.ControlTransfer ct
                    && ct.fromInstructionIndex() == idx;
                if (tookIt) taken++;
            } else if (UNCONDITIONAL.contains(opcode)) {
                unconditional++;
            }
        }
        return new BranchMetrics(conditional, taken, conditional - taken, unconditional, true);
    }
}

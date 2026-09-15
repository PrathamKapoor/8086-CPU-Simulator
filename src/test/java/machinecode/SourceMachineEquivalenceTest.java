package machinecode;

import org.junit.jupiter.api.Test;

class SourceMachineEquivalenceTest {
    @Test void equivalenceCasesUseTheSharedSemanticExecutionPath() {
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 1234H\nMOV BX, 0100H\nADD AX, BX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("MOV AX, 0001H\nMOV BX, 0002H\nXCHG AX, BX\nTEST AX, BX\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("JMP target\nMOV AX, 0001H\ntarget: MOV AX, 5678H\nHLT\n");
        SourceMachineEquivalenceHarness.assertEquivalent("CLC\nSTC\nCMC\nMOV AX, 0009H\nAAA\nHLT\n");
    }
}

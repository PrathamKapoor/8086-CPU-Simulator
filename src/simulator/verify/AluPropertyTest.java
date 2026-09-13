package simulator.verify;

import cpu.ALU;
import cpu.registers.FLAGS;
import java.util.*;

/**
 * AluPropertyTest — property-based verification for ALU/flag behavior.
 *
 * Generates input pairs over the declared 8086 value space (including
 * 0x0000, 0x007F, 0x0080, 0x00FF, 0x0100, 0x7FFF, 0x8000, 0x8001,
 * 0xFFFF and boundary-creating pairs) and compares results to the
 * independent GoldenReference oracle.
 */
public final class AluPropertyTest {

    private AluPropertyTest() { }

    private static final int[] SAMPLES = {
        0, 1, 2, 0x7F, 0x80, 0xFF, 0x100, 0x7FFF, 0x8000, 0x8001, 0xFFFF
    };

    public static List<VerificationReport> runAll() {
        List<VerificationReport> reports = new ArrayList<>();
        reports.add(runAddCases());
        reports.add(runSubCases());
        reports.add(runAdcCases());
        reports.add(runSbbCases());
        reports.add(runIncCases());
        reports.add(runDecCases());
        reports.add(runNegCases());
        reports.add(runAndCases());
        reports.add(runOrCases());
        reports.add(runXorCases());
        reports.add(runShlCases());
        reports.add(runShrCases());
        reports.add(runSarCases());
        reports.add(runRolCases());
        reports.add(runRorCases());
        reports.add(runRclCases());
        reports.add(runRcrCases());
        return reports;
    }

    private static boolean compareResult(ALU.Operation op, int a, int b, ALU alu, FLAGS flags, Map<String, Integer> expected) {
        int result = alu.execute(op, a, b);
        int expectedResult = expected.get("AX");
        if (result != expectedResult) {
            return false;
        }
        // Compare flags where deterministically defined
        for (String key : Arrays.asList("CF", "OF", "ZF", "SF", "PF", "AF")) {
            int expVal = expected.getOrDefault(key, -1);
            if (expVal >= 0) {
                boolean actualBool = switch (key) {
                    case "CF" -> flags.isCarry();
                    case "OF" -> flags.isOverflow();
                    case "ZF" -> flags.isZero();
                    case "SF" -> flags.isSign();
                    case "PF" -> flags.isParity();
                    case "AF" -> flags.isAuxCarry();
                    default -> false;
                };
                int actualInt = actualBool ? 1 : 0;
                if (actualInt != expVal) {
                    return false;
                }
            }
        }
        return true;
    }

    private static VerificationReport runAddCases() {
        VerificationReport r = new VerificationReport();
        FLAGS flags = new FLAGS();
        ALU alu = new ALU(flags);
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                count++;
                flags.clear();
                Map<String, Integer> exp = GoldenReference.resultAdd(a, b);
                if (!compareResult(ALU.Operation.ADD, a, b, alu, flags, exp)) {
                    r.addFail("ADD FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b)
                        + " result=" + String.format("%04X", alu.getLastResult()));
                } else {
                    r.addPass();
                }
            }
        }
        r.note("ADD samples: " + count + " pairs checked against GoldenReference");
        return r;
    }

    private static VerificationReport runSubCases() {
        VerificationReport r = new VerificationReport();
        FLAGS flags = new FLAGS();
        ALU alu = new ALU(flags);
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                count++;
                flags.clear();
                Map<String, Integer> exp = GoldenReference.resultSub(a, b);
                if (!compareResult(ALU.Operation.SUB, a, b, alu, flags, exp)) {
                    r.addFail("SUB FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b));
                } else {
                    r.addPass();
                }
            }
        }
        r.note("SUB samples: " + count + " pairs checked");
        return r;
    }

    private static VerificationReport runAdcCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                for (boolean cf : new boolean[]{false, true}) {
                    count++;
                    FLAGS flags = new FLAGS();
                    flags.setCarry(cf);
                    ALU alu = new ALU(flags);
                    Map<String, Integer> exp = GoldenReference.resultAdc(a, b, cf);
                    if (!compareResult(ALU.Operation.ADC, a, b, alu, flags, exp)) {
                        r.addFail("ADC FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b) + " CF=" + cf);
                    } else {
                        r.addPass();
                    }
                }
            }
        }
        r.note("ADC samples: " + count + " pairs (with/without CF)");
        return r;
    }

    private static VerificationReport runSbbCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                for (boolean cf : new boolean[]{false, true}) {
                    count++;
                    FLAGS flags = new FLAGS();
                    flags.setCarry(cf);
                    ALU alu = new ALU(flags);
                    Map<String, Integer> exp = GoldenReference.resultSbb(a, b, cf);
                    if (!compareResult(ALU.Operation.SBB, a, b, alu, flags, exp)) {
                        r.addFail("SBB FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b) + " CF=" + cf);
                    } else {
                        r.addPass();
                    }
                }
            }
        }
        r.note("SBB samples: " + count + " pairs (with/without borrow)");
        return r;
    }

    private static VerificationReport runIncCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (boolean cf : new boolean[]{false, true}) {
                count++;
                FLAGS flags = new FLAGS();
                flags.setCarry(cf);
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultInc(a, cf);
                if (!compareResult(ALU.Operation.INC, a, 0, alu, flags, exp)) {
                    r.addFail("INC FAIL a=" + String.format("%04X", a) + " CF=" + cf);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("INC samples: " + count + " (CF preserved)");
        return r;
    }

    private static VerificationReport runDecCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (boolean cf : new boolean[]{false, true}) {
                count++;
                FLAGS flags = new FLAGS();
                flags.setCarry(cf);
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultDec(a, cf);
                if (!compareResult(ALU.Operation.DEC, a, 0, alu, flags, exp)) {
                    r.addFail("DEC FAIL a=" + String.format("%04X", a) + " CF=" + cf);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("DEC samples: " + count + " (CF preserved)");
        return r;
    }

    private static VerificationReport runNegCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            count++;
            FLAGS flags = new FLAGS();
            ALU alu = new ALU(flags);
            Map<String, Integer> exp = GoldenReference.resultNeg(a);
            if (!compareResult(ALU.Operation.NEG, a, 0, alu, flags, exp)) {
                r.addFail("NEG FAIL a=" + String.format("%04X", a));
            } else {
                r.addPass();
            }
        }
        r.note("NEG samples: " + count);
        return r;
    }

    private static VerificationReport runAndCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultAnd(a, b);
                if (!compareResult(ALU.Operation.AND, a, b, alu, flags, exp)) {
                    r.addFail("AND FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b));
                } else {
                    r.addPass();
                }
            }
        }
        r.note("AND samples: " + count);
        return r;
    }

    private static VerificationReport runOrCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultOr(a, b);
                if (!compareResult(ALU.Operation.OR, a, b, alu, flags, exp)) {
                    r.addFail("OR FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b));
                } else {
                    r.addPass();
                }
            }
        }
        r.note("OR samples: " + count);
        return r;
    }

    private static VerificationReport runXorCases() {
        VerificationReport r = new VerificationReport();
        int count = 0;
        for (int a : SAMPLES) {
            for (int b : SAMPLES) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultXor(a, b);
                if (!compareResult(ALU.Operation.XOR, a, b, alu, flags, exp)) {
                    r.addFail("XOR FAIL a=" + String.format("%04X", a) + " b=" + String.format("%04X", b));
                } else {
                    r.addPass();
                }
            }
        }
        r.note("XOR samples: " + count);
        return r;
    }

    private static VerificationReport runShlCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultShl(a, c);
                if (!compareResult(ALU.Operation.SHL, a, c, alu, flags, exp)) {
                    r.addFail("SHL FAIL a=" + String.format("%04X", a) + " c=" + c);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("SHL samples: " + count);
        return r;
    }

    private static VerificationReport runShrCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultShr(a, c);
                if (!compareResult(ALU.Operation.SHR, a, c, alu, flags, exp)) {
                    r.addFail("SHR FAIL a=" + String.format("%04X", a) + " c=" + c);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("SHR samples: " + count);
        return r;
    }

    private static VerificationReport runSarCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultSar(a, c);
                if (!compareResult(ALU.Operation.SAR, a, c, alu, flags, exp)) {
                    r.addFail("SAR FAIL a=" + String.format("%04X", a) + " c=" + c);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("SAR samples: " + count);
        return r;
    }

    private static VerificationReport runRolCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultRol(a, c);
                if (!compareResult(ALU.Operation.ROL, a, c, alu, flags, exp)) {
                    r.addFail("ROL FAIL a=" + String.format("%04X", a) + " c=" + c);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("ROL samples: " + count);
        return r;
    }

    private static VerificationReport runRorCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                count++;
                FLAGS flags = new FLAGS();
                ALU alu = new ALU(flags);
                Map<String, Integer> exp = GoldenReference.resultRor(a, c);
                if (!compareResult(ALU.Operation.ROR, a, c, alu, flags, exp)) {
                    r.addFail("ROR FAIL a=" + String.format("%04X", a) + " c=" + c);
                } else {
                    r.addPass();
                }
            }
        }
        r.note("ROR samples: " + count);
        return r;
    }

    private static VerificationReport runRclCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                for (boolean cf : new boolean[]{false, true}) {
                    count++;
                    FLAGS flags = new FLAGS();
                    flags.setCarry(cf);
                    ALU alu = new ALU(flags);
                    Map<String, Integer> exp = GoldenReference.resultRcl(a, c, cf);
                    if (!compareResult(ALU.Operation.RCL, a, c, alu, flags, exp)) {
                        r.addFail("RCL FAIL a=" + String.format("%04X", a) + " c=" + c + " CF=" + cf);
                    } else {
                        r.addPass();
                    }
                }
            }
        }
        r.note("RCL samples: " + count + " (with/without CF)");
        return r;
    }

    private static VerificationReport runRcrCases() {
        VerificationReport r = new VerificationReport();
        int[] counts = {0, 1, 2, 7, 8, 15, 16, 17};
        int[] regs = {0x0000, 0x00FF, 0x7FFF, 0x8000, 0xFFFF};
        int count = 0;
        for (int a : regs) {
            for (int c : counts) {
                for (boolean cf : new boolean[]{false, true}) {
                    count++;
                    FLAGS flags = new FLAGS();
                    flags.setCarry(cf);
                    ALU alu = new ALU(flags);
                    Map<String, Integer> exp = GoldenReference.resultRcr(a, c, cf);
                    if (!compareResult(ALU.Operation.RCR, a, c, alu, flags, exp)) {
                        r.addFail("RCR FAIL a=" + String.format("%04X", a) + " c=" + c + " CF=" + cf);
                    } else {
                        r.addPass();
                    }
                }
            }
        }
        r.note("RCR samples: " + count + " (with/without CF)");
        return r;
    }

    public static class VerificationReport {
        public int passed = 0;
        public int failed = 0;
        private final List<String> notes = new ArrayList<>();

        public void addPass() { passed++; }
        public void addFail(String msg) { failed++; notes.add(msg); }
        public void note(String n) { notes.add(n); }
        public boolean isPass() { return failed == 0; }
        public int passed() { return passed; }
        public int failed() { return failed; }
        public List<String> notes() { return Collections.unmodifiableList(notes); }
    }
}

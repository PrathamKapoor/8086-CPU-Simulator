package simulator.verify;

import cpu.CPU;
import cpu.registers.FLAGS;
import instruction.InstructionParser;
import instruction.Instruction;

import java.nio.file.*;
import java.util.*;

public final class VectorRunner {

    public static final java.util.Set<String> FLAG_SETTERS = java.util.Set.of(
        "CF", "PF", "AF", "ZF", "SF", "TF", "IF", "DF", "OF"
    );

    private VectorRunner() { }

    public static VerificationReport runAll() {
        return runAll(List.of("src/test/resources/vectors"));
    }

    public static VerificationReport runAll(List<String> paths) {
        VerificationReport r = new VerificationReport();
        for (String pathStr : paths) {
            Path dir = Path.of(pathStr);
            if (!Files.isDirectory(dir)) {
                r.addFail("Vector directory not found: " + dir);
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                List<Path> files = new ArrayList<>();
                for (Path p : stream) files.add(p);
                files.sort(Comparator.comparing(p -> p.getFileName().toString()));
                for (Path p : files) {
                    String text = Files.readString(p);
                    List<ArchVector> vectors = VectorJson.parseAll(text, p.getFileName().toString());
                    for (ArchVector v : vectors) {
                        try {
                            boolean ok = runVector(v);
                            if (ok) r.addPass();
                            else r.addFail("FAIL: " + v.name + " (expected state mismatch)");
                        } catch (Exception e) {
                            r.addFail("FAIL: " + v.name + " (exception: " + e.getMessage() + ")");
                        }
                    }
                }
            } catch (Exception e) {
                r.addFail("FAIL: reading " + dir + ": " + e.getMessage());
            }
        }
        return r;
    }

    private static boolean runVector(ArchVector v) {
        try {
            InstructionParser parser = new InstructionParser();
            List<Instruction> instructions = parser.parseProgram(String.join("\n", v.program));

            CPU cpu = new CPU();
            cpu.reset();

            // Apply initial registers
            for (Map.Entry<String, Integer> e : v.initialRegs.entrySet()) {
                cpu.getRegister(e.getKey()).load(e.getValue());
            }
            // Apply initial flags
            for (Map.Entry<String, Integer> e : v.initialFlags.entrySet()) {
                String k = e.getKey();
                int val = e.getValue();
                FLAGS f = cpu.getFlags();
                switch (k) {
                    case "CF" -> f.setCarry(val == 1);
                    case "PF" -> f.setParity(val == 1);
                    case "AF" -> f.setAuxCarry(val == 1);
                    case "ZF" -> f.setZero(val == 1);
                    case "SF" -> f.setSign(val == 1);
                    case "TF" -> f.setTrap(val == 1);
                    case "IF" -> f.setInterrupt(val == 1);
                    case "DF" -> f.setDirection(val == 1);
                    case "OF" -> f.setOverflow(val == 1);
                }
            }
            // Apply initial memory
            for (Map.Entry<Integer, Integer> e : v.initialMem.entrySet()) {
                cpu.getMemory().directWrite(e.getKey(), e.getValue());
            }

            cpu.loadProgram(instructions);

            int limit = 500;
            int it = 0;
            while (!cpu.isHalted() && cpu.getCurrentMicroOp() != null && it++ < limit) {
                cpu.step();
            }

            // Compare expected registers (partial assertion)
            for (Map.Entry<String, Integer> e : v.expectedRegs.entrySet()) {
                int actual = cpu.getRegister(e.getKey()).output();
                if (actual != e.getValue()) {
                    System.out.println("VECTOR FAIL " + v.name + ": reg " + e.getKey()
                        + " expected " + String.format("0x%04X", e.getValue())
                        + " got " + String.format("0x%04X", actual));
                    return false;
                }
            }

            // Compare expected flags (partial assertion)
            for (Map.Entry<String, Integer> e : v.expectedFlags.entrySet()) {
                String k = e.getKey();
                int val = e.getValue();
                boolean actualBool = false;
                FLAGS f = cpu.getFlags();
                switch (k) {
                    case "CF" -> actualBool = f.isCarry();
                    case "PF" -> actualBool = f.isParity();
                    case "AF" -> actualBool = f.isAuxCarry();
                    case "ZF" -> actualBool = f.isZero();
                    case "SF" -> actualBool = f.isSign();
                    case "TF" -> actualBool = f.isTrap();
                    case "IF" -> actualBool = f.isInterrupt();
                    case "DF" -> actualBool = f.isDirection();
                    case "OF" -> actualBool = f.isOverflow();
                    default -> { }
                }
                int actualInt = actualBool ? 1 : 0;
                if (actualInt != val) {
                    System.out.println("VECTOR FAIL " + v.name + ": flag " + k
                        + " expected " + val + " got " + actualInt);
                    return false;
                }
            }

            // Compare expected memory writes (partial assertion)
            for (Map.Entry<Integer, Integer> e : v.expectedMem.entrySet()) {
                int actual = cpu.getMemory().directRead(e.getKey());
                if (actual != e.getValue()) {
                    System.out.println("VECTOR FAIL " + v.name + ": mem ["
                        + String.format("0x%04X", e.getKey()) + "] expected "
                        + String.format("0x%04X", e.getValue())
                        + " got " + String.format("0x%04X", actual));
                    return false;
                }
            }

            if (v.expectedHalted && !cpu.isHalted()) {
                System.out.println("VECTOR FAIL " + v.name + ": expected halted but is not halted");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("VECTOR EXCEPTION " + v.name + ": " + e.getMessage());
            return false;
        }
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

        public String summary() {
            return "PASS: " + passed + "/" + (passed + failed)
                + " (failures: " + failed + ")";
        }
    }
}

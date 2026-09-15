package simulator;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MainSimulatorCliTest {
    private record Result(int code, String output) { }
    private Result run(String... args) throws IOException, InterruptedException {
        var command = new java.util.ArrayList<String>();
        command.add(System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java");
        command.add("-cp"); command.add(System.getProperty("java.class.path")); command.add("simulator.MainSimulator");
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }
    @Test void benchmark_json_is_machine_readable_and_repeatable() throws Exception {
        Result first = run("--benchmark", "--json"), second = run("--benchmark", "--json");
        assertEquals(0, first.code()); assertEquals(first.output(), second.output());
        assertTrue(first.output().trim().startsWith("[")); assertTrue(first.output().contains("queue-friendly-sequential"));
    }
    @Test void timing_trace_and_profile_keep_original_file_invocation_working() throws Exception {
        Result result = run("benchmark/queue-friendly-sequential.asm", "--timing=simplified-8086", "--trace", "--profile", "--json");
        assertEquals(0, result.code()); assertTrue(result.output().contains("\"timing\":\"SIMPLIFIED_8086\""));
        assertTrue(result.output().contains("\"events\"")); assertTrue(result.output().contains("Simulation complete."));
    }
    @Test void invalid_timing_fails() throws Exception { assertNotEquals(0, run("--timing=wrong").code()); }
}

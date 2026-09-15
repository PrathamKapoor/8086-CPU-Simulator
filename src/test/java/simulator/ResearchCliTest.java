package simulator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResearchCliTest {
    private String run(List<String> commands) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ResearchCli.runScript(commands, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test void listShowsEveryCatalogEntry() {
        String out = run(List.of("experiment list"));
        assertTrue(out.contains("sequential-alu"));
        assertTrue(out.contains("taken-branch"));
    }

    @Test void showPrintsExperimentDetails() {
        String out = run(List.of("experiment show sequential-alu"));
        assertTrue(out.contains("id=sequential-alu"));
        assertTrue(out.contains("workloadKind=SOURCE"));
    }

    @Test void runPrintsPassedAndMetrics() {
        String out = run(List.of("experiment run sequential-alu"));
        assertTrue(out.contains("passed=true"));
        assertTrue(out.contains("execution.instructionsRetired"));
    }

    @Test void runJsonPrintsValidJson() {
        String out = run(List.of("experiment run sequential-alu --json"));
        assertTrue(out.trim().startsWith("{\"experimentId\""));
    }

    @Test void compareProducesEvidenceReportLines() {
        String out = run(List.of("experiment compare taken-branch not-taken-branch"));
        assertTrue(out.contains("cycles"));
    }

    @Test void repeatReportsIdenticalAndStats() {
        String out = run(List.of("experiment repeat loop-workload 3"));
        assertTrue(out.contains("allIdentical=true"));
        assertTrue(out.contains("spread=0"));
    }

    @Test void exportToJsonFileAndCsvFile() throws Exception {
        Path jsonFile = Files.createTempFile("phase6-export", ".json");
        Path csvFile = Files.createTempFile("phase6-export", ".csv");
        try {
            run(List.of("experiment export sequential-alu " + jsonFile));
            run(List.of("experiment export sequential-alu " + csvFile));
            String json = Files.readString(jsonFile);
            String csv = Files.readString(csvFile);
            assertTrue(json.startsWith("{\"experimentId\""));
            assertTrue(csv.startsWith("experimentId,resultHash,passed"));
            assertTrue(csv.contains("execution.instructionsRetired"));
        } finally {
            Files.deleteIfExists(jsonFile);
            Files.deleteIfExists(csvFile);
        }
    }

    @Test void analyzePrintsCycleBreakdown() {
        String out = run(List.of("experiment analyze queue-starvation"));
        assertTrue(out.contains("total="));
        assertTrue(out.contains("stalledCycleCount="));
    }

    @Test void batchProducesAMatrixJson() {
        String out = run(List.of("experiment batch sequential-alu,loop-workload FUNCTIONAL,SIMPLIFIED_8086"));
        assertTrue(out.contains("\"workloadAxis\""));
        assertTrue(out.contains("\"cells\""));
    }

    @Test void unknownExperimentIdReportsAnErrorRatherThanCrashing() {
        String out = run(List.of("experiment show does-not-exist"));
        assertTrue(out.contains("error:"));
    }
}

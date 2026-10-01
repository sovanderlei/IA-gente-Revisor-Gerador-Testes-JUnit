package com.example.testagent.output;

import com.example.testagent.model.AnalysisResult;
import com.example.testagent.model.TestScenario;
import com.example.testagent.model.Vulnerability;
import com.example.testagent.model.WeakPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGeneratorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private ReportGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ReportGenerator(FIXED_CLOCK);
    }

    private static AnalysisResult sampleAnalysis() {
        return new AnalysisResult(
                "UserService",
                List.of(new WeakPoint(15, "Validação de entrada", "high", "id | não validado", "Validar null")),
                List.of(new Vulnerability(19, "Null Dereference", "MEDIUM", "username nulo", "CWE-476", "Objects.requireNonNull")),
                List.of(new TestScenario("findById", "id nulo", "NULL", "Deve lançar exceção"),
                        new TestScenario("deleteUser", "username válido", "HAPPY", "Remove o usuário")),
                45);
    }

    @Test
    @DisplayName("Gera header com nome da classe, score e data")
    void should_generateHeader_when_analysisProvided() {
        String report = generator.generate(sampleAnalysis());

        assertTrue(report.contains("`UserService`"));
        assertTrue(report.contains("45/100 🔴"));
        assertTrue(report.contains("2026-10-01 12:00:00"));
    }

    @Test
    @DisplayName("Gera tabela de pontos fracos com pipes escapados e severidade normalizada")
    void should_generateWeakPointsTable_when_weakPointsPresent() {
        String report = generator.generate(sampleAnalysis());

        assertTrue(report.contains("| Linha | Categoria | Severidade | Descrição | Sugestão |"));
        assertTrue(report.contains("| 15 | Validação de entrada | 🟠 HIGH | id \\| não validado | Validar null |"));
    }

    @Test
    @DisplayName("Gera tabela de vulnerabilidades com CWE")
    void should_generateVulnerabilitiesTable_when_vulnerabilitiesPresent() {
        String report = generator.generate(sampleAnalysis());

        assertTrue(report.contains("| Linha | Tipo | Severidade | CWE | Descrição | Correção |"));
        assertTrue(report.contains("| 19 | Null Dereference | 🟡 MEDIUM | CWE-476 | username nulo | Objects.requireNonNull |"));
    }

    @Test
    @DisplayName("Gera checklist de cenários de teste")
    void should_generateChecklist_when_scenariosPresent() {
        String report = generator.generate(sampleAnalysis());

        assertTrue(report.contains("- [ ] **findById** — `id nulo` [NULL]: Deve lançar exceção"));
        assertTrue(report.contains("- [ ] **deleteUser** — `username válido` [HAPPY]: Remove o usuário"));
    }

    @Test
    @DisplayName("Exibe mensagens quando não há itens e não deixa placeholders")
    void should_showEmptyMessages_when_listsEmpty() {
        String report = generator.generate(new AnalysisResult("Clean", List.of(), List.of(), List.of(), 95));

        assertTrue(report.contains("_Nenhum ponto fraco identificado._"));
        assertTrue(report.contains("_Nenhuma vulnerabilidade identificada._"));
        assertTrue(report.contains("_Nenhum cenário de teste recomendado._"));
        assertTrue(report.contains("95/100 🟢"));
        assertFalse(report.contains("{{"), "nenhum placeholder deve sobrar");
    }

    @Test
    @DisplayName("Usa template customizado quando fornecido")
    void should_useCustomTemplate_when_provided() {
        ReportGenerator custom = new ReportGenerator(FIXED_CLOCK, "{{class_name}}={{overall_score}}");

        assertEquals("UserService=45", custom.generate(sampleAnalysis()));
    }

    @Test
    @DisplayName("Grava o relatório em disco criando diretórios")
    void should_writeReportToFile_when_pathProvided() throws IOException {
        Path target = tempDir.resolve("nested/dir/analysis_report.md");

        Path written = generator.writeToFile(target.toString(), sampleAnalysis());

        assertTrue(Files.exists(written));
        assertEquals(generator.generate(sampleAnalysis()), Files.readString(written, StandardCharsets.UTF_8));
    }
}

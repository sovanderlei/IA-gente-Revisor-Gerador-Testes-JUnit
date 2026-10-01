package com.example.testagent.output;

import com.example.testagent.model.AnalysisResult;
import com.example.testagent.model.TestScenario;
import com.example.testagent.model.Vulnerability;
import com.example.testagent.model.WeakPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gera o relatório Markdown da análise a partir de {@code templates/report_template.md}
 * (ou de um template inline, caso o recurso não exista).
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    public static final String TEMPLATE_RESOURCE = "/templates/report_template.md";
    public static final String DEFAULT_REPORT_FILE_NAME = "analysis_report.md";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String INLINE_TEMPLATE = """
            # Relatório de Análise — `{{class_name}}`

            **Score geral:** {{overall_score}}/100 {{score_badge}}
            **Gerado em:** {{generated_at}}

            ## Pontos Fracos ({{weak_points_count}})

            {{weak_points_table}}

            ## Vulnerabilidades ({{vulnerabilities_count}})

            {{vulnerabilities_table}}

            ## Cenários de Teste Recomendados ({{test_scenarios_count}})

            {{test_scenarios_checklist}}
            """;

    private final Clock clock;
    private final String template;

    public ReportGenerator() {
        this(Clock.systemDefaultZone());
    }

    public ReportGenerator(Clock clock) {
        this(clock, loadTemplate());
    }

    public ReportGenerator(Clock clock, String template) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.template = Objects.requireNonNull(template, "template");
    }

    /** Gera o conteúdo Markdown do relatório. */
    public String generate(AnalysisResult analysis) {
        Objects.requireNonNull(analysis, "analysis");

        Map<String, String> values = new LinkedHashMap<>();
        values.put("class_name", analysis.className() == null ? "?" : analysis.className());
        values.put("overall_score", String.valueOf(analysis.overallScore()));
        values.put("score_badge", scoreBadge(analysis.overallScore()));
        values.put("generated_at", ZonedDateTime.now(clock).format(DATE_FORMAT));
        values.put("weak_points_count", String.valueOf(analysis.weakPoints().size()));
        values.put("vulnerabilities_count", String.valueOf(analysis.vulnerabilities().size()));
        values.put("test_scenarios_count", String.valueOf(analysis.testScenarios().size()));
        values.put("weak_points_table", weakPointsTable(analysis.weakPoints()));
        values.put("vulnerabilities_table", vulnerabilitiesTable(analysis.vulnerabilities()));
        values.put("test_scenarios_checklist", scenariosChecklist(analysis.testScenarios()));

        String report = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            report = report.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return report;
    }

    /** Gera o relatório e grava em disco, criando diretórios se necessário. */
    public Path writeToFile(String path, AnalysisResult analysis) {
        Objects.requireNonNull(path, "path");
        Path target = Path.of(path);
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, generate(analysis), StandardCharsets.UTF_8);
            log.info("Relatório gravado em {}", target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            log.error("Erro ao gravar o relatório em {}", path, e);
            throw new UncheckedIOException("Erro ao gravar o relatório: " + path, e);
        }
    }

    static String weakPointsTable(List<WeakPoint> weakPoints) {
        if (weakPoints.isEmpty()) {
            return "_Nenhum ponto fraco identificado._";
        }
        StringBuilder sb = new StringBuilder()
                .append("| Linha | Categoria | Severidade | Descrição | Sugestão |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        for (WeakPoint wp : weakPoints) {
            sb.append("| ").append(formatLine(wp.line()))
                    .append(" | ").append(cell(wp.category()))
                    .append(" | ").append(severity(wp.severity()))
                    .append(" | ").append(cell(wp.description()))
                    .append(" | ").append(cell(wp.suggestion()))
                    .append(" |\n");
        }
        return sb.toString().stripTrailing();
    }

    static String vulnerabilitiesTable(List<Vulnerability> vulnerabilities) {
        if (vulnerabilities.isEmpty()) {
            return "_Nenhuma vulnerabilidade identificada._";
        }
        StringBuilder sb = new StringBuilder()
                .append("| Linha | Tipo | Severidade | CWE | Descrição | Correção |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");
        for (Vulnerability v : vulnerabilities) {
            sb.append("| ").append(formatLine(v.line()))
                    .append(" | ").append(cell(v.type()))
                    .append(" | ").append(severity(v.severity()))
                    .append(" | ").append(cell(v.cweId()))
                    .append(" | ").append(cell(v.description()))
                    .append(" | ").append(cell(v.remediation()))
                    .append(" |\n");
        }
        return sb.toString().stripTrailing();
    }

    static String scenariosChecklist(List<TestScenario> scenarios) {
        if (scenarios.isEmpty()) {
            return "_Nenhum cenário de teste recomendado._";
        }
        return scenarios.stream()
                .map(s -> String.format("- [ ] **%s** — `%s` [%s]: %s",
                        inline(s.method()), inline(s.scenario()), inline(s.type()), inline(s.description())))
                .collect(Collectors.joining("\n"));
    }

    static String scoreBadge(int score) {
        if (score >= 80) {
            return "🟢";
        }
        if (score >= 50) {
            return "🟡";
        }
        return "🔴";
    }

    private static String severity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "-";
        }
        String normalized = severity.trim().toUpperCase();
        String icon = switch (normalized) {
            case "CRITICAL" -> "🔴 ";
            case "HIGH" -> "🟠 ";
            case "MEDIUM" -> "🟡 ";
            case "LOW" -> "🟢 ";
            default -> "";
        };
        return icon + cell(normalized);
    }

    private static String formatLine(int line) {
        return line > 0 ? String.valueOf(line) : "-";
    }

    /** Escapa conteúdo para uma célula de tabela Markdown (pipes e quebras de linha). */
    private static String cell(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace("|", "\\|").replaceAll("\\r?\\n", "<br>").trim();
    }

    private static String inline(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("\\r?\\n", " ").trim();
    }

    private static String loadTemplate() {
        try (InputStream in = ReportGenerator.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                log.warn("Template {} não encontrado; usando template inline", TEMPLATE_RESOURCE);
                return INLINE_TEMPLATE;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Erro ao ler o template " + TEMPLATE_RESOURCE, e);
        }
    }
}

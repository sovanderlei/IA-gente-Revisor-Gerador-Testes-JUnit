package com.example.testagent.cli;

import com.example.testagent.core.KernelFactory;
import com.example.testagent.core.PromptChainExecutor;
import com.example.testagent.model.AnalysisResult;
import com.example.testagent.output.ReportGenerator;
import com.example.testagent.parser.SourceFileReader;
import com.example.testagent.parser.SourceMetadata;
import com.example.testagent.parser.SourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code analyze}: lê o arquivo, executa o Prompt 1 e gera o relatório Markdown.
 */
@Command(
        name = "analyze",
        mixinStandardHelpOptions = true,
        description = "Analisa um arquivo .java e gera um relatório Markdown com pontos fracos e vulnerabilidades.")
public class AnalyzeCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeCommand.class);

    @Spec
    CommandSpec spec;

    @Option(names = "--source", required = true, description = "Caminho do arquivo .java a analisar.")
    String source;

    @Option(names = "--output", defaultValue = "./output",
            description = "Diretório de saída (padrão: ${DEFAULT-VALUE}).")
    String output;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        try {
            String sourceCode = new SourceFileReader().read(source);
            SourceMetadata metadata = new SourceParser().parse(sourceCode);
            out.println("📊 Analisando: " + Path.of(source).getFileName());

            PromptChainExecutor executor = new PromptChainExecutor(KernelFactory.createKernel());
            AnalysisResult analysis = executor.analyze(sourceCode, metadata);
            printAnalysisSummary(out, analysis);

            Path report = new ReportGenerator().writeToFile(
                    Path.of(output, ReportGenerator.DEFAULT_REPORT_FILE_NAME).toString(), analysis);
            out.println("📝 Relatório salvo em: " + report);
            return 0;
        } catch (IllegalArgumentException e) {
            // Erro de entrada do usuário: mensagem clara, sem stack trace no console
            log.warn("Entrada inválida no comando analyze: {}", e.getMessage());
            spec.commandLine().getErr().println("❌ Erro: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            log.error("Falha no comando analyze", e);
            spec.commandLine().getErr().println("❌ Erro: " + e.getMessage());
            return 1;
        }
    }

    static void printAnalysisSummary(PrintWriter out, AnalysisResult analysis) {
        out.println("✅ Análise concluída (Score: " + analysis.overallScore() + "/100)");
        out.println("🔍 " + analysis.weakPoints().size() + " pontos fracos encontrados");
        out.println("🚨 " + analysis.vulnerabilities().size() + " vulnerabilidades encontradas");
    }
}

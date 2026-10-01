package com.example.testagent.cli;

import com.example.testagent.core.KernelFactory;
import com.example.testagent.core.PromptChainExecutor;
import com.example.testagent.model.AnalysisResult;
import com.example.testagent.model.TestSuite;
import com.example.testagent.output.ReportGenerator;
import com.example.testagent.output.TestFileWriter;
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
 * {@code full}: análise + relatório + geração de testes, reaproveitando a mesma análise
 * (uma única chamada ao Prompt 1).
 */
@Command(
        name = "full",
        mixinStandardHelpOptions = true,
        description = "Executa análise completa (relatório Markdown) e gera os testes JUnit.")
public class FullCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(FullCommand.class);

    @Spec
    CommandSpec spec;

    @Option(names = "--source", required = true, description = "Caminho do arquivo .java.")
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
            PromptChainExecutor executor = new PromptChainExecutor(KernelFactory.createKernel());

            out.println("📊 Analisando: " + Path.of(source).getFileName());
            AnalysisResult analysis = executor.analyze(sourceCode, metadata);
            AnalyzeCommand.printAnalysisSummary(out, analysis);

            Path report = new ReportGenerator().writeToFile(
                    Path.of(output, ReportGenerator.DEFAULT_REPORT_FILE_NAME).toString(), analysis);
            out.println("📝 Relatório salvo em: " + report);

            out.println("🧪 Gerando testes...");
            TestSuite suite = executor.generateTests(sourceCode, metadata, analysis);
            out.println("✅ Testes gerados com sucesso");

            Path testFile = new TestFileWriter().write(suite, Path.of(output));
            out.println("📄 Arquivo salvo em: " + testFile);
            return 0;
        } catch (IllegalArgumentException e) {
            // Erro de entrada do usuário: mensagem clara, sem stack trace no console
            log.warn("Entrada inválida no comando full: {}", e.getMessage());
            spec.commandLine().getErr().println("❌ Erro: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            log.error("Falha no comando full", e);
            spec.commandLine().getErr().println("❌ Erro: " + e.getMessage());
            return 1;
        }
    }
}

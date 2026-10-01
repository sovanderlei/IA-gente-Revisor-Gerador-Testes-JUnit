package com.example.testagent.cli;

import com.example.testagent.core.KernelFactory;
import com.example.testagent.core.PromptChainExecutor;
import com.example.testagent.model.TestSuite;
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
 * {@code generate}: executa o prompt chain completo e grava o arquivo {@code *Test.java}.
 */
@Command(
        name = "generate",
        mixinStandardHelpOptions = true,
        description = "Gera uma classe de teste JUnit 5 + Mockito para um arquivo .java.")
public class GenerateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(GenerateCommand.class);

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

            out.println("🧪 Gerando testes para: " + Path.of(source).getFileName());
            PromptChainExecutor executor = new PromptChainExecutor(KernelFactory.createKernel());
            TestSuite suite = executor.execute(sourceCode, metadata);
            out.println("✅ Testes gerados com sucesso");

            Path testFile = new TestFileWriter().write(suite, Path.of(output));
            out.println("📄 Arquivo salvo em: " + testFile);
            return 0;
        } catch (IllegalArgumentException e) {
            // Erro de entrada do usuário: mensagem clara, sem stack trace no console
            log.warn("Entrada inválida no comando generate: {}", e.getMessage());
            spec.commandLine().getErr().println("❌ Erro: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            log.error("Falha no comando generate", e);
            spec.commandLine().getErr().println("❌ Erro: " + e.getMessage());
            return 1;
        }
    }
}

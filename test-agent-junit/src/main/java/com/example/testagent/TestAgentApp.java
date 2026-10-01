package com.example.testagent;

import ch.qos.logback.classic.Level;
import com.example.testagent.cli.AnalyzeCommand;
import com.example.testagent.cli.FullCommand;
import com.example.testagent.cli.GenerateCommand;
import com.example.testagent.core.KernelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

/**
 * Ponto de entrada da CLI {@code test-agent}.
 * <pre>
 *   test-agent analyze  --source=Arquivo.java [--output=./output]
 *   test-agent generate --source=Arquivo.java [--output=./output]
 *   test-agent full     --source=Arquivo.java [--output=./output]
 * </pre>
 */
@Command(
        name = "test-agent",
        mixinStandardHelpOptions = true,
        version = "test-agent-junit 1.0.0",
        description = "Agente revisor de código Java e gerador de testes JUnit 5 + Mockito (Semantic Kernel + OpenAI).",
        subcommands = {AnalyzeCommand.class, GenerateCommand.class, FullCommand.class})
public class TestAgentApp implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TestAgentApp.class);

    static final int EXIT_MISSING_API_KEY = 2;

    @Spec
    CommandSpec spec;

    @Option(names = {"-v", "--verbose"}, scope = CommandLine.ScopeType.INHERIT,
            description = "Exibe logs detalhados (DEBUG) no console.")
    void setVerbose(boolean verbose) {
        if (verbose) {
            configureLogging(Level.DEBUG);
        }
    }

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new TestAgentApp());
        cli.setExecutionStrategy(TestAgentApp::executeWithApiKeyCheck);
        System.exit(cli.execute(args));
    }

    /** Sem subcomando: exibe a ajuda. */
    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /**
     * Valida {@code OPENAI_API_KEY} antes de executar qualquer subcomando
     * (ajuda e versão continuam funcionando sem a chave).
     */
    private static int executeWithApiKeyCheck(ParseResult parseResult) {
        boolean helpOrVersion = CommandLine.printHelpIfRequested(parseResult);
        if (!helpOrVersion && parseResult.hasSubcommand()) {
            String apiKey = System.getenv(KernelFactory.API_KEY_ENV);
            if (apiKey == null || apiKey.isBlank()) {
                log.error("Variável de ambiente {} não definida", KernelFactory.API_KEY_ENV);
                parseResult.commandSpec().commandLine().getErr().println(
                        "❌ Variável de ambiente " + KernelFactory.API_KEY_ENV + " não definida. "
                                + "Exemplo: export " + KernelFactory.API_KEY_ENV + "=\"sk-...\"");
                return EXIT_MISSING_API_KEY;
            }
        }
        if (helpOrVersion) {
            return 0;
        }
        return new CommandLine.RunLast().execute(parseResult);
    }

    /** Ajusta o nível de log da aplicação e do appender de console. */
    static void configureLogging(Level level) {
        if (!(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context)) {
            log.warn("Logback não está ativo; nível de log não alterado");
            return;
        }
        context.getLogger("com.example.testagent").setLevel(level);
        var consoleAppender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
        if (consoleAppender != null) {
            consoleAppender.clearAllFilters();
        }
        log.debug("Logging configurado com nível {}", level);
    }
}

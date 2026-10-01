package com.example.testagent.core;

import com.example.testagent.model.AnalysisResult;
import com.example.testagent.model.TestSuite;
import com.example.testagent.parser.SourceMetadata;
import com.example.testagent.plugins.CodeAnalysisPlugin;
import com.example.testagent.plugins.CodeAnalysisPlugin.InvalidAnalysisException;
import com.example.testagent.plugins.TestGenerationPlugin;
import com.example.testagent.validator.TestValidator;
import com.azure.core.exception.HttpResponseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.semantickernel.Kernel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Orquestra o <b>prompt chaining</b>:
 * <ol>
 *     <li>Prompt 1 (análise) com {@code source_code} + {@code metadata}</li>
 *     <li>Validação do JSON retornado ({@link AnalysisResult})</li>
 *     <li>Prompt 2 (geração) com {@code source_code} + {@code analysis_json} + {@code package_name}</li>
 *     <li>Sanitização do código gerado (remoção de markdown fences)</li>
 * </ol>
 * Chamadas à IA usam retry com backoff exponencial (3 tentativas: espera de 1s e 2s entre elas).
 * Respostas com JSON malformado e erros HTTP permanentes (ex.: 401) não são retentados: falham imediatamente.
 */
public class PromptChainExecutor {

    private static final Logger log = LoggerFactory.getLogger(PromptChainExecutor.class);

    public static final int MAX_ATTEMPTS = 3;
    public static final long INITIAL_BACKOFF_MILLIS = 1_000L;

    /** Abstração de espera, permite testar o backoff sem dormir de verdade. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final CodeAnalysisPlugin analysisPlugin;
    private final TestGenerationPlugin generationPlugin;
    private final TestValidator validator;
    private final ObjectMapper mapper;
    private final Sleeper sleeper;

    public PromptChainExecutor(Kernel kernel) {
        this(new CodeAnalysisPlugin(kernel), new TestGenerationPlugin(kernel),
                new TestValidator(), CodeAnalysisPlugin.defaultMapper(), Thread::sleep);
    }

    public PromptChainExecutor(CodeAnalysisPlugin analysisPlugin,
                               TestGenerationPlugin generationPlugin,
                               TestValidator validator,
                               ObjectMapper mapper,
                               Sleeper sleeper) {
        this.analysisPlugin = Objects.requireNonNull(analysisPlugin, "analysisPlugin");
        this.generationPlugin = Objects.requireNonNull(generationPlugin, "generationPlugin");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * Executa a cadeia completa: análise + geração de testes.
     */
    public TestSuite execute(String sourceCode, SourceMetadata metadata) {
        AnalysisResult analysis = analyze(sourceCode, metadata);
        return generateTests(sourceCode, metadata, analysis);
    }

    /**
     * Passos 1 e 2: executa o prompt de análise e valida o JSON retornado.
     */
    public AnalysisResult analyze(String sourceCode, SourceMetadata metadata) {
        requireInputs(sourceCode, metadata);
        log.info("[Passo 1] Analisando a classe {}", metadata.className());
        String rawJson = withRetry("análise de código",
                () -> analysisPlugin.analyzeCode(sourceCode, metadata.toSummary()));

        log.info("[Passo 2] Validando JSON da análise");
        AnalysisResult parsed = analysisPlugin.parseAnalysis(rawJson);
        if (parsed.className() == null || parsed.className().isBlank()) {
            parsed = new AnalysisResult(metadata.className(), parsed.weakPoints(),
                    parsed.vulnerabilities(), parsed.testScenarios(), parsed.overallScore());
        }
        return parsed;
    }

    /**
     * Passos 3 e 4: gera os testes a partir da análise e sanitiza o resultado.
     */
    public TestSuite generateTests(String sourceCode, SourceMetadata metadata, AnalysisResult analysis) {
        requireInputs(sourceCode, metadata);
        Objects.requireNonNull(analysis, "analysis");

        String analysisJson = toJson(analysis);
        String packageName = metadata.packageName().isEmpty()
                ? "(pacote padrão — não declare package)"
                : metadata.packageName();

        log.info("[Passo 3] Gerando testes para {}", metadata.className());
        String rawTestCode = withRetry("geração de testes",
                () -> generationPlugin.generateTests(sourceCode, analysisJson, packageName));

        log.info("[Passo 4] Sanitizando código gerado");
        String testCode = TestGenerationPlugin.stripCodeFences(rawTestCode);

        TestValidator.ValidationResult validation = validator.validate(testCode);
        if (!validation.valid()) {
            log.warn("O teste gerado apresentou problemas de validação: {}", validation.errors());
        }

        return new TestSuite(analysis, testCode, metadata.className() + "Test.java");
    }

    /**
     * Executa a ação com retry e backoff exponencial (1s, 2s, ...).
     * {@link InvalidAnalysisException} não é retentada, pois indica resposta inválida e não falha transitória.
     */
    <T> T withRetry(String operation, Supplier<T> action) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (InvalidAnalysisException e) {
                throw e;
            } catch (RuntimeException e) {
                lastError = e;
                if (!isRetryable(e)) {
                    log.error("Falha não recuperável na {}: {}", operation, e.getMessage());
                    throw new PromptChainException("Falha não recuperável na " + operation, e);
                }
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                long delay = INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1));
                log.warn("Falha na {} (tentativa {}/{}): {}. Nova tentativa em {} ms",
                        operation, attempt, MAX_ATTEMPTS, e.getMessage(), delay);
                pause(delay);
            }
        }
        log.error("Falha definitiva na {} após {} tentativas", operation, MAX_ATTEMPTS, lastError);
        throw new PromptChainException(
                "Falha na " + operation + " após " + MAX_ATTEMPTS + " tentativas", lastError);
    }

    /**
     * Erros HTTP 4xx (chave inválida, requisição malformada, etc.) são permanentes e não devem ser retentados,
     * exceto 408 (timeout) e 429 (rate limit). Erros de rede e 5xx são transitórios.
     */
    static boolean isRetryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof HttpResponseException http && http.getResponse() != null) {
                int status = http.getResponse().getStatusCode();
                return status == 408 || status == 429 || status >= 500;
            }
        }
        return true;
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PromptChainException("Execução interrompida durante o backoff", e);
        }
    }

    private String toJson(AnalysisResult analysis) {
        try {
            return mapper.writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            throw new PromptChainException("Falha ao serializar a análise para JSON", e);
        }
    }

    private static void requireInputs(String sourceCode, SourceMetadata metadata) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("sourceCode não pode ser vazio");
        }
        Objects.requireNonNull(metadata, "metadata");
    }

    /** Falha na execução do prompt chain após esgotar as tentativas. */
    public static class PromptChainException extends RuntimeException {

        public PromptChainException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

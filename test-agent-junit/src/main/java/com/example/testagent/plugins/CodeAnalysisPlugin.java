package com.example.testagent.plugins;

import com.example.testagent.core.ContextBuilder;
import com.example.testagent.core.KernelFactory;
import com.example.testagent.model.AnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.orchestration.FunctionResult;
import com.microsoft.semantickernel.semanticfunctions.KernelFunction;
import com.microsoft.semantickernel.semanticfunctions.annotations.DefineKernelFunction;
import com.microsoft.semantickernel.semanticfunctions.annotations.KernelFunctionParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plugin do Semantic Kernel responsável pelo Prompt 1: análise de código.
 * <p>
 * Invoca a função semântica {@code analyze_code} e converte a resposta JSON
 * da IA em {@link AnalysisResult}, tolerando respostas envoltas em markdown.
 */
public class CodeAnalysisPlugin {

    private static final Logger log = LoggerFactory.getLogger(CodeAnalysisPlugin.class);

    public static final String PROMPT_NAME = "analyze_code";

    private static final Pattern JSON_FENCE =
            Pattern.compile("```(?:json|JSON)?\\s*\\n?(.*?)```", Pattern.DOTALL);

    private final Kernel kernel;
    private final KernelFunction<String> analyzeFunction;
    private final ObjectMapper mapper;

    public CodeAnalysisPlugin(Kernel kernel) {
        this(kernel, KernelFactory.loadPromptFunction(PROMPT_NAME), defaultMapper());
    }

    public CodeAnalysisPlugin(Kernel kernel, KernelFunction<String> analyzeFunction, ObjectMapper mapper) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.analyzeFunction = Objects.requireNonNull(analyzeFunction, "analyzeFunction");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** ObjectMapper tolerante a campos desconhecidos retornados pela IA. */
    public static ObjectMapper defaultMapper() {
        return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Executa o prompt de análise e retorna o JSON bruto (já extraído de eventuais markdown fences).
     *
     * @param sourceCode código-fonte Java
     * @param metadata   resumo dos metadados extraídos
     * @return JSON da análise
     */
    @DefineKernelFunction(
            name = "analyzeCode",
            description = "Analisa código Java identificando pontos fracos, vulnerabilidades e cenários de teste",
            returnType = "java.lang.String",
            returnDescription = "JSON com weak_points, vulnerabilities, test_scenarios e overall_score")
    public String analyzeCode(
            @KernelFunctionParameter(name = ContextBuilder.SOURCE_CODE, description = "Código-fonte Java")
            String sourceCode,
            @KernelFunctionParameter(name = ContextBuilder.METADATA, description = "Metadados extraídos")
            String metadata) {

        log.info("Invocando função semântica '{}'", PROMPT_NAME);
        FunctionResult<String> result = kernel.invokeAsync(analyzeFunction)
                .withArguments(ContextBuilder.create()
                        .withSourceCode(sourceCode)
                        .withMetadata(metadata)
                        .build())
                .withResultType(String.class)
                .block();

        String raw = result == null ? null : result.getResult();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("A IA retornou uma resposta vazia para a análise de código");
        }
        log.debug("Resposta bruta da análise: {}", raw);
        return extractJson(raw);
    }

    /**
     * Converte o texto retornado pela IA em {@link AnalysisResult}.
     *
     * @throws InvalidAnalysisException se o conteúdo não for um JSON válido no schema esperado
     */
    public AnalysisResult parseAnalysis(String rawResponse) {
        String json = extractJson(rawResponse);
        try {
            AnalysisResult analysis = mapper.readValue(json, AnalysisResult.class);
            log.info("Análise convertida: classe={}, score={}, {} pontos fracos, {} vulnerabilidades",
                    analysis.className(), analysis.overallScore(),
                    analysis.weakPoints().size(), analysis.vulnerabilities().size());
            return analysis;
        } catch (JsonProcessingException e) {
            log.error("JSON de análise inválido: {}", e.getOriginalMessage());
            throw new InvalidAnalysisException("JSON de análise inválido: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * Extrai o objeto JSON de uma resposta da IA, removendo markdown fences e texto ao redor.
     *
     * @throws InvalidAnalysisException se nenhum objeto JSON for encontrado
     */
    public static String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new InvalidAnalysisException("Resposta da IA vazia: nenhum JSON encontrado");
        }
        String text = rawResponse.trim();
        Matcher fence = JSON_FENCE.matcher(text);
        if (fence.find()) {
            text = fence.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidAnalysisException("Nenhum objeto JSON encontrado na resposta da IA");
        }
        return text.substring(start, end + 1);
    }

    /** Resposta da IA não pôde ser convertida em {@link AnalysisResult}. */
    public static class InvalidAnalysisException extends RuntimeException {

        public InvalidAnalysisException(String message) {
            super(message);
        }

        public InvalidAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

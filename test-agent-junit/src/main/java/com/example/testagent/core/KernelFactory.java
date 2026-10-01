package com.example.testagent.core;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.KeyCredential;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.semanticfunctions.KernelFunction;
import com.microsoft.semantickernel.semanticfunctions.KernelFunctionFromPrompt;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Cria instâncias do {@link Kernel} do Semantic Kernel e carrega as funções semânticas
 * (pares {@code *.skprompt.txt} + {@code *.config.json}) do classpath.
 */
public final class KernelFactory {

    private static final Logger log = LoggerFactory.getLogger(KernelFactory.class);

    public static final String API_KEY_ENV = "OPENAI_API_KEY";
    public static final String MODEL_ENV = "OPENAI_MODEL_ID";
    public static final String DEFAULT_MODEL_ID = "gpt-4o";

    private static final int DEFAULT_MAX_TOKENS = 4000;
    private static final double DEFAULT_TEMPERATURE = 0.3;
    private static final double DEFAULT_TOP_P = 1.0;

    private static final String PROMPTS_DIR = "/prompts/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KernelFactory() {
    }

    /**
     * Cria o Kernel usando {@code OPENAI_API_KEY} do ambiente e o modelo {@code gpt-4o}
     * (sobrescrevível via {@code OPENAI_MODEL_ID}).
     *
     * @throws IllegalStateException se a variável de ambiente não estiver definida
     */
    public static Kernel createKernel() {
        String apiKey = System.getenv(API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Variável de ambiente " + API_KEY_ENV + " não definida. Configure-a antes de executar.");
        }
        String modelId = System.getenv(MODEL_ENV);
        return createKernel(apiKey, modelId == null || modelId.isBlank() ? DEFAULT_MODEL_ID : modelId);
    }

    /**
     * Cria o Kernel com chave e modelo explícitos (útil para testes e configurações customizadas).
     */
    public static Kernel createKernel(String apiKey, String modelId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey não pode ser vazia");
        }
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId não pode ser vazio");
        }

        // Retry do SDK desabilitado: o PromptChainExecutor já aplica retry com backoff exponencial
        OpenAIAsyncClient client = new OpenAIClientBuilder()
                .credential(new KeyCredential(apiKey))
                .retryOptions(new RetryOptions(new ExponentialBackoffOptions().setMaxRetries(0)))
                .buildAsyncClient();

        ChatCompletionService chatCompletion = OpenAIChatCompletion.builder()
                .withModelId(modelId)
                .withOpenAIAsyncClient(client)
                .build();

        log.info("Kernel criado com o modelo {}", modelId);
        return Kernel.builder()
                .withAIService(ChatCompletionService.class, chatCompletion)
                .build();
    }

    /**
     * Carrega uma função semântica a partir de {@code /prompts/<name>.skprompt.txt}
     * e {@code /prompts/<name>.config.json}.
     *
     * @param name nome base do prompt (ex.: "analyze_code")
     * @return função semântica pronta para ser invocada pelo Kernel
     */
    public static KernelFunction<String> loadPromptFunction(String name) {
        String template = readResource(PROMPTS_DIR + name + ".skprompt.txt");
        JsonNode config = parseConfig(readResource(PROMPTS_DIR + name + ".config.json"), name);

        JsonNode defaults = config.path("execution_settings").path("default");
        PromptExecutionSettings settings = PromptExecutionSettings.builder()
                .withMaxTokens(defaults.path("max_tokens").asInt(DEFAULT_MAX_TOKENS))
                .withTemperature(defaults.path("temperature").asDouble(DEFAULT_TEMPERATURE))
                .withTopP(defaults.path("top_p").asDouble(DEFAULT_TOP_P))
                .build();

        log.debug("Função semântica '{}' carregada (max_tokens={}, temperature={})",
                name, settings.getMaxTokens(), settings.getTemperature());

        return KernelFunctionFromPrompt.<String>builder()
                .withName(name)
                .withDescription(config.path("description").asText(""))
                .withTemplate(template)
                .withDefaultExecutionSettings(settings)
                .build();
    }

    private static JsonNode parseConfig(String json, String name) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("config.json inválido para o prompt '" + name + "'", e);
        }
    }

    private static String readResource(String resourcePath) {
        try (InputStream in = KernelFactory.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado no classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Erro ao ler recurso " + resourcePath, e);
        }
    }
}

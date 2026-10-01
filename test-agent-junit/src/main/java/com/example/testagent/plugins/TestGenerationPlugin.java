package com.example.testagent.plugins;

import com.example.testagent.core.ContextBuilder;
import com.example.testagent.core.KernelFactory;
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
 * Plugin do Semantic Kernel responsável pelo Prompt 2: geração de testes JUnit 5 + Mockito.
 * Recebe o código-fonte e o JSON produzido pelo {@link CodeAnalysisPlugin} (prompt chaining).
 */
public class TestGenerationPlugin {

    private static final Logger log = LoggerFactory.getLogger(TestGenerationPlugin.class);

    public static final String PROMPT_NAME = "generate_tests";

    private static final Pattern CODE_FENCE =
            Pattern.compile("```[a-zA-Z]*\\s*\\n(.*?)(?:```|\\z)", Pattern.DOTALL);

    private final Kernel kernel;
    private final KernelFunction<String> generateFunction;

    public TestGenerationPlugin(Kernel kernel) {
        this(kernel, KernelFactory.loadPromptFunction(PROMPT_NAME));
    }

    public TestGenerationPlugin(Kernel kernel, KernelFunction<String> generateFunction) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
        this.generateFunction = Objects.requireNonNull(generateFunction, "generateFunction");
    }

    /**
     * Executa o prompt de geração de testes.
     *
     * @param sourceCode   código-fonte Java original
     * @param analysisJson JSON da análise (saída do Prompt 1)
     * @param packageName  pacote da classe de teste
     * @return código Java da classe de teste, sem markdown fences
     */
    @DefineKernelFunction(
            name = "generateTests",
            description = "Gera uma suíte de testes JUnit 5 + Mockito a partir do código e da análise",
            returnType = "java.lang.String",
            returnDescription = "Código Java compilável da classe de teste")
    public String generateTests(
            @KernelFunctionParameter(name = ContextBuilder.SOURCE_CODE, description = "Código-fonte Java original")
            String sourceCode,
            @KernelFunctionParameter(name = ContextBuilder.ANALYSIS_JSON, description = "JSON da análise")
            String analysisJson,
            @KernelFunctionParameter(name = ContextBuilder.PACKAGE_NAME, description = "Package do teste")
            String packageName) {

        log.info("Invocando função semântica '{}'", PROMPT_NAME);
        FunctionResult<String> result = kernel.invokeAsync(generateFunction)
                .withArguments(ContextBuilder.create()
                        .withSourceCode(sourceCode)
                        .withAnalysisJson(analysisJson)
                        .withPackageName(packageName)
                        .build())
                .withResultType(String.class)
                .block();

        String raw = result == null ? null : result.getResult();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("A IA retornou uma resposta vazia para a geração de testes");
        }
        return stripCodeFences(raw);
    }

    /**
     * Remove markdown fences (```java ... ```) e texto explicativo ao redor do bloco de código.
     * Se não houver fences, apenas remove espaços nas extremidades.
     */
    public static String stripCodeFences(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        Matcher matcher = CODE_FENCE.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1);
        }
        // Remove eventuais linhas de fence residuais
        text = text.replaceAll("(?m)^\\s*```[a-zA-Z]*\\s*$", "");
        return text.strip() + "\n";
    }
}

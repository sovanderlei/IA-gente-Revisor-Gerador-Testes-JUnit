package com.example.testagent.core;

import com.example.testagent.model.AnalysisResult;
import com.example.testagent.model.TestSuite;
import com.example.testagent.parser.SourceMetadata;
import com.example.testagent.plugins.CodeAnalysisPlugin;
import com.example.testagent.plugins.CodeAnalysisPlugin.InvalidAnalysisException;
import com.example.testagent.plugins.TestGenerationPlugin;
import com.example.testagent.validator.TestValidator;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.contextvariables.ContextVariable;
import com.microsoft.semantickernel.orchestration.FunctionInvocation;
import com.microsoft.semantickernel.orchestration.FunctionResult;
import com.microsoft.semantickernel.semanticfunctions.KernelFunction;
import com.microsoft.semantickernel.semanticfunctions.KernelFunctionArguments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptChainExecutorTest {

    private static final String SOURCE = """
            package com.example.service;
            public class UserService {
                public User findById(Long id) { return null; }
            }
            """;

    private static final String ANALYSIS_JSON = """
            {"class_name":"UserService","weak_points":[],"vulnerabilities":[],
             "test_scenarios":[{"method":"findById","scenario":"id nulo","type":"NULL","description":"x"}],
             "overall_score":60}
            """;

    private static final String GENERATED_TEST = """
            ```java
            package com.example.service;

            import org.junit.jupiter.api.Test;

            class UserServiceTest {
                @Test
                void should_returnUser_when_idExists() {
                }
            }
            ```
            """;

    @Mock
    private Kernel kernel;
    @Mock
    private KernelFunction<String> analyzeFunction;
    @Mock
    private KernelFunction<String> generateFunction;
    @Mock
    private FunctionInvocation<String> analyzeInvocation;
    @Mock
    private FunctionInvocation<String> generateInvocation;

    private final List<Long> sleeps = new ArrayList<>();
    private PromptChainExecutor executor;
    private SourceMetadata metadata;

    @BeforeEach
    void setUp() {
        CodeAnalysisPlugin analysisPlugin =
                new CodeAnalysisPlugin(kernel, analyzeFunction, CodeAnalysisPlugin.defaultMapper());
        TestGenerationPlugin generationPlugin = new TestGenerationPlugin(kernel, generateFunction);
        executor = new PromptChainExecutor(analysisPlugin, generationPlugin, new TestValidator(),
                CodeAnalysisPlugin.defaultMapper(), sleeps::add);
        metadata = new SourceMetadata("com.example.service", "UserService", List.of(),
                List.of(new SourceMetadata.MethodInfo("findById", "User", List.of("Long id"))),
                List.of(), List.of());
    }

    private void stubInvocation(KernelFunction<String> function, FunctionInvocation<String> invocation) {
        when(kernel.invokeAsync(function)).thenReturn(invocation);
        when(invocation.withArguments(any())).thenReturn(invocation);
        when(invocation.withResultType(String.class)).thenReturn(invocation);
    }

    private static FunctionResult<String> result(String value) {
        return new FunctionResult<>(ContextVariable.of(value), value);
    }

    @Test
    @DisplayName("Executa a cadeia completa encadeando o JSON da análise no prompt de geração")
    void should_chainAnalysisIntoGeneration_when_executing() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        stubInvocation(generateFunction, generateInvocation);
        when(analyzeInvocation.block()).thenReturn(result(ANALYSIS_JSON));
        when(generateInvocation.block()).thenReturn(result(GENERATED_TEST));

        TestSuite suite = executor.execute(SOURCE, metadata);

        assertEquals("UserServiceTest.java", suite.testFileName());
        assertEquals(60, suite.analysis().overallScore());
        assertFalse(suite.testCode().contains("```"), "fences devem ser removidas");
        assertTrue(suite.testCode().startsWith("package com.example.service;"));

        ArgumentCaptor<KernelFunctionArguments> captor = ArgumentCaptor.forClass(KernelFunctionArguments.class);
        verify(generateInvocation).withArguments(captor.capture());
        KernelFunctionArguments args = captor.getValue();
        assertEquals(SOURCE, args.get(ContextBuilder.SOURCE_CODE).getValue());
        assertEquals("com.example.service", args.get(ContextBuilder.PACKAGE_NAME).getValue());
        assertTrue(args.get(ContextBuilder.ANALYSIS_JSON).getValue().toString().contains("\"overall_score\":60"));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("Retenta com backoff exponencial e conclui após falhas transitórias")
    void should_retryWithBackoff_when_apiFailsTransiently() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        when(analyzeInvocation.block())
                .thenThrow(new RuntimeException("503 Service Unavailable"))
                .thenThrow(new RuntimeException("429 Too Many Requests"))
                .thenReturn(result(ANALYSIS_JSON));

        AnalysisResult analysis = executor.analyze(SOURCE, metadata);

        assertEquals("UserService", analysis.className());
        assertEquals(List.of(1000L, 2000L), sleeps);
        verify(analyzeInvocation, times(3)).block();
    }

    @Test
    @DisplayName("Lança PromptChainException após esgotar as 3 tentativas")
    void should_throwPromptChainException_when_allAttemptsFail() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        RuntimeException apiError = new RuntimeException("timeout");
        when(analyzeInvocation.block()).thenThrow(apiError);

        PromptChainExecutor.PromptChainException ex = assertThrows(
                PromptChainExecutor.PromptChainException.class,
                () -> executor.analyze(SOURCE, metadata));

        assertEquals(apiError, ex.getCause());
        assertEquals(List.of(1000L, 2000L), sleeps);
        verify(analyzeInvocation, times(PromptChainExecutor.MAX_ATTEMPTS)).block();
        verify(kernel, never()).invokeAsync(generateFunction);
    }

    @Test
    @DisplayName("Não retenta erros HTTP permanentes (401), mas retenta 429")
    void should_notRetry_when_httpErrorIsPermanent() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        HttpResponse unauthorized = mock(HttpResponse.class);
        when(unauthorized.getStatusCode()).thenReturn(401);
        when(analyzeInvocation.block()).thenThrow(new HttpResponseException("invalid_api_key", unauthorized));

        assertThrows(PromptChainExecutor.PromptChainException.class, () -> executor.analyze(SOURCE, metadata));

        verify(analyzeInvocation, times(1)).block();
        assertTrue(sleeps.isEmpty());

        HttpResponse rateLimited = mock(HttpResponse.class);
        when(rateLimited.getStatusCode()).thenReturn(429);
        assertTrue(PromptChainExecutor.isRetryable(
                new RuntimeException(new HttpResponseException("rate limit", rateLimited))));
    }

    @Test
    @DisplayName("Falha imediatamente (sem retry) quando o JSON da análise é malformado")
    void should_failWithoutRetry_when_analysisJsonIsMalformed() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        when(analyzeInvocation.block()).thenReturn(result("Desculpe, não consigo gerar JSON."));

        assertThrows(InvalidAnalysisException.class, () -> executor.execute(SOURCE, metadata));

        verify(analyzeInvocation, times(1)).block();
        verify(kernel, never()).invokeAsync(generateFunction);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("Falha quando o JSON está truncado")
    void should_throw_when_analysisJsonIsTruncated() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        when(analyzeInvocation.block()).thenReturn(result("{\"class_name\":\"UserService\",\"weak_points\":[{\"line\":1}"));

        assertThrows(InvalidAnalysisException.class, () -> executor.analyze(SOURCE, metadata));
    }

    @Test
    @DisplayName("Usa o nome da classe dos metadados quando a IA omite class_name")
    void should_fillClassName_when_analysisOmitsIt() {
        stubInvocation(analyzeFunction, analyzeInvocation);
        when(analyzeInvocation.block()).thenReturn(result("{\"overall_score\": 70}"));

        AnalysisResult analysis = executor.analyze(SOURCE, metadata);

        assertEquals("UserService", analysis.className());
    }

    @Test
    @DisplayName("Rejeita código-fonte vazio sem chamar a IA")
    void should_throw_when_sourceIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(" ", metadata));
        verify(kernel, never()).invokeAsync(any(KernelFunction.class));
    }
}

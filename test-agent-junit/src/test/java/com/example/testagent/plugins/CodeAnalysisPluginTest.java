package com.example.testagent.plugins;

import com.example.testagent.core.ContextBuilder;
import com.example.testagent.model.AnalysisResult;
import com.example.testagent.plugins.CodeAnalysisPlugin.InvalidAnalysisException;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.contextvariables.ContextVariable;
import com.microsoft.semantickernel.orchestration.FunctionInvocation;
import com.microsoft.semantickernel.orchestration.FunctionResult;
import com.microsoft.semantickernel.semanticfunctions.KernelFunction;
import com.microsoft.semantickernel.semanticfunctions.KernelFunctionArguments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeAnalysisPluginTest {

    static final String VALID_JSON = """
            {
              "class_name": "UserService",
              "weak_points": [
                {"line": 15, "category": "Validação de entrada", "severity": "HIGH",
                 "description": "id não é validado", "suggestion": "Validar null"}
              ],
              "vulnerabilities": [
                {"line": 19, "type": "Null Dereference", "severity": "MEDIUM",
                 "description": "username pode ser nulo", "cwe_id": "CWE-476", "remediation": "Validar"}
              ],
              "test_scenarios": [
                {"method": "findById", "scenario": "id válido", "type": "HAPPY", "description": "retorna usuário"},
                {"method": "findById", "scenario": "id nulo", "type": "NULL", "description": "lança exceção"}
              ],
              "overall_score": 45,
              "campo_extra": "ignorado"
            }
            """;

    @Mock
    private Kernel kernel;

    @Mock
    private KernelFunction<String> analyzeFunction;

    @Mock
    private FunctionInvocation<String> invocation;

    private CodeAnalysisPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new CodeAnalysisPlugin(kernel, analyzeFunction, CodeAnalysisPlugin.defaultMapper());
    }

    @Nested
    @DisplayName("parseAnalysis")
    class ParseAnalysis {

        @Test
        @DisplayName("Converte JSON válido em AnalysisResult")
        void should_parseAnalysis_when_jsonIsValid() {
            AnalysisResult result = plugin.parseAnalysis(VALID_JSON);

            assertEquals("UserService", result.className());
            assertEquals(45, result.overallScore());
            assertEquals(1, result.weakPoints().size());
            assertEquals("HIGH", result.weakPoints().get(0).severity());
            assertEquals("CWE-476", result.vulnerabilities().get(0).cweId());
            assertEquals(2, result.testScenarios().size());
        }

        @Test
        @DisplayName("Aceita JSON dentro de markdown fences com texto ao redor")
        void should_parseAnalysis_when_jsonWrappedInMarkdown() {
            String wrapped = "Aqui está a análise:\n```json\n" + VALID_JSON + "\n```\nEspero ter ajudado!";

            AnalysisResult result = plugin.parseAnalysis(wrapped);

            assertEquals("UserService", result.className());
        }

        @Test
        @DisplayName("Usa listas vazias quando campos são omitidos")
        void should_useEmptyLists_when_fieldsMissing() {
            AnalysisResult result = plugin.parseAnalysis("{\"class_name\": \"Foo\", \"overall_score\": 150}");

            assertTrue(result.weakPoints().isEmpty());
            assertTrue(result.vulnerabilities().isEmpty());
            assertTrue(result.testScenarios().isEmpty());
            assertEquals(100, result.overallScore());
        }

        @Test
        @DisplayName("Lança InvalidAnalysisException para JSON malformado")
        void should_throw_when_jsonIsMalformed() {
            assertThrows(InvalidAnalysisException.class,
                    () -> plugin.parseAnalysis("{\"class_name\": \"Foo\", \"weak_points\": [ }"));
        }

        @Test
        @DisplayName("Lança InvalidAnalysisException quando não há JSON")
        void should_throw_when_noJsonPresent() {
            assertThrows(InvalidAnalysisException.class, () -> plugin.parseAnalysis("Não consegui analisar."));
            assertThrows(InvalidAnalysisException.class, () -> plugin.parseAnalysis(""));
            assertThrows(InvalidAnalysisException.class, () -> plugin.parseAnalysis(null));
        }

        @Test
        @DisplayName("Lança InvalidAnalysisException para tipo incompatível")
        void should_throw_when_typeMismatch() {
            assertThrows(InvalidAnalysisException.class,
                    () -> plugin.parseAnalysis("{\"overall_score\": \"alto\"}"));
        }
    }

    @Nested
    @DisplayName("analyzeCode")
    class AnalyzeCode {

        @Test
        @DisplayName("Invoca o Kernel com source_code e metadata e retorna o JSON extraído")
        void should_invokeKernelWithArguments_when_analyzing() {
            when(kernel.invokeAsync(analyzeFunction)).thenReturn(invocation);
            when(invocation.withArguments(any())).thenReturn(invocation);
            when(invocation.withResultType(String.class)).thenReturn(invocation);
            String response = "```json\n{\"a\":1}\n```";
            when(invocation.block()).thenReturn(new FunctionResult<>(ContextVariable.of(response), response));

            String json = plugin.analyzeCode("class A {}", "Classe: A");

            assertEquals("{\"a\":1}", json);
            ArgumentCaptor<KernelFunctionArguments> captor = ArgumentCaptor.forClass(KernelFunctionArguments.class);
            verify(invocation).withArguments(captor.capture());
            assertEquals("class A {}", captor.getValue().get(ContextBuilder.SOURCE_CODE).getValue());
            assertEquals("Classe: A", captor.getValue().get(ContextBuilder.METADATA).getValue());
        }

        @Test
        @DisplayName("Lança IllegalStateException quando a IA retorna vazio")
        void should_throw_when_responseIsEmpty() {
            when(kernel.invokeAsync(analyzeFunction)).thenReturn(invocation);
            when(invocation.withArguments(any())).thenReturn(invocation);
            when(invocation.withResultType(String.class)).thenReturn(invocation);
            when(invocation.block()).thenReturn(new FunctionResult<>(ContextVariable.of("  "), "  "));

            assertThrows(IllegalStateException.class, () -> plugin.analyzeCode("class A {}", "meta"));
        }
    }
}

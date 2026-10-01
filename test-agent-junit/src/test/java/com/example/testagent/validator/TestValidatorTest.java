package com.example.testagent.validator;

import com.example.testagent.plugins.TestGenerationPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestValidatorTest {

    private static final String VALID_TEST = """
            package com.example;

            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertEquals;

            class CalculatorTest {
                // comentário com { não balanceado
                @Test
                void should_sum_when_valid() {
                    String s = "texto com ) e }";
                    char c = '{';
                    assertEquals(2, 1 + 1);
                }
            }
            """;

    private final TestValidator validator = new TestValidator();

    @Test
    @DisplayName("Aceita código de teste válido, ignorando delimitadores em strings e comentários")
    void should_beValid_when_codeIsWellFormed() {
        TestValidator.ValidationResult result = validator.validate(VALID_TEST);

        assertTrue(result.valid(), () -> "erros: " + result.errors());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Detecta ausência de @Test, classe de teste e imports JUnit")
    void should_reportErrors_when_structureMissing() {
        TestValidator.ValidationResult result = validator.validate("class Foo { void a() {} }");

        assertFalse(result.valid());
        assertEquals(3, result.errors().size());
    }

    @Test
    @DisplayName("Detecta resíduos de markdown")
    void should_reportError_when_markdownFencesPresent() {
        TestValidator.ValidationResult result = validator.validate("```java\n" + VALID_TEST + "```");

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("markdown")));
    }

    @Test
    @DisplayName("Detecta chaves/parênteses desbalanceados")
    void should_reportError_when_unbalanced() {
        TestValidator.ValidationResult missingClose = validator.validate(VALID_TEST.replaceFirst("}\\s*$", ""));
        TestValidator.ValidationResult wrongOrder = validator.validate(VALID_TEST.replace("1 + 1)", "1 + 1]"));

        assertFalse(missingClose.valid());
        assertFalse(wrongOrder.valid());
    }

    @Test
    @DisplayName("Rejeita código vazio")
    void should_beInvalid_when_codeIsEmpty() {
        assertFalse(validator.validate("").valid());
        assertFalse(validator.validate(null).valid());
    }

    @Test
    @DisplayName("Código sanitizado pelo plugin passa na validação")
    void should_beValid_when_fencesStrippedByPlugin() {
        String raw = "Segue o teste:\n```java\n" + VALID_TEST + "```\nQualquer dúvida, avise.";

        TestValidator.ValidationResult result = validator.validate(TestGenerationPlugin.stripCodeFences(raw));

        assertTrue(result.valid(), () -> "erros: " + result.errors());
    }
}

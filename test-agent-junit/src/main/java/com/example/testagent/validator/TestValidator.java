package com.example.testagent.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validação estrutural básica do código de teste gerado pela IA.
 * Não compila o código: detecta apenas problemas comuns de saída de LLM.
 */
public class TestValidator {

    private static final Logger log = LoggerFactory.getLogger(TestValidator.class);

    private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b");
    private static final Pattern TEST_CLASS = Pattern.compile("\\bclass\\s+\\w*Test\\b");
    private static final Pattern JUNIT_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?org\\.junit\\.jupiter\\.");

    /**
     * Resultado da validação.
     *
     * @param valid  {@code true} quando nenhum erro foi encontrado
     * @param errors mensagens de erro (vazia quando válido)
     */
    public record ValidationResult(boolean valid, List<String> errors) {

        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    public ValidationResult validate(String testCode) {
        List<String> errors = new ArrayList<>();

        if (testCode == null || testCode.isBlank()) {
            errors.add("O código de teste está vazio");
            return new ValidationResult(false, errors);
        }
        if (!TEST_ANNOTATION.matcher(testCode).find()) {
            errors.add("Nenhum método anotado com @Test encontrado");
        }
        if (!TEST_CLASS.matcher(testCode).find()) {
            errors.add("Nenhuma declaração 'class ...Test' encontrada");
        }
        if (!JUNIT_IMPORT.matcher(testCode).find()) {
            errors.add("Imports do JUnit 5 (org.junit.jupiter) ausentes");
        }
        if (testCode.contains("```")) {
            errors.add("O código contém resíduos de markdown (```)");
        }
        String balanceError = checkBalance(testCode);
        if (balanceError != null) {
            errors.add(balanceError);
        }

        ValidationResult result = new ValidationResult(errors.isEmpty(), errors);
        if (result.valid()) {
            log.info("Código de teste validado com sucesso");
        } else {
            log.warn("Código de teste com {} problema(s): {}", errors.size(), errors);
        }
        return result;
    }

    /**
     * Verifica se parênteses, chaves e colchetes estão balanceados,
     * ignorando o conteúdo de strings, chars e comentários.
     *
     * @return mensagem de erro ou {@code null} se balanceado
     */
    static String checkBalance(String code) {
        Deque<Character> stack = new ArrayDeque<>();
        int n = code.length();
        int i = 0;
        while (i < n) {
            char c = code.charAt(i);
            char next = i + 1 < n ? code.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                i = skipUntil(code, i, "\n");
                continue;
            }
            if (c == '/' && next == '*') {
                i = skipUntil(code, i + 2, "*/");
                continue;
            }
            if (code.startsWith("\"\"\"", i)) {
                i = skipUntil(code, i + 3, "\"\"\"");
                continue;
            }
            if (c == '"' || c == '\'') {
                i = skipLiteral(code, i, c);
                continue;
            }
            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
            } else if (c == ')' || c == '}' || c == ']') {
                if (stack.isEmpty() || stack.pop() != opening(c)) {
                    return "Delimitador '" + c + "' sem correspondência (posição " + i + ")";
                }
            }
            i++;
        }
        return stack.isEmpty() ? null : "Delimitadores não fechados: " + stack.size();
    }

    private static int skipUntil(String code, int from, String terminator) {
        int idx = code.indexOf(terminator, from);
        return idx < 0 ? code.length() : idx + terminator.length();
    }

    private static int skipLiteral(String code, int start, char quote) {
        int i = start + 1;
        while (i < code.length() && code.charAt(i) != quote && code.charAt(i) != '\n') {
            if (code.charAt(i) == '\\') {
                i++;
            }
            i++;
        }
        return i + 1;
    }

    private static char opening(char closing) {
        return switch (closing) {
            case ')' -> '(';
            case '}' -> '{';
            default -> '[';
        };
    }
}

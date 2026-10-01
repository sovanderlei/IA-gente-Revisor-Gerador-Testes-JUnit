package com.example.testagent.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai metadados de código-fonte Java usando expressões regulares.
 * <p>
 * Não é um parser completo da linguagem: o objetivo é fornecer contexto
 * suficiente para os prompts (pacote, classe, métodos públicos, dependências).
 */
public class SourceParser {

    private static final Logger log = LoggerFactory.getLogger(SourceParser.class);

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);

    private static final Pattern TYPE_DECLARATION_PATTERN =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");

    private static final Pattern ANNOTATION_PATTERN =
            Pattern.compile("@([A-Za-z_$][\\w$.]*)(\\([^)]*\\))?");

    private static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
            "\\bpublic\\s+"
                    + "((?:(?:static|final|synchronized|abstract|default|native)\\s+)*)"
                    + "(?:<[^>]+>\\s+)?"
                    + "([\\w$.]+(?:\\s*<[^()]*?>)?(?:\\s*\\[\\s*])*)\\s+"
                    + "([a-zA-Z_$][\\w$]*)\\s*\\(([^)]*)\\)");

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*((?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*)"
                    + "((?:(?:private|protected|public|final|transient|volatile|static)\\s+)*)"
                    + "([A-Z][\\w$.]*(?:\\s*<[^;=()]*>)?)\\s+"
                    + "([a-zA-Z_$][\\w$]*)\\s*(?:=[^;]*)?;",
            Pattern.MULTILINE);

    /** Tipos de valor que não são considerados dependências injetáveis. */
    private static final Set<String> VALUE_TYPES = Set.of(
            "String", "Integer", "Long", "Double", "Float", "Short", "Byte", "Boolean",
            "Character", "BigDecimal", "BigInteger", "Object", "LocalDate", "LocalDateTime",
            "Instant", "UUID");

    private static final Set<String> NON_TYPE_KEYWORDS = Set.of(
            "class", "interface", "enum", "record", "new", "return", "throw", "else");

    /**
     * Extrai os metadados do código-fonte.
     *
     * @param sourceCode código Java
     * @return metadados extraídos
     */
    public SourceMetadata parse(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException("O código-fonte não pode ser vazio");
        }

        String code = stripCommentsAndStrings(sourceCode);

        String packageName = extractPackage(code);
        List<String> imports = extractImports(code);
        Matcher typeMatcher = TYPE_DECLARATION_PATTERN.matcher(code);
        if (!typeMatcher.find()) {
            throw new IllegalArgumentException("Nenhuma declaração de classe/interface/enum/record encontrada");
        }
        String className = typeMatcher.group(1);
        List<String> annotations = extractClassAnnotations(code, typeMatcher.start());
        String body = code.substring(typeMatcher.end());
        List<SourceMetadata.MethodInfo> methods = extractPublicMethods(body);
        List<String> dependencies = extractDependencies(body);

        SourceMetadata metadata = new SourceMetadata(
                packageName, className, imports, methods, dependencies, annotations);
        log.debug("Metadados extraídos: {}", metadata);
        log.info("Classe {} parseada: {} métodos públicos, {} dependências",
                className, methods.size(), dependencies.size());
        return metadata;
    }

    private static String extractPackage(String code) {
        Matcher matcher = PACKAGE_PATTERN.matcher(code);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<String> extractImports(String code) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(code);
        while (matcher.find()) {
            String prefix = matcher.group(1) != null ? "static " : "";
            imports.add(prefix + matcher.group(2));
        }
        return imports;
    }

    /**
     * Anotações da classe são as que aparecem entre o último import/package e a declaração do tipo.
     */
    private static List<String> extractClassAnnotations(String code, int typeDeclarationStart) {
        String header = code.substring(0, typeDeclarationStart);
        int lastStatementEnd = header.lastIndexOf(';');
        String region = lastStatementEnd >= 0 ? header.substring(lastStatementEnd + 1) : header;

        List<String> annotations = new ArrayList<>();
        Matcher matcher = ANNOTATION_PATTERN.matcher(region);
        while (matcher.find()) {
            annotations.add("@" + matcher.group(1) + (matcher.group(2) != null ? matcher.group(2) : ""));
        }
        return annotations;
    }

    private static List<SourceMetadata.MethodInfo> extractPublicMethods(String body) {
        List<SourceMetadata.MethodInfo> methods = new ArrayList<>();
        Matcher matcher = PUBLIC_METHOD_PATTERN.matcher(body);
        while (matcher.find()) {
            String returnType = matcher.group(2).replaceAll("\\s+", " ").trim();
            if (NON_TYPE_KEYWORDS.contains(returnType)) {
                continue;
            }
            methods.add(new SourceMetadata.MethodInfo(
                    matcher.group(3), returnType, splitParameters(matcher.group(4))));
        }
        return methods;
    }

    /** Divide parâmetros respeitando vírgulas dentro de generics (ex.: {@code Map<K, V> map}). */
    static List<String> splitParameters(String rawParameters) {
        if (rawParameters == null || rawParameters.isBlank()) {
            return List.of();
        }
        List<String> parameters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : rawParameters.toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parameters.add(normalizeWhitespace(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parameters.add(normalizeWhitespace(current.toString()));
        return parameters.stream().filter(p -> !p.isEmpty()).toList();
    }

    /**
     * Dependências são campos de instância (não estáticos) cujo tipo não é um tipo de valor simples.
     * Considera apenas o primeiro nível de chaves do corpo da classe.
     */
    private static List<String> extractDependencies(String body) {
        String topLevel = topLevelMembers(body);
        List<String> dependencies = new ArrayList<>();
        Matcher matcher = FIELD_PATTERN.matcher(topLevel);
        while (matcher.find()) {
            List<String> modifiers = Arrays.asList(matcher.group(2).trim().split("\\s+"));
            if (modifiers.contains("static")) {
                continue;
            }
            String type = normalizeWhitespace(matcher.group(3));
            String rawType = type.contains("<") ? type.substring(0, type.indexOf('<')).trim() : type;
            if (VALUE_TYPES.contains(rawType) || NON_TYPE_KEYWORDS.contains(rawType)) {
                continue;
            }
            dependencies.add(type + " " + matcher.group(4));
        }
        return dependencies;
    }

    /** Retorna apenas o texto em profundidade 1 de chaves (membros diretos da classe). */
    private static String topLevelMembers(String body) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (char c : body.toCharArray()) {
            if (c == '{') {
                depth++;
                if (depth == 2) {
                    sb.append(";\n");
                }
                continue;
            }
            if (c == '}') {
                depth--;
                continue;
            }
            if (depth == 1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Remove comentários e conteúdo de literais (strings/chars) preservando quebras de linha,
     * para que regex não capturem trechos de texto como se fossem código.
     */
    static String stripCommentsAndStrings(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            char next = i + 1 < n ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (c == '"' && source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? n : end + 3;
                out.append("\"\"");
                for (int k = i; k < stop; k++) {
                    if (source.charAt(k) == '\n') {
                        out.append('\n');
                    }
                }
                i = stop;
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < n && source.charAt(i) != c && source.charAt(i) != '\n') {
                    if (source.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
                if (i < n) {
                    out.append(c);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}

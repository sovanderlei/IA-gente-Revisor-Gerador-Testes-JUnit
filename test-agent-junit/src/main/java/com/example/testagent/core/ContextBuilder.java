package com.example.testagent.core;

import com.microsoft.semantickernel.semanticfunctions.KernelFunctionArguments;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder das variáveis de contexto passadas às funções semânticas.
 * Os nomes das variáveis coincidem com os placeholders {@code {{$...}}} dos prompts.
 */
public class ContextBuilder {

    public static final String SOURCE_CODE = "source_code";
    public static final String METADATA = "metadata";
    public static final String ANALYSIS_JSON = "analysis_json";
    public static final String PACKAGE_NAME = "package_name";

    private final Map<String, String> variables = new LinkedHashMap<>();

    public static ContextBuilder create() {
        return new ContextBuilder();
    }

    public ContextBuilder withSourceCode(String sourceCode) {
        return put(SOURCE_CODE, sourceCode);
    }

    public ContextBuilder withMetadata(String metadata) {
        return put(METADATA, metadata);
    }

    public ContextBuilder withAnalysisJson(String analysisJson) {
        return put(ANALYSIS_JSON, analysisJson);
    }

    public ContextBuilder withPackageName(String packageName) {
        return put(PACKAGE_NAME, packageName);
    }

    /** Variáveis como mapa imutável (útil para logs e testes). */
    public Map<String, String> buildMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    /** Variáveis no formato esperado pelo Semantic Kernel. */
    public KernelFunctionArguments build() {
        KernelFunctionArguments.Builder builder = KernelFunctionArguments.builder();
        variables.forEach(builder::withVariable);
        return builder.build();
    }

    private ContextBuilder put(String name, String value) {
        variables.put(name, Objects.requireNonNull(value, name + " não pode ser nulo"));
        return this;
    }
}

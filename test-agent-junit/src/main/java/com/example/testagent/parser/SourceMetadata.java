package com.example.testagent.parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Metadados extraídos de um arquivo-fonte Java.
 *
 * @param packageName  pacote declarado (vazio quando é o pacote padrão)
 * @param className    nome da classe/interface/record principal
 * @param imports      imports declarados
 * @param methods      métodos públicos
 * @param dependencies campos de instância que representam dependências (ex.: "UserRepository repository")
 * @param annotations  anotações aplicadas à classe
 */
public record SourceMetadata(
        @JsonProperty("package_name") String packageName,
        @JsonProperty("class_name") String className,
        @JsonProperty("imports") List<String> imports,
        @JsonProperty("methods") List<MethodInfo> methods,
        @JsonProperty("dependencies") List<String> dependencies,
        @JsonProperty("annotations") List<String> annotations) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Informações de um método público.
     *
     * @param name       nome do método
     * @param returnType tipo de retorno
     * @param parameters lista de parâmetros como declarados (ex.: "Long id")
     */
    public record MethodInfo(
            @JsonProperty("name") String name,
            @JsonProperty("return_type") String returnType,
            @JsonProperty("parameters") List<String> parameters) {

        public MethodInfo {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }

        /** Assinatura legível, ex.: {@code User findById(Long id)}. */
        public String signature() {
            return returnType + " " + name + "(" + String.join(", ", parameters) + ")";
        }
    }

    public SourceMetadata {
        packageName = packageName == null ? "" : packageName;
        className = className == null ? "" : className;
        imports = imports == null ? List.of() : List.copyOf(imports);
        methods = methods == null ? List.of() : List.copyOf(methods);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
    }

    /** Serializa os metadados para JSON. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Falha ao serializar SourceMetadata para JSON", e);
        }
    }

    /** Texto legível usado como variável {@code metadata} do prompt de análise. */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pacote: ").append(packageName.isEmpty() ? "(padrão)" : packageName).append('\n');
        sb.append("Classe: ").append(className).append('\n');
        sb.append("Anotações da classe: ").append(joinOrNone(annotations)).append('\n');
        sb.append("Dependências: ").append(joinOrNone(dependencies)).append('\n');
        sb.append("Imports: ").append(joinOrNone(imports)).append('\n');
        sb.append("Métodos públicos:");
        if (methods.isEmpty()) {
            sb.append(" nenhum");
        } else {
            sb.append('\n').append(methods.stream()
                    .map(m -> "  - " + m.signature())
                    .collect(Collectors.joining("\n")));
        }
        return sb.toString();
    }

    private static String joinOrNone(List<String> values) {
        return values.isEmpty() ? "nenhum" : String.join(", ", values);
    }
}

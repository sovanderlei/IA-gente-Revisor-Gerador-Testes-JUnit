package com.example.testagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ponto fraco de qualidade/design identificado pela análise da IA.
 *
 * @param line        linha aproximada no código-fonte (0 quando desconhecida)
 * @param category    categoria (ex.: "Validação de entrada", "Tratamento de exceções")
 * @param severity    severidade: LOW, MEDIUM, HIGH ou CRITICAL
 * @param description descrição do problema
 * @param suggestion  sugestão de melhoria
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WeakPoint(
        @JsonProperty("line") int line,
        @JsonProperty("category") String category,
        @JsonProperty("severity") String severity,
        @JsonProperty("description") String description,
        @JsonProperty("suggestion") String suggestion) {
}

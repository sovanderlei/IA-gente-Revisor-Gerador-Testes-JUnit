package com.example.testagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cenário de teste recomendado pela análise da IA.
 *
 * @param method      método público alvo do cenário
 * @param scenario    nome curto do cenário
 * @param type        tipo: HAPPY, ERROR, BOUNDARY, NULL ou CONCURRENCY
 * @param description descrição do comportamento esperado
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TestScenario(
        @JsonProperty("method") String method,
        @JsonProperty("scenario") String scenario,
        @JsonProperty("type") String type,
        @JsonProperty("description") String description) {
}

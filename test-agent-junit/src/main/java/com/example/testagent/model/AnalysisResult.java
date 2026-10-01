package com.example.testagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resultado estruturado do Prompt 1 (análise de código).
 * Serve de entrada para o Prompt 2 (geração de testes) no prompt chaining.
 *
 * @param className       nome da classe analisada
 * @param weakPoints      pontos fracos encontrados
 * @param vulnerabilities vulnerabilidades encontradas
 * @param testScenarios   cenários de teste recomendados
 * @param overallScore    nota geral de qualidade (0-100)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResult(
        @JsonProperty("class_name") String className,
        @JsonProperty("weak_points") List<WeakPoint> weakPoints,
        @JsonProperty("vulnerabilities") List<Vulnerability> vulnerabilities,
        @JsonProperty("test_scenarios") List<TestScenario> testScenarios,
        @JsonProperty("overall_score") int overallScore) {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    /**
     * Normaliza listas nulas (a IA pode omitir campos) e limita o score ao intervalo 0-100.
     */
    public AnalysisResult {
        weakPoints = weakPoints == null ? List.of() : List.copyOf(weakPoints);
        vulnerabilities = vulnerabilities == null ? List.of() : List.copyOf(vulnerabilities);
        testScenarios = testScenarios == null ? List.of() : List.copyOf(testScenarios);
        overallScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, overallScore));
    }
}

package com.example.testagent.model;

import java.util.Objects;

/**
 * Resultado final do prompt chain: análise + código de teste gerado.
 *
 * @param analysis     análise estruturada (saída do Prompt 1)
 * @param testCode     código Java da classe de teste (saída do Prompt 2, já sanitizada)
 * @param testFileName nome do arquivo de teste (ex.: "UserServiceTest.java")
 */
public record TestSuite(AnalysisResult analysis, String testCode, String testFileName) {

    public TestSuite {
        Objects.requireNonNull(analysis, "analysis não pode ser nulo");
        Objects.requireNonNull(testCode, "testCode não pode ser nulo");
        Objects.requireNonNull(testFileName, "testFileName não pode ser nulo");
    }
}

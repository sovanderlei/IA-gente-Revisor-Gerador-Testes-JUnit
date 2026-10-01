package com.example.testagent.output;

import com.example.testagent.model.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Grava o arquivo {@code *Test.java} gerado no diretório de saída.
 */
public class TestFileWriter {

    private static final Logger log = LoggerFactory.getLogger(TestFileWriter.class);

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z_$][\\w$]*\\.java");

    /**
     * Escreve o código de teste em {@code outputDir/testFileName}, criando diretórios se necessário.
     *
     * @return caminho do arquivo gravado
     */
    public Path write(TestSuite suite, Path outputDir) {
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(outputDir, "outputDir");

        String fileName = suite.testFileName();
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Nome de arquivo de teste inválido: " + fileName);
        }

        try {
            Files.createDirectories(outputDir);
            Path target = outputDir.resolve(fileName);
            Files.writeString(target, suite.testCode(), StandardCharsets.UTF_8);
            log.info("Arquivo de teste gravado em {}", target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            log.error("Erro ao gravar o arquivo de teste {} em {}", fileName, outputDir, e);
            throw new UncheckedIOException("Erro ao gravar o arquivo de teste: " + fileName, e);
        }
    }
}

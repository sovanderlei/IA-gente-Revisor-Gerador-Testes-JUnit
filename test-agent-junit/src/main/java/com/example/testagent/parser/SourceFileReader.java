package com.example.testagent.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Lê arquivos {@code .java} do disco aplicando validações de segurança e tamanho.
 */
public class SourceFileReader {

    private static final Logger log = LoggerFactory.getLogger(SourceFileReader.class);

    public static final long DEFAULT_MAX_SIZE_BYTES = 50L * 1024L;
    private static final String JAVA_EXTENSION = ".java";

    private final long maxSizeBytes;

    public SourceFileReader() {
        this(DEFAULT_MAX_SIZE_BYTES);
    }

    public SourceFileReader(long maxSizeBytes) {
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes deve ser positivo");
        }
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * Lê o conteúdo do arquivo informado.
     *
     * @param rawPath caminho do arquivo .java
     * @return conteúdo do arquivo em UTF-8
     * @throws IllegalArgumentException se o caminho for inválido ou o arquivo não passar nas validações
     * @throws UncheckedIOException     se ocorrer erro de I/O na leitura
     */
    public String read(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("O caminho do arquivo não pode ser vazio");
        }

        Path path = toPath(rawPath);
        rejectPathTraversal(path, rawPath);

        if (!path.getFileName().toString().endsWith(JAVA_EXTENSION)) {
            throw new IllegalArgumentException("O arquivo deve ter extensão .java: " + rawPath);
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + rawPath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("O caminho não é um arquivo regular: " + rawPath);
        }

        try {
            long size = Files.size(path);
            if (size == 0) {
                throw new IllegalArgumentException("O arquivo está vazio: " + rawPath);
            }
            if (size > maxSizeBytes) {
                throw new IllegalArgumentException(String.format(
                        "O arquivo excede o tamanho máximo de %d bytes (%d bytes): %s",
                        maxSizeBytes, size, rawPath));
            }

            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                throw new IllegalArgumentException("O arquivo contém apenas espaços em branco: " + rawPath);
            }
            log.info("Arquivo lido com sucesso: {} ({} bytes)", path.toAbsolutePath(), size);
            return content;
        } catch (IOException e) {
            log.error("Erro ao ler o arquivo {}", rawPath, e);
            throw new UncheckedIOException("Erro ao ler o arquivo: " + rawPath, e);
        }
    }

    private static Path toPath(String rawPath) {
        try {
            return Path.of(rawPath);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Caminho inválido: " + rawPath, e);
        }
    }

    /** Bloqueia qualquer segmento ".." no caminho para evitar path traversal. */
    private static void rejectPathTraversal(Path path, String rawPath) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                log.warn("Tentativa de path traversal bloqueada: {}", rawPath);
                throw new IllegalArgumentException("Path traversal não permitido: " + rawPath);
            }
        }
    }
}

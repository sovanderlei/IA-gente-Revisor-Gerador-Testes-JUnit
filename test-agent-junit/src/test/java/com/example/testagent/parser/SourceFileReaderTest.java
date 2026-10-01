package com.example.testagent.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceFileReaderTest {

    private static final String VALID_SOURCE = "package a;\npublic class Foo {}\n";

    @TempDir
    Path tempDir;

    private SourceFileReader reader;

    @BeforeEach
    void setUp() {
        reader = new SourceFileReader();
    }

    @Test
    @DisplayName("Lê o conteúdo de um arquivo .java válido")
    void should_returnContent_when_fileIsValid() throws IOException {
        Path file = Files.writeString(tempDir.resolve("Foo.java"), VALID_SOURCE, StandardCharsets.UTF_8);

        String content = reader.read(file.toString());

        assertEquals(VALID_SOURCE, content);
    }

    @Test
    @DisplayName("Falha quando o arquivo não existe")
    void should_throw_when_fileDoesNotExist() {
        Path missing = tempDir.resolve("Missing.java");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(missing.toString()));

        assertTrue(ex.getMessage().contains("não encontrado"));
    }

    @Test
    @DisplayName("Falha quando a extensão não é .java")
    void should_throw_when_extensionIsNotJava() throws IOException {
        Path file = Files.writeString(tempDir.resolve("Foo.txt"), VALID_SOURCE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(file.toString()));

        assertTrue(ex.getMessage().contains(".java"));
    }

    @Test
    @DisplayName("Falha quando o arquivo está vazio")
    void should_throw_when_fileIsEmpty() throws IOException {
        Path file = Files.createFile(tempDir.resolve("Empty.java"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(file.toString()));

        assertTrue(ex.getMessage().contains("vazio"));
    }

    @Test
    @DisplayName("Falha quando o arquivo contém apenas espaços")
    void should_throw_when_fileIsBlank() throws IOException {
        Path file = Files.writeString(tempDir.resolve("Blank.java"), "   \n\t\n");

        assertThrows(IllegalArgumentException.class, () -> reader.read(file.toString()));
    }

    @Test
    @DisplayName("Bloqueia path traversal com '..'")
    void should_throw_when_pathContainsTraversal() {
        String path = tempDir.resolve("sub").toString() + "/../Foo.java";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reader.read(path));

        assertTrue(ex.getMessage().contains("Path traversal"));
    }

    @Test
    @DisplayName("Falha quando o arquivo excede o tamanho máximo")
    void should_throw_when_fileExceedsMaxSize() throws IOException {
        SourceFileReader smallReader = new SourceFileReader(10);
        Path file = Files.writeString(tempDir.resolve("Big.java"), VALID_SOURCE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> smallReader.read(file.toString()));

        assertTrue(ex.getMessage().contains("tamanho máximo"));
    }

    @Test
    @DisplayName("Falha quando o caminho é nulo ou vazio")
    void should_throw_when_pathIsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(null));
        assertThrows(IllegalArgumentException.class, () -> reader.read("  "));
    }
}

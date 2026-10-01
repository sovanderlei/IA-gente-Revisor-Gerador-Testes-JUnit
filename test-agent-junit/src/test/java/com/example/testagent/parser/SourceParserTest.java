package com.example.testagent.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceParserTest {

    private static final String USER_SERVICE = """
            package com.example.service;

            import com.example.model.User;
            import com.example.repository.UserRepository;
            import static java.util.Objects.requireNonNull;

            /**
             * Serviço de usuários. public void fakeMethod() em comentário não deve contar.
             */
            @Service
            @Transactional(readOnly = true)
            public class UserService {
                private static final String DEFAULT_ROLE = "public String notAMethod()";
                private final UserRepository repository;
                private final AuditClient auditClient;
                private int counter;

                public UserService(UserRepository repository, AuditClient auditClient) {
                    this.repository = repository;
                    this.auditClient = auditClient;
                }

                public User findById(Long id) {
                    return repository.findById(id);
                }

                public void deleteUser(String username) {
                    repository.deleteByUsername(username);
                }

                public Map<String, List<User>> groupBy(Map<String, Integer> filters, int limit) {
                    return Map.of();
                }

                private void helper() {
                }
            }
            """;

    private SourceParser parser;

    @BeforeEach
    void setUp() {
        parser = new SourceParser();
    }

    @Test
    @DisplayName("Extrai o package")
    void should_extractPackage_when_declared() {
        assertEquals("com.example.service", parser.parse(USER_SERVICE).packageName());
    }

    @Test
    @DisplayName("Extrai o nome da classe")
    void should_extractClassName_when_classDeclared() {
        assertEquals("UserService", parser.parse(USER_SERVICE).className());
    }

    @Test
    @DisplayName("Extrai imports, incluindo static")
    void should_extractImports_when_present() {
        List<String> imports = parser.parse(USER_SERVICE).imports();

        assertEquals(List.of(
                "com.example.model.User",
                "com.example.repository.UserRepository",
                "static java.util.Objects.requireNonNull"), imports);
    }

    @Test
    @DisplayName("Extrai apenas métodos públicos, ignorando construtor, comentários e strings")
    void should_extractPublicMethods_when_present() {
        List<SourceMetadata.MethodInfo> methods = parser.parse(USER_SERVICE).methods();

        assertEquals(List.of("findById", "deleteUser", "groupBy"),
                methods.stream().map(SourceMetadata.MethodInfo::name).toList());

        SourceMetadata.MethodInfo findById = methods.get(0);
        assertEquals("User", findById.returnType());
        assertEquals(List.of("Long id"), findById.parameters());

        SourceMetadata.MethodInfo deleteUser = methods.get(1);
        assertEquals("void", deleteUser.returnType());
        assertEquals(List.of("String username"), deleteUser.parameters());

        SourceMetadata.MethodInfo groupBy = methods.get(2);
        assertEquals("Map<String, List<User>>", groupBy.returnType());
        assertEquals(List.of("Map<String, Integer> filters", "int limit"), groupBy.parameters());
    }

    @Test
    @DisplayName("Extrai dependências (campos de instância não primitivos)")
    void should_extractDependencies_when_fieldsInjected() {
        List<String> dependencies = parser.parse(USER_SERVICE).dependencies();

        assertEquals(List.of("UserRepository repository", "AuditClient auditClient"), dependencies);
    }

    @Test
    @DisplayName("Extrai anotações da classe")
    void should_extractClassAnnotations_when_present() {
        List<String> annotations = parser.parse(USER_SERVICE).annotations();

        assertEquals(List.of("@Service", "@Transactional(readOnly = true)"), annotations);
    }

    @Test
    @DisplayName("Retorna package vazio quando não declarado")
    void should_returnEmptyPackage_when_defaultPackage() {
        SourceMetadata metadata = parser.parse("public class Foo { public int sum(int a, int b) { return a + b; } }");

        assertEquals("", metadata.packageName());
        assertEquals("Foo", metadata.className());
        assertEquals(1, metadata.methods().size());
        assertTrue(metadata.imports().isEmpty());
    }

    @Test
    @DisplayName("Falha quando não há declaração de tipo")
    void should_throw_when_noTypeDeclaration() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("package a; // nada aqui"));
    }

    @Test
    @DisplayName("Falha quando o código é nulo ou vazio")
    void should_throw_when_sourceIsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(" "));
    }

    @Test
    @DisplayName("Resumo e JSON contêm os metadados")
    void should_produceSummaryAndJson_when_parsed() {
        SourceMetadata metadata = parser.parse(USER_SERVICE);

        String summary = metadata.toSummary();
        assertTrue(summary.contains("Classe: UserService"));
        assertTrue(summary.contains("User findById(Long id)"));

        String json = metadata.toJson();
        assertTrue(json.contains("\"class_name\":\"UserService\""));
        assertFalse(json.isBlank());
    }
}

# PROMPT: Criar Projeto Completo — Agente Revisor e Gerador de Testes JUnit

---

## PAPEL

Você é um engenheiro de software sênior especializado em Java 17+, Semantic Kernel, JUnit 5 e Mockito. Você escreve código limpo, bem documentado, testável e seguindo SOLID. Você vai criar um projeto Java completo do zero.

---

## OBJETIVO

Criar uma ferramenta CLI em Java que:

1. Lê um arquivo `.java` de entrada
2. Analisa o código-fonte identificando pontos fracos e vulnerabilidades (via IA)
3. Gera automaticamente um arquivo `Test.java` completo com JUnit 5 + Mockito (via IA)
4. Produz um relatório em Markdown com a análise

O projeto usa **Semantic Kernel Java SDK** para orquestrar chamadas a LLM (OpenAI) com a técnica de **Prompt Chaining**: a saída estruturada (JSON) do Prompt 1 (análise) alimenta o Prompt 2 (geração de testes).

---

## STACK OBRIGATÓRIA

| Tecnologia               | Versão         | Uso                |
| ------------------------ | -------------- | ------------------ |
| Java                     | 17+            | Linguagem base     |
| Maven                    | 3.8+           | Build              |
| Semantic Kernel Java SDK | 1.2.0          | Orquestração de IA |
| Picocli                  | 4.7.5          | CLI framework      |
| Jackson                  | 2.17.0         | JSON parsing       |
| SLF4J + Logback          | 2.0.12 / 1.5.3 | Logging            |
| JUnit 5                  | 5.10.2         | Testes do projeto  |
| Mockito                  | 5.11.0         | Mocks nos testes   |

---

## ESTRUTURA DO PROJETO

Crie exatamente esta estrutura de arquivos:

```
test-agent-junit/
├── pom.xml
├── README.md
│
├── src/main/java/com/example/testagent/
│   ├── TestAgentApp.java
│   │
│   ├── cli/
│   │   ├── GenerateCommand.java
│   │   ├── AnalyzeCommand.java
│   │   └── FullCommand.java
│   │
│   ├── core/
│   │   ├── KernelFactory.java
│   │   ├── PromptChainExecutor.java
│   │   └── ContextBuilder.java
│   │
│   ├── parser/
│   │   ├── SourceFileReader.java
│   │   ├── SourceParser.java
│   │   └── SourceMetadata.java
│   │
│   ├── plugins/
│   │   ├── CodeAnalysisPlugin.java
│   │   └── TestGenerationPlugin.java
│   │
│   ├── model/
│   │   ├── AnalysisResult.java
│   │   ├── Vulnerability.java
│   │   ├── WeakPoint.java
│   │   ├── TestScenario.java
│   │   └── TestSuite.java
│   │
│   ├── output/
│   │   ├── TestFileWriter.java
│   │   └── ReportGenerator.java
│   │
│   └── validator/
│       └── TestValidator.java
│
├── src/main/resources/
│   ├── prompts/
│   │   ├── analyze_code.skprompt.txt
│   │   ├── analyze_code.config.json
│   │   ├── generate_tests.skprompt.txt
│   │   └── generate_tests.config.json
│   ├── templates/
│   │   └── report_template.md
│   └── logback.xml
│
├── src/test/java/com/example/testagent/
│   ├── parser/
│   │   ├── SourceFileReaderTest.java
│   │   └── SourceParserTest.java
│   ├── core/
│   │   └── PromptChainExecutorTest.java
│   ├── plugins/
│   │   └── CodeAnalysisPluginTest.java
│   └── output/
│       └── ReportGeneratorTest.java
│
└── samples/
    ├── input/
    │   └── UserService.java
    └── output/
        ├── UserServiceTest.java
        └── analysis_report.md
```

---

## ESPECIFICAÇÃO DE CADA ARQUIVO

### 1. `pom.xml`

- groupId: `com.example`
- artifactId: `test-agent-junit`
- version: `1.0.0`
- packaging: `jar`
- Java 17 source/target
- Todas as dependências da tabela de stack
- Plugins: maven-compiler-plugin, maven-jar-plugin (mainClass = `com.example.testagent.TestAgentApp`), maven-shade-plugin (fat jar), maven-surefire-plugin

### 2. `TestAgentApp.java`

- Entry point com Picocli `@Command`
- Subcomandos: `generate`, `analyze`, `full`
- Valida que `OPENAI_API_KEY` existe como variável de ambiente
- Configura logging

### 3. `cli/GenerateCommand.java`

- `@Command(name = "generate")`
- Opção `--source` (obrigatória): caminho do arquivo .java
- Opção `--output` (opcional): diretório de saída (default: `./output`)
- Fluxo: ler arquivo → parsear → executar prompt chain → escrever Test.java

### 4. `cli/AnalyzeCommand.java`

- `@Command(name = "analyze")`
- Mesmas opções do GenerateCommand
- Fluxo: ler arquivo → parsear → executar prompt 1 → gerar relatório.md

### 5. `cli/FullCommand.java`

- `@Command(name = "full")`
- Executa análise + geração de testes
- Gera ambos os outputs

### 6. `core/KernelFactory.java`

- Método estático `createKernel()` que:
  - Lê `OPENAI_API_KEY` do ambiente
  - Cria `Kernel` com `OpenAIChatCompletion` (modelo `gpt-4o`)
  - Retorna o Kernel configurado
- Método `createKernel(String apiKey, String modelId)` para testes

### 7. `core/PromptChainExecutor.java`

- Recebe `Kernel`, `sourceCode`, `SourceMetadata`
- **Passo 1:** Monta contexto com `source_code` e `metadata`, invoca a função semântica de análise
- **Passo 2:** Valida o JSON retornado (parse com Jackson para `AnalysisResult`)
- **Passo 3:** Monta novo contexto com `source_code`, `analysis_json` e `package_name`, invoca a função de geração
- **Passo 4:** Limpa o output (remove markdown fences se presentes)
- Implementa retry (3 tentativas) com backoff exponencial para falhas de API
- Retorna `TestSuite` contendo `AnalysisResult` + código do teste gerado

### 8. `core/ContextBuilder.java`

- Builder pattern para montar variáveis do Semantic Kernel
- Métodos: `withSourceCode(String)`, `withMetadata(String)`, `withAnalysisJson(String)`, `withPackageName(String)`
- Método `build()` retorna `Map<String, String>` ou `KernelArguments`

### 9. `parser/SourceFileReader.java`

- Lê arquivo `.java` do disco
- Validações:
  - Arquivo existe
  - Extensão é `.java`
  - Tamanho ≤ 50KB
  - Não está vazio
  - Previne path traversal (verifica `..` no caminho)
- Retorna o conteúdo como `String`

### 10. `parser/SourceParser.java`

- Extrai metadados do código-fonte Java usando regex
- Extrai:
  - `package` declaration
  - Nome da classe
  - Lista de imports
  - Métodos públicos (nome, parâmetros, tipo de retorno)
  - Dependências (campos injetados)
  - Anotações da classe
- Retorna `SourceMetadata`

### 11. `parser/SourceMetadata.java`

- `record` Java com campos:
  - `String packageName`
  - `String className`
  - `List<String> imports`
  - `List<MethodInfo> methods` (record interno com name, returnType, parameters)
  - `List<String> dependencies`
  - `List<String> annotations`
- Método `toJson()` que serializa para JSON via Jackson
- Método `toSummary()` que retorna texto legível para o prompt

### 12. `plugins/CodeAnalysisPlugin.java`

- Classe com método anotado com `@DefineKernelFunction`
- Recebe o código-fonte e metadados
- Carrega o prompt de `analyze_code.skprompt.txt`
- Invoca via Semantic Kernel
- Faz parse da resposta JSON para `AnalysisResult`
- Trata JSON malformado (extrai JSON de dentro de markdown fences se necessário)

### 13. `plugins/TestGenerationPlugin.java`

- Similar ao CodeAnalysisPlugin
- Recebe código-fonte + JSON da análise
- Carrega prompt de `generate_tests.skprompt.txt`
- Retorna o código Java do teste como String

### 14. `model/AnalysisResult.java`

- `record` com campos:
  - `String className`
  - `List<WeakPoint> weakPoints`
  - `List<Vulnerability> vulnerabilities`
  - `List<TestScenario> testScenarios`
  - `int overallScore`
- Anotações Jackson (`@JsonProperty` com snake_case)

### 15. `model/Vulnerability.java`

- `record`: `int line`, `String type`, `String severity`, `String description`, `String cweId`, `String remediation`

### 16. `model/WeakPoint.java`

- `record`: `int line`, `String category`, `String severity`, `String description`, `String suggestion`

### 17. `model/TestScenario.java`

- `record`: `String method`, `String scenario`, `String type`, `String description`

### 18. `model/TestSuite.java`

- `record`: `AnalysisResult analysis`, `String testCode`, `String testFileName`

### 19. `output/TestFileWriter.java`

- Recebe `TestSuite` e diretório de saída
- Escreve o arquivo `Test.java` no disco
- Cria diretórios se não existirem
- Loga o caminho do arquivo criado

### 20. `output/ReportGenerator.java`

- Recebe `AnalysisResult`
- Gera Markdown formatado com:
  - Header com nome da classe e score
  - Tabela de pontos fracos
  - Tabela de vulnerabilidades
  - Checklist de cenários de teste
- Usa template de `report_template.md` ou gera inline
- Método `generate(AnalysisResult): String` e `writeToFile(String path, AnalysisResult)`

### 21. `validator/TestValidator.java`

- Validação básica do código gerado:
  - Contém `@Test`
  - Contém `class ... Test`
  - Contém imports do JUnit
  - Não contém blocos ```java (markdown residue)
  - Tem parênteses/chaves balanceados
- Retorna `ValidationResult` (record com `boolean valid`, `List<String> errors`)

### 22. Arquivos de Prompt

**`analyze_code.skprompt.txt`:**

```
Você é um arquiteto de software sênior e especialista em segurança de código Java.

Analise o seguinte código-fonte Java e identifique:

1. Pontos Fracos (weak_points): código duplicado, métodos longos (>20 linhas), baixa coesão, ausência de validação de entrada, tratamento inadequado de exceções, falta de logs.

2. Vulnerabilidades (vulnerabilities): injeção SQL/comandos, exposição de dados sensíveis, falta de validação de null, condições de corrida, uso inseguro de APIs.

3. Cenários de Teste Recomendados (test_scenarios): happy path, erros/exceções, casos limite (boundary), null/empty, concorrência.

CÓDIGO-FONTE:
{{$source_code}}

METADADOS:
{{$metadata}}

RESPONDA EXCLUSIVAMENTE em JSON válido seguindo este schema (sem markdown, sem texto extra):
{
  "class_name": "string",
  "weak_points": [{"line": 0, "category": "string", "severity": "LOW|MEDIUM|HIGH|CRITICAL", "description": "string", "suggestion": "string"}],
  "vulnerabilities": [{"line": 0, "type": "string", "severity": "LOW|MEDIUM|HIGH|CRITICAL", "description": "string", "cwe_id": "string", "remediation": "string"}],
  "test_scenarios": [{"method": "string", "scenario": "string", "type": "HAPPY|ERROR|BOUNDARY|NULL|CONCURRENCY", "description": "string"}],
  "overall_score": 0
}
```

**`analyze_code.config.json`:**

```json
{
  "schema": 1,
  "description": "Analisa código Java para pontos fracos e vulnerabilidades",
  "execution_settings": {
    "default": {
      "max_tokens": 4000,
      "temperature": 0.3,
      "top_p": 0.9
    }
  },
  "input_variables": [
    {
      "name": "source_code",
      "description": "Código-fonte Java",
      "required": true
    },
    {
      "name": "metadata",
      "description": "Metadados extraídos",
      "required": true
    }
  ]
}
```

**`generate_tests.skprompt.txt`:**

```
Você é um engenheiro de QA sênior especialista em Java, JUnit 5 e Mockito.

Gere uma suíte completa de testes unitários baseada na análise e no código-fonte.

CÓDIGO-FONTE:
{{$source_code}}

ANÁLISE (JSON):
{{$analysis_json}}

REGRAS OBRIGATÓRIAS:
1. Use JUnit 5 (@Test, @BeforeEach, @DisplayName, @Nested)
2. Use Mockito (@Mock, @InjectMocks, @ExtendWith(MockitoExtension.class))
3. Siga padrão Arrange-Act-Assert (AAA)
4. Nomeie métodos: should_[expected]_when_[condition]
5. Cubra TODOS os métodos públicos
6. Cubra TODOS os test_scenarios da análise
7. Inclua testes de null/empty para parâmetros
8. Use assertions específicas (assertThrows, assertEquals, assertNotNull)
9. Use verify() para interações com mocks
10. APENAS código Java compilável, SEM markdown, SEM explicações
11. Inclua TODOS os imports necessários
12. Package: {{$package_name}}
```

**`generate_tests.config.json`:**

```json
{
  "schema": 1,
  "description": "Gera testes JUnit 5 + Mockito",
  "execution_settings": {
    "default": {
      "max_tokens": 6000,
      "temperature": 0.2,
      "top_p": 0.95
    }
  },
  "input_variables": [
    {
      "name": "source_code",
      "description": "Código-fonte Java original",
      "required": true
    },
    {
      "name": "analysis_json",
      "description": "JSON da análise",
      "required": true
    },
    {
      "name": "package_name",
      "description": "Package do teste",
      "required": true
    }
  ]
}
```

### 23. `samples/input/UserService.java`

```java
package com.example.service;

import com.example.model.User;
import com.example.repository.UserRepository;

public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User findById(Long id) {
        return repository.findById(id);
    }

    public void deleteUser(String username) {
        repository.deleteByUsername(username);
    }
}
```

### 24. Testes Unitários do Projeto

Crie testes para:

- `SourceFileReaderTest`: arquivo válido, arquivo inexistente, extensão errada, arquivo vazio, path traversal
- `SourceParserTest`: extrai package, classe, métodos, imports corretamente
- `PromptChainExecutorTest`: usa mocks do Kernel, testa retry, testa JSON malformado
- `CodeAnalysisPluginTest`: parse de JSON válido, tratamento de JSON inválido
- `ReportGeneratorTest`: gera markdown com tabelas corretas

Use Mockito para mockar o Kernel e as chamadas à IA.

### 25. `README.md`

- Descrição do projeto
- Pré-requisitos
- Como compilar (`mvn clean package`)
- Como executar (exemplos dos 3 comandos)
- Variáveis de ambiente necessárias
- Estrutura do projeto
- Exemplo de uso com o sample

---

## REGRAS DE QUALIDADE OBRIGATÓRIAS

1. **Todo código deve compilar** sem erros
2. **Use `record`** para todos os DTOs (SourceMetadata, AnalysisResult, etc.)
3. **Injeção por construtor** em todas as classes de serviço
4. **SLF4J** para logging em todas as classes (`private static final Logger log = LoggerFactory.getLogger(...)`)
5. **Tratamento de erros** robusto: nunca engolir exceções, sempre logar e propagar ou wrapar
6. **Retry com backoff** para chamadas à API (3 tentativas, 1s/2s/4s)
7. **Sanitização** do output da IA: remover ```java fences, validar JSON
8. **Nenuma API key hardcoded** — sempre via variável de ambiente
9. **Testes unitários** para todas as classes de lógica (não testar CLI diretamente)
10. **Código em português** apenas nos comentários e logs; nomes de variáveis/métodos em inglês

---

## FORMATO DE ENTREGA

Para cada arquivo, entregue no seguinte formato:

```
📄 [caminho/relativo/do/arquivo]
```

```[linguagem]
[código completo do arquivo]
```

**Ordem de entrega:**

1. `pom.xml`
2. Arquivos de `model/` (DTOs)
3. Arquivos de `parser/`
4. Arquivos de `resources/prompts/`
5. Arquivos de `core/`
6. Arquivos de `plugins/`
7. Arquivos de `validator/`
8. Arquivos de `output/`
9. Arquivos de `cli/`
10. `TestAgentApp.java`
11. Arquivos de `src/test/`
12. `samples/`
13. `README.md`
14. `logback.xml`

---

## EXEMPLO DE USO ESPERADO

```bash
# Setup
export OPENAI_API_KEY="sk-..."

# Compilar
mvn clean package

# Analisar código
java -jar target/test-agent-junit-1.0.0.jar analyze --source=samples/input/UserService.java

# Gerar testes
java -jar target/test-agent-junit-1.0.0.jar generate --source=samples/input/UserService.java --output=./output

# Análise completa + testes
java -jar target/test-agent-junit-1.0.0.jar full --source=samples/input/UserService.java --output=./output
```

**Output esperado:**

```
📊 Analisando: UserService.java
✅ Análise concluída (Score: 45/100)
🔍 3 pontos fracos encontrados
🚨 2 vulnerabilidades encontradas
📝 Relatório salvo em: ./output/analysis_report.md
🧪 Gerando testes...
✅ Testes gerados com sucesso
📄 Arquivo salvo em: ./output/UserServiceTest.java
```

---

Comece a implementar agora, seguindo a ordem de entrega especificada. Gere código completo para TODOS os arquivos listados. Não use placeholders como "// TODO" — implemente tudo.

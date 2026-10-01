# 🧪 test-agent-junit

Ferramenta CLI em Java que **revisa código-fonte Java com IA** e **gera automaticamente testes JUnit 5 + Mockito**.

Ela usa o **Semantic Kernel Java SDK** para orquestrar chamadas à OpenAI (`gpt-4o`) com **Prompt Chaining**: a saída JSON estruturada do Prompt 1 (análise) alimenta o Prompt 2 (geração de testes).

```
 Arquivo.java ──► SourceFileReader ──► SourceParser ──► SourceMetadata
                                                            │
                     ┌──────────────────────────────────────┘
                     ▼
     ┌─────────────── PromptChainExecutor (retry + backoff) ───────────────┐
     │  Prompt 1: analyze_code   {{$source_code}} {{$metadata}}            │
     │        │                                                            │
     │        ▼  JSON validado com Jackson ──► AnalysisResult              │
     │  Prompt 2: generate_tests {{$source_code}} {{$analysis_json}}       │
     │                           {{$package_name}}                         │
     │        │                                                            │
     │        ▼  sanitização (remove ```java) + TestValidator              │
     └──────────────────────────────── TestSuite ──────────────────────────┘
                     │                               │
                     ▼                               ▼
          ReportGenerator → analysis_report.md   TestFileWriter → ArquivoTest.java
```

---

## ✅ Pré-requisitos

| Ferramenta     | Versão               |
| -------------- | -------------------- |
| Java (JDK)     | 17+                  |
| Maven          | 3.8+                 |
| Chave OpenAI   | com acesso ao `gpt-4o` |

## 🔐 Variáveis de ambiente

| Variável           | Obrigatória | Descrição                                   |
| ------------------ | ----------- | ------------------------------------------- |
| `OPENAI_API_KEY`   | Sim         | Chave da API OpenAI. Nunca é gravada no código. |
| `OPENAI_MODEL_ID`  | Não         | Modelo a usar (padrão: `gpt-4o`).            |
| `LOG_DIR`          | Não         | Diretório dos logs em arquivo (padrão: `logs`). |

```bash
# Linux / macOS / Git Bash
export OPENAI_API_KEY="sk-..."

# PowerShell
$env:OPENAI_API_KEY = "sk-..."
```

## 🔨 Como compilar

> O projeto Maven fica na pasta [`test-agent-junit/`](test-agent-junit/). Execute os comandos abaixo a partir dela: `cd test-agent-junit`.

```bash
mvn clean package
```

Gera o fat jar `target/test-agent-junit-1.0.0.jar` (com todas as dependências) e executa os testes unitários.

## ▶️ Como executar

```bash
# Ajuda
java -jar target/test-agent-junit-1.0.0.jar --help

# Apenas análise → output/analysis_report.md
java -jar target/test-agent-junit-1.0.0.jar analyze --source=samples/input/UserService.java

# Apenas geração de testes → output/UserServiceTest.java
java -jar target/test-agent-junit-1.0.0.jar generate --source=samples/input/UserService.java --output=./output

# Análise completa + testes (uma única chamada ao prompt de análise)
java -jar target/test-agent-junit-1.0.0.jar full --source=samples/input/UserService.java --output=./output
```

| Opção            | Comandos | Descrição                                   |
| ---------------- | -------- | ------------------------------------------- |
| `--source`       | todos    | Caminho do arquivo `.java` (obrigatório)    |
| `--output`       | todos    | Diretório de saída (padrão: `./output`)     |
| `-v, --verbose`  | todos    | Mostra logs DEBUG no console                |

**Códigos de saída:** `0` sucesso · `1` erro na execução · `2` `OPENAI_API_KEY` ausente.

### Saída esperada

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

> **Windows:** para exibir os emojis corretamente, execute `chcp 65001` no terminal antes de rodar a CLI.

## 📁 Exemplo com o sample

| Arquivo | Conteúdo |
| ------- | -------- |
| [`samples/input/UserService.java`](test-agent-junit/samples/input/UserService.java) | Classe de entrada (sem validações, com dependência injetada) |
| [`samples/output/analysis_report.md`](test-agent-junit/samples/output/analysis_report.md) | Exemplo de relatório gerado pelo `ReportGenerator` |
| [`samples/output/UserServiceTest.java`](test-agent-junit/samples/output/UserServiceTest.java) | Exemplo de teste no formato que o Prompt 2 deve produzir (10 testes, `@Nested`, AAA, `verify`) |

As saídas da IA não são determinísticas: o conteúdo exato varia a cada execução.

## 🗂️ Estrutura do projeto

```
src/main/java/com/example/testagent/
├── TestAgentApp.java          # Entry point Picocli; valida OPENAI_API_KEY; configura logging
├── cli/                       # Subcomandos analyze, generate, full
├── core/
│   ├── KernelFactory.java     # Cria o Kernel (OpenAI) e carrega os prompts do classpath
│   ├── PromptChainExecutor.java # Prompt chaining + retry com backoff (1s, 2s)
│   └── ContextBuilder.java    # Builder das variáveis do Semantic Kernel
├── parser/                    # Leitura segura do arquivo e extração de metadados (regex)
├── plugins/                   # Funções do kernel (@DefineKernelFunction): análise e geração
├── model/                     # DTOs (records) com Jackson snake_case
├── output/                    # Escrita do Test.java e do relatório Markdown
└── validator/TestValidator.java # Validação estrutural do teste gerado
src/main/resources/
├── prompts/                   # analyze_code / generate_tests (.skprompt.txt + .config.json)
├── templates/report_template.md
└── logback.xml                # Console (WARN+, stderr) + arquivo rotativo (logs/)
```

## 🛡️ Robustez e segurança

- **Validação de entrada:** apenas `.java`, até 50 KB, não vazio, bloqueio de path traversal (`..`).
- **Retry com backoff exponencial:** 3 tentativas (espera de 1s e depois 2s) para falhas transitórias (rede, 408, 429, 5xx). Erros permanentes (ex.: 401 chave inválida) e JSON malformado falham na hora. O retry interno do SDK Azure é desabilitado para não multiplicar tentativas.
- **Sanitização do output da IA:** extração do JSON de dentro de markdown fences e texto ao redor; remoção de ```` ```java ```` do código gerado.
- **Validação do teste gerado:** `@Test`, `class ...Test`, imports do JUnit 5, ausência de markdown e delimitadores balanceados. Problemas são reportados como aviso.
- **Sem segredos no código:** a chave vem sempre de variável de ambiente.

> O código-fonte analisado é enviado à API da OpenAI. Não use a ferramenta com código que não possa sair do seu ambiente.

## 🧪 Testes do projeto

```bash
mvn test
```

| Classe de teste            | Cobertura |
| -------------------------- | --------- |
| `SourceFileReaderTest`     | arquivo válido, inexistente, extensão errada, vazio, path traversal, tamanho máximo |
| `SourceParserTest`         | package, classe, imports, métodos públicos (inclusive generics), dependências, anotações |
| `PromptChainExecutorTest`  | encadeamento dos prompts com Kernel mockado, retry/backoff, 401 sem retry, JSON malformado |
| `CodeAnalysisPluginTest`   | parse de JSON válido, JSON em markdown, JSON inválido, argumentos enviados ao Kernel |
| `ReportGeneratorTest`      | header, tabelas, escape de pipes, checklist, gravação em disco |
| `TestValidatorTest`        | validação estrutural do código gerado |

## 🧯 Solução de problemas

**`PKIX path building failed` / `SSLHandshakeException`:** um antivírus ou proxy corporativo (ex.: inspeção HTTPS do Avast) intercepta o TLS e a JVM não confia no certificado dele. No Windows, use o repositório de certificados do sistema:

```bash
# Maven
MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" mvn clean package

# CLI
java -Djavax.net.ssl.trustStoreType=Windows-ROOT -jar target/test-agent-junit-1.0.0.jar full --source=...
```

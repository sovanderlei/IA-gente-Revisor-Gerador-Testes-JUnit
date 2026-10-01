# 📊 Relatório de Análise — `UserService`

**Score geral:** 45/100 🔴
**Gerado em:** 2026-10-01 15:00:00

| Indicador | Quantidade |
| --- | --- |
| Pontos fracos | 3 |
| Vulnerabilidades | 2 |
| Cenários de teste recomendados | 10 |

---

## 🔍 Pontos Fracos (3)

| Linha | Categoria | Severidade | Descrição | Sugestão |
| --- | --- | --- | --- | --- |
| 9 | Validação de entrada | 🟡 MEDIUM | O construtor aceita um UserRepository nulo, adiando a falha para a primeira chamada de método. | Usar Objects.requireNonNull(repository, "repository") no construtor. |
| 13 | Tratamento de exceções | 🟡 MEDIUM | findById retorna null quando o usuário não existe, obrigando o chamador a tratar null. | Retornar Optional<User> ou lançar UserNotFoundException. |
| 17 | Falta de logs | 🟢 LOW | A remoção de usuários (operação destrutiva) não gera nenhum log de auditoria. | Registrar log de auditoria com o username e o resultado da operação. |

---

## 🚨 Vulnerabilidades (2)

| Linha | Tipo | Severidade | CWE | Descrição | Correção |
| --- | --- | --- | --- | --- | --- |
| 13 | Null Dereference | 🟠 HIGH | CWE-476 | O id não é validado; um id nulo é repassado diretamente ao repositório. | Validar o id (não nulo e positivo) antes de consultar o repositório. |
| 17 | Validação de entrada ausente | 🟠 HIGH | CWE-20 | username nulo ou vazio é repassado ao deleteByUsername, podendo causar remoções inesperadas dependendo da implementação. | Rejeitar username nulo/vazio com IllegalArgumentException e verificar autorização. |

---

## 🧪 Cenários de Teste Recomendados (10)

- [ ] **findById** — `id existente` [HAPPY]: Retorna o usuário fornecido pelo repositório.
- [ ] **findById** — `id inexistente` [ERROR]: Retorna null quando o repositório não encontra o usuário.
- [ ] **findById** — `id nulo` [NULL]: Documenta que o id nulo é repassado ao repositório.
- [ ] **findById** — `id zero ou negativo` [BOUNDARY]: Verifica o comportamento com ids limite.
- [ ] **findById** — `falha no repositório` [ERROR]: A exceção do repositório é propagada.
- [ ] **deleteUser** — `username válido` [HAPPY]: Chama deleteByUsername com o username informado.
- [ ] **deleteUser** — `username nulo` [NULL]: Documenta que o username nulo é repassado ao repositório.
- [ ] **deleteUser** — `username vazio` [BOUNDARY]: Documenta que o username vazio é repassado ao repositório.
- [ ] **deleteUser** — `falha no repositório` [ERROR]: A exceção do repositório é propagada.
- [ ] **deleteUser** — `remoções concorrentes` [CONCURRENCY]: Múltiplas threads removendo não perdem chamadas ao repositório.

---

_Relatório gerado automaticamente pelo test-agent-junit (Semantic Kernel + OpenAI)._

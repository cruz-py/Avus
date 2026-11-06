# Avus

App Android (pt-PT) para facilitar a inscrição de um familiar em Estruturas Residenciais para Pessoas Idosas (ERPI / lares) em Portugal.

## Funcionalidades

- **Lares** — importa a lista oficial da Carta Social (CSV de dados.gov.pt), filtra só as ERPI; pesquisa e filtros por distrito/concelho; seleção múltipla; adicionar/editar lares à mão.
- **Utente** — ficha do utente e do familiar responsável; lista de documentos habitualmente pedidos, com anexos (PDF/fotografia).
- **Enviar** — gera a carta de pré-inscrição (editável) e abre a app de email com tudo preenchido: um email para todos (lares em Bcc) ou um email por lar; carta em PDF opcional.
- **Respostas** — acompanhamento dos contactos (enviado, respondeu, visita, lista de espera, vaga…) com notas.

## Privacidade

Todos os dados ficam apenas no telemóvel (backups desativados). Nada é enviado sem o utilizador carregar em "Enviar" na sua app de email.

## Desenvolvimento

- Kotlin + Jetpack Compose (Material 3), minSdk 26
- `./gradlew assembleDebug` — compilar
- `./gradlew testDebugUnitTest` — testes (importador CSV e gerador da carta)

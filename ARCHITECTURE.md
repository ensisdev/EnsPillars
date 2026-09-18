# EnsPillars Phase 1 Architecture

`api` stable domain contracts; `game` arena domain/lifecycle; `platform` version-dependent boundary; `config` configuration; `storage` persistence boundary; `command` command surface; `util` cross-cutting helpers.

State flow: DISABLED -> WAITING -> STARTING -> PRE_GAME -> CAGED -> ACTIVE -> FINAL -> ENDING -> RESETTING -> WAITING.

Baseline: Paper API 1.20.1 / Java 17. Newer APIs must be isolated behind platform adapters.

---
name: serena
description: Use Serena MCP for semantic code intelligence in this repo. Use when navigating or refactoring code by symbols, finding declarations, implementations, references, diagnostics, callers, or doing symbol-level edits; especially useful for Kotlin/Java source work, cross-file code structure questions, and non-trivial refactors. Do not use for tiny docs-only edits, exact log inspection, or when Serena MCP/tools are unavailable.
---

# Serena

Serena is this repo's semantic code navigation and refactor layer. Pair it with LeanCTX: Serena for symbols and references; LeanCTX for compressed reads, text search, and shell output.

## Workflow

1. Activate this repo as project if needed: `mc-battlepass` or current working directory.
2. Read initial Serena instructions once per session when MCP tools expose them.
3. Use symbol tools before broad text search when task concerns declarations, implementations, callers, references, or class/object structure.
4. Use diagnostics before and after risky symbol edits when available.
5. Fall back to LeanCTX when Serena is missing, slow, or wrong for task shape; mention fallback in handoff.

## Preferred Operations

- `get_symbols_overview`: first pass on unfamiliar Kotlin/Java files.
- `find_symbol`: locate classes, objects, methods, properties, and nested symbols.
- `find_referencing_symbols`: find callers/usages before changing APIs or behavior.
- `find_implementations` / `find_declaration`: resolve contracts, interfaces, and overrides.
- `replace_symbol_body`, `insert_before_symbol`, `insert_after_symbol`: use for scoped code changes when safer than line edits.
- `get_diagnostics_for_file`: verify local semantic errors after edits.

## Boundaries

- Keep AGENTS.md forced LeanCTX policy: use LeanCTX for file reads, searches, and shell commands unless Serena semantic tools are the better fit.
- Do not use Serena for tiny text-only/doc-only edits, exact command logs, generated artifact churn, or simple path existence checks.
- Do not run long project-wide Serena health/index commands during normal tasks unless the user asks or validation needs it; this mod can exceed 10 minutes.
- If Serena says onboarding/memories missing, initialize memories, then continue.

## Local Setup

Expected local setup:

```text
serena start-mcp-server --context=codex --project-from-cwd
```

Project config lives at `.serena/project.yml`. See `docs/context/serena.md` for install and client setup details.

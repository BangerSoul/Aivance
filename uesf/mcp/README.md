# UESF over MCP

`uesf.server.json` declares how the framework's tooling is exposed as Model Context
Protocol tools so agents can call validation, scaffolding, and the graph directly
instead of typing shell commands.

## Today

The tools are **directly runnable as CLI commands** (stdlib Python, no deps) — the
manifest is the contract a runtime server must implement:

```bash
python3 tools/validate_framework.py            # validate
python3 tools/validate_framework.py --graph   # dependency graph
python3 tools/validate_framework.py --list    # inventory
python3 tools/skill_scaffold.py new pf slug   # scaffold
```

## Roadmap (R-06)

A stdlib-only JSON-RPC server over stdio implementing the manifest: `initialize`,
`tools/list`, `tools/call`. Transport and protocol follow the MCP spec; no external
dependencies. The server validates the framework, scaffolds skills, and returns
structured reports to the agent.

## Why

- Agents get deterministic answers with meaningful exit codes instead of parsing
  prose.
- CI and agents share the *same* gate — no drift between what the pipeline checks
  and what the agent believes.
- The framework's automation principles (stdlib-only, deterministic,
  human-auditable) carry over unchanged.

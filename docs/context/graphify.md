# Graphify

Graphify is the durable project knowledge graph. Use it for broad repo navigation, architecture questions, and cross-file relationships.

## Install

```bash
uv tool install graphifyy
```

## Agent Setup

```bash
graphify install --platform codex
graphify install --platform pi
```

## Build Graph

```bash
graphify .
```

## Query

```bash
graphify query "show the main architecture"
graphify path "FeatureA" "FeatureB"
graphify explain "ImportantClass"
```

## Team Workflow

Commit durable graph outputs when they are useful for the team:

```text
graphify-out/graph.json
graphify-out/GRAPH_REPORT.md
graphify-out/graph.html
```

Do not commit local metadata:

```text
graphify-out/manifest.json
graphify-out/cost.json
graphify-out/cache/
```

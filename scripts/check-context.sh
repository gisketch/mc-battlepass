#!/usr/bin/env bash
set -euo pipefail

missing=0

if [[ -f docs/context/graphify.md ]]; then
  if ! command -v graphify >/dev/null 2>&1; then
    echo "missing command: graphify"
    missing=1
  fi
fi

if [[ -f docs/context/lean-ctx.md ]]; then
  if ! command -v lean-ctx >/dev/null 2>&1; then
    echo "missing command: lean-ctx"
    missing=1
  fi
fi

if [[ -d .pi ]]; then
  if ! command -v pi >/dev/null 2>&1; then
    echo "missing command: pi"
    missing=1
  fi
fi

if [[ "$missing" -ne 0 ]]; then
  echo "context tools not fully installed"
  exit 1
fi

echo "context tools ok"

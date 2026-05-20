#!/usr/bin/env bash
set -euo pipefail

want_serena="${SONATA_SERENA:-1}"
want_graphify="${SONATA_GRAPHIFY:-1}"
want_leanctx="${SONATA_LEANCTX:-1}"
want_pi="${SONATA_PI:-1}"
want_codex="${SONATA_CODEX:-1}"

echo "Sonata context setup"

if [[ "$want_serena" == "1" ]]; then
  if ! command -v serena >/dev/null 2>&1; then
    echo "serena not found"
    echo "Install: uv tool install -p 3.13 serena-agent@latest --prerelease=allow"
  else
    serena init || true
    if [[ "$want_codex" == "1" ]]; then
      serena setup codex || true
      echo "Codex app sessions may need: Activate the current dir as project using serena"
    fi
  fi
fi

if [[ "$want_graphify" == "1" ]]; then
  if ! command -v graphify >/dev/null 2>&1; then
    echo "graphify not found"
    echo "Install: uv tool install graphifyy"
  else
    if [[ "$want_codex" == "1" ]]; then
      graphify install --platform codex || true
    fi
    if [[ "$want_pi" == "1" ]]; then
      graphify install --platform pi || true
    fi
    graphify . --no-viz || true
  fi
fi

if [[ "$want_leanctx" == "1" ]]; then
  if ! command -v lean-ctx >/dev/null 2>&1; then
    echo "lean-ctx not found"
    echo "Install: npm install -g lean-ctx-bin"
    echo "Or: curl -fsSL https://leanctx.com/install.sh | sh"
  else
    lean-ctx setup || true
    if [[ "$want_codex" == "1" ]]; then
      lean-ctx init --agent codex || true
    fi
    if [[ "$want_pi" == "1" ]]; then
      lean-ctx init --agent pi || true
    fi
    lean-ctx doctor || true
  fi
fi

if [[ "$want_pi" == "1" ]] && command -v pi >/dev/null 2>&1; then
  echo "Pi detected"
  echo "Optional: pi install npm:pi-lean-ctx -l"
  echo "Optional after review: pi install -l npm:pi-serena-tools"
fi

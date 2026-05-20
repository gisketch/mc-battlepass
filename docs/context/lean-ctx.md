# LeanCTX

LeanCTX reduces repeated file-read and shell-output context cost. Use it for compressed reads, command output, and session context.

## Install

Pick one:

```bash
curl -fsSL https://leanctx.com/install.sh | sh
brew tap yvgude/lean-ctx && brew install lean-ctx
npm install -g lean-ctx-bin
cargo install lean-ctx
```

## Setup

```bash
lean-ctx setup
lean-ctx init --agent codex
lean-ctx init --agent pi
lean-ctx doctor
```

## Pi Package Path

```bash
pi install npm:pi-lean-ctx -l
```

## Useful Commands

```bash
lean-ctx read AGENTS.md -m map
lean-ctx -c "git status"
lean-ctx gain --live
lean-ctx doctor --json
```

## Escape Hatches

```bash
lean-ctx-off
lean-ctx -c --raw "git status"
```

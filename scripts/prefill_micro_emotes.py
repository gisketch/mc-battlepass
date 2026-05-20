#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib
import re
import sys
import tomllib


SECTION_RE = re.compile(r"^\s*\[\[(?P<section>[A-Za-z0-9_]+)\]\]\s*$")
FIELD_RE = re.compile(r'^(?P<key>[A-Za-z0-9_]+)\s*=\s*"(?P<value>.*)"\s*$')

EXCHANGE_SECTIONS = {"exchanges", "trainer_exchanges"}
INSERT_AFTER_KEYS = ("response", "res", "line")

DEFAULT_SAFE_MICRO = (
    "hi",
    "wave",
    "clap",
    "facepalm",
    "shrug",
    "proud",
    "lookout",
    "speaking",
    "head_scratches",
    "hands_in_back",
    "threaten",
    "six_seven",
)
POSTURE_IDS = {"sit", "sit_cool", "sit_cool_2", "sit_cute", "sit_lying_almost", "sit_minimal", "lay_on_back"}

TOPIC_DEFAULTS = {
    "greeting": ("wave", "hi"),
    "farewell": ("wave", "wave"),
    "weather": ("lookout", "shrug"),
    "patrol": ("hands_in_back", "lookout"),
    "training": ("proud", "clap"),
    "mentor": ("proud", "clap"),
    "chores": ("lookout", "clap"),
    "adventure": ("lookout", "wave"),
    "league": ("proud", "clap"),
    "plaza": ("wave", "clap"),
    "pokemon": ("lookout", "proud"),
    "match_prep": ("lookout", "proud"),
    "type_matchups": ("lookout", "shrug"),
    "league_records": ("proud", "clap"),
    "gym_habits": ("lookout", "shrug"),
    "rivals": ("threaten", "proud"),
    "recovery": ("shrug", "clap"),
    "clean_switches": ("lookout", "proud"),
    "badges": ("proud", "clap"),
    "shop_stock": ("lookout", "clap"),
    "research": ("lookout", "shrug"),
}

KEYWORD_EMOTES = (
    (re.compile(r"\b(threat|threaten|enemy|bandit|guard|fight|spar|attack|intimidat|warning|warn|danger|hostile)\b", re.I), "threaten"),
    (re.compile(r"\b(hello|hi|morning|welcome|breakfast|tea)\b", re.I), "wave"),
    (re.compile(r"\b(thank|thanks|grateful|well done|good work|excellent|perfect|impressive|beautiful|congrat)\b", re.I), "clap"),
    (re.compile(r"\b(proud|ready|won|win|victory|champion|badge|record|strong|flawless|best)\b", re.I), "proud"),
    (re.compile(r"\b(duty|formal|order|rank|report|leader|rules|discipline|strict|schedule)\b", re.I), "hands_in_back"),
    (re.compile(r"\b(watch|look|check|saw|see|map|track|sense|patrol|scout|search|found|route|storm|cloud|wind|rain|fog|weather)\b", re.I), "lookout"),
    (re.compile(r"\b(maybe|how|why|odd|strange|confused|uncertain|probably|think|study|learn)\b|\?", re.I), "head_scratches"),
    (re.compile(r"\b(say|said|speak|talk|tell|ask|answer|reply|explain)\b", re.I), "speaking"),
    (re.compile(r"\b(stop|wrong|lost|fail|bad|thief|fake|dirty|sigh|ugh|no threat|too much|can't|cannot)\b", re.I), "facepalm"),
)


def load_micro_emotes(npc_dir: pathlib.Path) -> set[str]:
    path = npc_dir / "emotes.toml"
    if not path.exists():
        return set(DEFAULT_SAFE_MICRO)
    text = path.read_text(encoding="ascii")
    try:
        data = tomllib.loads(text)
    except tomllib.TOMLDecodeError:
        data = json.loads(text)
    ids = set()
    for entry in data.get("emotes", []):
        emote_id = str(entry.get("id", "")).strip()
        surfaces = {str(surface).strip() for surface in entry.get("surfaces", [])}
        if (
            emote_id
            and entry.get("enabled", True)
            and "micro" in surfaces
            and emote_id not in POSTURE_IDS
            and not entry.get("posture", False)
            and not entry.get("ambient_only", False)
        ):
            ids.add(emote_id)
    return ids or set(DEFAULT_SAFE_MICRO)


def stable_pick(seed: str, choices: tuple[str, ...]) -> str:
    digest = hashlib.sha1(seed.encode("utf-8")).digest()
    return choices[digest[0] % len(choices)]


def emote_for(topic: str, text: str, entry_id: str, role: str, safe: set[str]) -> str:
    haystack = f"{topic} {text}".strip()
    for pattern, emote in KEYWORD_EMOTES:
        if emote in safe and pattern.search(haystack):
            if role == "target" and emote == "facepalm" and "shrug" in safe:
                return "shrug"
            return emote
    if topic in TOPIC_DEFAULTS:
        emote = TOPIC_DEFAULTS[topic][0 if role == "source" else 1]
        if emote in safe:
            return emote
    fallback = tuple(emote for emote in ("wave", "hi", "shrug", "clap", "proud", "lookout") if emote in safe)
    return stable_pick(f"{entry_id}:{role}", fallback or tuple(sorted(safe)))


def parse_block(lines: list[str]) -> dict[str, str]:
    values = {}
    for line in lines:
        match = FIELD_RE.match(line.strip())
        if match:
            values[match.group("key")] = match.group("value")
    return values


def set_emote_fields(lines: list[str], source: str, target: str) -> tuple[list[str], bool, bool]:
    changed = False
    saw_source = False
    saw_target = False
    result = []
    insert_at = len(lines)
    for line in lines:
        key_match = re.match(r"^\s*([A-Za-z0-9_]+)\s*=", line)
        if key_match and key_match.group(1) == "source_emote":
            saw_source = True
            changed = changed or line != f'source_emote = "{source}"'
            continue
        if key_match and key_match.group(1) == "target_emote":
            saw_target = True
            changed = changed or line != f'target_emote = "{target}"'
            continue
        result.append(line)
    insert_at = len(result)
    for index, line in enumerate(result):
        match = re.match(r"^\s*([A-Za-z0-9_]+)\s*=", line)
        if match and match.group(1) in INSERT_AFTER_KEYS:
            insert_at = index + 1
    result[insert_at:insert_at] = [f'source_emote = "{source}"', f'target_emote = "{target}"']
    source_changed = changed or not saw_source
    target_changed = changed or not saw_target
    return result, source_changed, target_changed


def rewrite_block(section: str, lines: list[str], safe: set[str]) -> tuple[list[str], int, int, int]:
    if section not in EXCHANGE_SECTIONS:
        return lines, 0, 0, 0
    values = parse_block(lines)
    entry_id = values.get("id", "")
    topic = values.get("topic", "")
    source_text = values.get("line", "")
    target_text = values.get("response", values.get("res", ""))
    source = emote_for(topic, source_text, entry_id, "source", safe)
    target = emote_for(topic, target_text, entry_id, "target", safe)
    updated, source_changed, target_changed = set_emote_fields(lines, source, target)
    return updated, 1, int(source_changed), int(target_changed)


def rewrite_file(path: pathlib.Path, safe: set[str], dry_run: bool) -> tuple[int, int, int]:
    text = path.read_text(encoding="ascii")
    newline = "\r\n" if "\r\n" in text else "\n"
    raw_lines = text.splitlines()
    output: list[str] = []
    block: list[str] = []
    section = ""
    entries = sources = targets = 0

    def flush() -> None:
        nonlocal block, entries, sources, targets, output
        if not block:
            return
        rewritten, entry_count, source_count, target_count = rewrite_block(section, block, safe)
        output.extend(rewritten)
        entries += entry_count
        sources += source_count
        targets += target_count
        block = []

    for line in raw_lines:
        match = SECTION_RE.match(line)
        if match:
            flush()
            section = match.group("section")
            block = [line]
        else:
            block.append(line)
    flush()

    rewritten_text = newline.join(output) + (newline if text.endswith(("\n", "\r\n")) else "")
    if rewritten_text != text and not dry_run:
        path.write_text(rewritten_text, encoding="ascii", newline="")
    return entries, sources, targets


def validate_files(npc_dir: pathlib.Path, safe: set[str]) -> tuple[int, int]:
    missing = 0
    invalid = 0
    for path in sorted(npc_dir.glob("micro_interactions*.toml")):
        lines = path.read_text(encoding="ascii").splitlines()
        section = ""
        block: list[str] = []

        def check_block() -> None:
            nonlocal missing, invalid
            if section not in EXCHANGE_SECTIONS or not block:
                return
            values = parse_block(block)
            for key in ("source_emote", "target_emote"):
                value = values.get(key, "").strip()
                if not value:
                    missing += 1
                elif value not in safe:
                    invalid += 1

        for line in lines:
            match = SECTION_RE.match(line)
            if match:
                check_block()
                section = match.group("section")
                block = [line]
            else:
                block.append(line)
        check_block()
    return missing, invalid


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--npc-dir", required=True)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    npc_dir = pathlib.Path(args.npc_dir)
    if not npc_dir.is_dir():
        print(f"missing npc dir: {npc_dir}", file=sys.stderr)
        return 2
    safe = load_micro_emotes(npc_dir)
    unknown_defaults = set(DEFAULT_SAFE_MICRO) - safe
    if unknown_defaults:
        print(f"warning: unavailable default emotes skipped: {', '.join(sorted(unknown_defaults))}", file=sys.stderr)
    files = sorted(npc_dir.glob("micro_interactions*.toml"))
    total_entries = total_sources = total_targets = 0
    for path in files:
        entries, sources, targets = rewrite_file(path, safe, args.dry_run)
        if entries:
            print(f"{path.name}: paired={entries} source_updates={sources} target_updates={targets}")
        total_entries += entries
        total_sources += sources
        total_targets += targets
    missing, invalid = validate_files(npc_dir, safe)
    print(f"safe_micro={','.join(sorted(safe))}")
    print(f"summary files={len(files)} paired={total_entries} source_updates={total_sources} target_updates={total_targets} missing={missing} invalid={invalid}")
    return 1 if missing or invalid else 0


if __name__ == "__main__":
    raise SystemExit(main())

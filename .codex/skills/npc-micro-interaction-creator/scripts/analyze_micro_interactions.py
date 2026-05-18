#!/usr/bin/env python3
import argparse
import collections
import itertools
import pathlib
import re
import sys
import tomllib

EXCLUDED_NPC_FILES = {
    "settings.toml",
    "state.toml",
    "generic_quests.toml",
    "friendship_messages.toml",
}
BAD_TERMS = re.compile(
    r"\b(romance|romantic|kiss|dating|crush|wife|husband|girlfriend|boyfriend|married|lover|grief|Marin)\b|```|optional_",
    re.I,
)
REQUIRED_FIELDS = ("id", "topic", "line", "response", "weight")


def load_toml(path):
    raw = path.read_bytes()
    if re.search(rb"[^\x00-\x7F]", raw):
        raise ValueError("non-ascii bytes")
    return tomllib.loads(raw.decode("ascii"))


def npc_files(npc_dir):
    for path in sorted(npc_dir.glob("*.toml")):
        name = path.name
        if name in EXCLUDED_NPC_FILES or name.startswith("micro_interactions"):
            continue
        yield path


def load_npcs(npc_dir):
    npcs = {}
    for path in npc_files(npc_dir):
        try:
            data = load_toml(path)
        except Exception as exc:
            print(f"WARN npc parse failed {path.name}: {exc}", file=sys.stderr)
            continue
        npc_id = str(data.get("id") or path.stem).strip()
        npcs[npc_id] = {
            "id": npc_id,
            "name": str(data.get("name") or npc_id),
            "title": str(data.get("title") or ""),
            "class": str(data.get("class") or ""),
            "store": str(data.get("store") or ""),
            "file": path.name,
        }
    return npcs


def is_trainer(npc):
    npc_id = npc["id"]
    title = npc["title"].lower()
    return (
        npc_id.startswith(("trainer_", "gym_", "elite_", "johto_", "hoenn_"))
        or "champion" in npc_id
        or npc_id == "prof_chowfan"
        or any(word in title for word in ("gym", "elite", "champion", "rival"))
    )


def micro_files(npc_dir):
    return sorted(npc_dir.glob("micro_interactions*.toml"))


def load_exchanges(npc_dir):
    exchanges = []
    trainer = []
    for path in micro_files(npc_dir):
        data = load_toml(path)
        for entry in data.get("exchanges", []):
            entry["_file"] = path.name
            exchanges.append(entry)
        for entry in data.get("trainer_exchanges", []):
            entry["_file"] = path.name
            trainer.append(entry)
    return exchanges, trainer


def validate_entries(entries, section):
    errors = []
    ids = collections.Counter()
    for entry in entries:
        entry_id = str(entry.get("id", ""))
        ids[entry_id] += 1
        file_name = entry.get("_file", "?")
        for field in REQUIRED_FIELDS:
            if field not in entry or entry[field] in ("", [], None):
                errors.append(f"{file_name}:{entry_id}: missing {field}")
        for field in ("line", "response"):
            text = str(entry.get(field, ""))
            if len(text) > 95:
                errors.append(f"{file_name}:{entry_id}: {field} too long ({len(text)})")
            if BAD_TERMS.search(text):
                errors.append(f"{file_name}:{entry_id}: bad term in {field}")
    for entry_id, count in ids.items():
        if count > 1:
            errors.append(f"duplicate id {entry_id} x{count}")
    return errors


def pair_counts(entries):
    counts = collections.Counter()
    for entry in entries:
        sources = entry.get("source_ids") or []
        targets = entry.get("target_ids") or []
        for source in sources:
            for target in targets:
                if source and target:
                    counts[tuple(sorted((source, target)))] += 1
    return counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--npc-dir", required=True)
    parser.add_argument("--focus")
    parser.add_argument("--summary", action="store_true")
    parser.add_argument("--validate", action="store_true")
    parser.add_argument("--include-trainers", action="store_true")
    args = parser.parse_args()

    npc_dir = pathlib.Path(args.npc_dir)
    npcs = load_npcs(npc_dir)
    exchanges, trainer = load_exchanges(npc_dir)

    if args.validate:
        errors = validate_entries(exchanges, "exchanges") + validate_entries(trainer, "trainer_exchanges")
        if errors:
            print("\n".join(errors[:200]))
            raise SystemExit(1)
        print(f"validation ok: exchanges={len(exchanges)} trainer_exchanges={len(trainer)} files={len(micro_files(npc_dir))}")

    if args.summary or not args.validate:
        print(f"npcs={len(npcs)} exchanges={len(exchanges)} trainer_exchanges={len(trainer)} files={len(micro_files(npc_dir))}")
        custom = [npc for npc in npcs.values() if args.include_trainers or not is_trainer(npc)]
        counts = pair_counts(exchanges)
        print(f"custom_npcs={len(custom)} pair_specific_pairs={len(counts)}")
        by_npc = collections.Counter()
        for (a, b), count in counts.items():
            by_npc[a] += count
            by_npc[b] += count
        for npc in sorted(custom, key=lambda item: item["id"]):
            print(f"{npc['id']}: pair_specific={by_npc[npc['id']]}")

    if args.focus:
        if args.focus not in npcs:
            raise SystemExit(f"unknown focus npc: {args.focus}")
        custom_ids = [npc["id"] for npc in npcs.values() if args.include_trainers or not is_trainer(npc)]
        counts = pair_counts(exchanges)
        missing = []
        for other in sorted(custom_ids):
            if other == args.focus:
                continue
            key = tuple(sorted((args.focus, other)))
            if counts[key] == 0:
                missing.append(other)
        print(f"missing_pair_specific_for={args.focus} count={len(missing)}")
        for other in missing:
            print(other)


if __name__ == "__main__":
    main()

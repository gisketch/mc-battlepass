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
SOLO_REQUIRED_FIELDS = ("id", "topic", "line", "weight")
AMBIGUOUS_ALIASES = {
    "Blue",
    "May",
    "Silver",
    "Will",
}


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


def npc_alias_maps(npcs):
    aliases = {}
    ambiguous = {}
    for npc in npcs.values():
        npc_id = npc["id"]
        name = npc["name"].strip()
        candidates = [name]
        first = re.split(r"\s+", name)[0].strip(".,")
        if first and first != name:
            candidates.append(first)
        for alias in candidates:
            if len(alias) < 2 or not re.search(r"[A-Z]", alias):
                continue
            bucket = ambiguous if alias in AMBIGUOUS_ALIASES else aliases
            bucket.setdefault(alias, set()).add(npc_id)
    return aliases, ambiguous


def mentioned_npc_ids(text, aliases):
    mentioned = set()
    for alias, npc_ids in aliases.items():
        if re.search(rf"\b{re.escape(alias)}\b", text):
            mentioned.update(npc_ids)
    return mentioned


def covered_npc_ids(entry, include_target=True):
    covered = set(entry.get("source_ids") or [])
    if include_target:
        covered.update(entry.get("target_ids") or [])
    covered.update(entry.get("required_spawned_ids") or [])
    return covered


def missing_required_spawned(entry, aliases, include_response=True, include_target=True):
    text = str(entry.get("line", ""))
    if include_response:
        text += " " + str(entry.get("response", ""))
    mentioned = mentioned_npc_ids(text, aliases)
    return sorted(mentioned - covered_npc_ids(entry, include_target=include_target))


def ambiguous_mentions(entry, ambiguous_aliases, include_response=True):
    text = str(entry.get("line", ""))
    if include_response:
        text += " " + str(entry.get("response", ""))
    found = []
    for alias, npc_ids in ambiguous_aliases.items():
        if re.search(rf"\b{re.escape(alias)}\b", text):
            found.append(f"{alias}=>{','.join(sorted(npc_ids))}")
    return found


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


def load_solo_moments(npc_dir):
    moments = []
    for path in micro_files(npc_dir):
        data = load_toml(path)
        for entry in data.get("solo_moments", []):
            entry["_file"] = path.name
            moments.append(entry)
    return moments


def validate_entries(entries, section, aliases=None, ambiguous_aliases=None):
    errors = []
    warnings = []
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
        if aliases is not None:
            missing = missing_required_spawned(entry, aliases)
            if missing:
                errors.append(f"{file_name}:{entry_id}: missing required_spawned_ids {','.join(missing)}")
        if ambiguous_aliases is not None:
            ambiguous = ambiguous_mentions(entry, ambiguous_aliases)
            if ambiguous:
                warnings.append(f"{file_name}:{entry_id}: ambiguous name mention {';'.join(ambiguous)}")
    for entry_id, count in ids.items():
        if count > 1:
            errors.append(f"duplicate id {entry_id} x{count}")
    return errors, warnings


def validate_solo_moments(entries, aliases=None, ambiguous_aliases=None):
    errors = []
    warnings = []
    ids = collections.Counter()
    for entry in entries:
        entry_id = str(entry.get("id", ""))
        ids[entry_id] += 1
        file_name = entry.get("_file", "?")
        for field in SOLO_REQUIRED_FIELDS:
            if field not in entry or entry[field] in ("", [], None):
                errors.append(f"{file_name}:{entry_id}: missing {field}")
        text = str(entry.get("line", ""))
        if len(text) > 95:
            errors.append(f"{file_name}:{entry_id}: line too long ({len(text)})")
        if BAD_TERMS.search(text):
            errors.append(f"{file_name}:{entry_id}: bad term in line")
        if aliases is not None:
            missing = missing_required_spawned(entry, aliases, include_response=False, include_target=False)
            if missing:
                errors.append(f"{file_name}:{entry_id}: missing required_spawned_ids {','.join(missing)}")
        if ambiguous_aliases is not None:
            ambiguous = ambiguous_mentions(entry, ambiguous_aliases, include_response=False)
            if ambiguous:
                warnings.append(f"{file_name}:{entry_id}: ambiguous name mention {';'.join(ambiguous)}")
    for entry_id, count in ids.items():
        if count > 1:
            errors.append(f"duplicate solo id {entry_id} x{count}")
    return errors, warnings


def toml_array(values):
    return "[" + ", ".join(f'"{value}"' for value in values) + "]"


def parse_block(block):
    data = tomllib.loads(block)
    for key in ("exchanges", "trainer_exchanges", "solo_moments"):
        entries = data.get(key)
        if entries:
            return key, entries[0]
    return "", {}


def update_required_spawned_line(block, required):
    required = sorted(set(required))
    replacement = f"required_spawned_ids = {toml_array(required)}"
    if re.search(r"(?m)^required_spawned_ids\s*=", block):
        return re.sub(r"(?m)^required_spawned_ids\s*=.*$", replacement, block, count=1)
    preferred_fields = ("target_tags", "source_tags", "target_ids", "source_ids", "activities")
    fallback_fields = ("weight",)
    lines = block.splitlines()
    insert_at = None
    for index, line in enumerate(lines):
        if any(re.match(rf"{field}\s*=", line) for field in preferred_fields):
            insert_at = index + 1
    if insert_at is None:
        for index, line in enumerate(lines):
            if any(re.match(rf"{field}\s*=", line) for field in fallback_fields):
                insert_at = index + 1
    if insert_at is None:
        insert_at = len(lines)
    lines.insert(insert_at, replacement)
    return "\n".join(lines)


def fix_required_spawned_file(path, aliases):
    raw = path.read_text(encoding="ascii")
    matches = list(re.finditer(r"(?m)^\[\[(exchanges|trainer_exchanges|solo_moments)\]\]\s*$", raw))
    if not matches:
        return 0
    output = []
    cursor = 0
    changed = 0
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(raw)
        block = raw[start:end].rstrip("\n")
        output.append(raw[cursor:start])
        try:
            section, entry = parse_block(block)
        except Exception:
            output.append(raw[start:end])
            cursor = end
            continue
        missing = missing_required_spawned(
            entry,
            aliases,
            include_response=section != "solo_moments",
            include_target=section != "solo_moments",
        )
        if missing:
            required = list(entry.get("required_spawned_ids") or []) + missing
            output.append(update_required_spawned_line(block, required))
            changed += 1
            if raw[end - 1:end] == "\n":
                output.append("\n")
        else:
            output.append(raw[start:end])
        cursor = end
    output.append(raw[cursor:])
    if changed:
        path.write_text("".join(output), encoding="ascii", newline="\n")
    return changed


def fix_required_spawned(npc_dir, aliases):
    total = 0
    changed_files = 0
    for path in micro_files(npc_dir):
        changed = fix_required_spawned_file(path, aliases)
        if changed:
            changed_files += 1
            total += changed
            print(f"fixed {path.name}: entries={changed}")
    print(f"fixed required_spawned_ids entries={total} files={changed_files}")
    return total


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
    parser.add_argument("--fix-required-spawned", action="store_true")
    parser.add_argument("--include-trainers", action="store_true")
    args = parser.parse_args()

    npc_dir = pathlib.Path(args.npc_dir)
    npcs = load_npcs(npc_dir)
    exchanges, trainer = load_exchanges(npc_dir)
    solo_moments = load_solo_moments(npc_dir)
    aliases, ambiguous_aliases = npc_alias_maps(npcs)

    if args.fix_required_spawned:
        fix_required_spawned(npc_dir, aliases)
        exchanges, trainer = load_exchanges(npc_dir)
        solo_moments = load_solo_moments(npc_dir)

    if args.validate:
        exchange_errors, exchange_warnings = validate_entries(exchanges, "exchanges", aliases, ambiguous_aliases)
        trainer_errors, trainer_warnings = validate_entries(trainer, "trainer_exchanges", aliases, ambiguous_aliases)
        solo_errors, solo_warnings = validate_solo_moments(solo_moments, aliases, ambiguous_aliases)
        errors = exchange_errors + trainer_errors + solo_errors
        warnings = exchange_warnings + trainer_warnings + solo_warnings
        if warnings:
            print("\n".join(f"WARN {warning}" for warning in warnings[:200]), file=sys.stderr)
        if errors:
            print("\n".join(errors[:200]))
            raise SystemExit(1)
        print(f"validation ok: exchanges={len(exchanges)} trainer_exchanges={len(trainer)} solo_moments={len(solo_moments)} files={len(micro_files(npc_dir))}")

    if args.summary or not args.validate:
        print(f"npcs={len(npcs)} exchanges={len(exchanges)} trainer_exchanges={len(trainer)} solo_moments={len(solo_moments)} files={len(micro_files(npc_dir))}")
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

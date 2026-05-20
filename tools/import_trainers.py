#!/usr/bin/env python3
"""Import public trainer roster data into CKDM random trainer catalog JSON.

Inputs are local source checkouts. This script does not download ROM data.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
from pathlib import Path


DEFAULT_TARGET = (
    Path.home()
    / "AppData/Roaming/gisketch/modsync/data/launchers/prismlauncher-cracked/11.0.2-1"
    / "instances/modsync-ckdm-2026/.minecraft/config/gisketchs_chowkingdom_mod/random_trainers/catalog"
)


SOURCE_SPECS = {
    "pokered": ("pret_pokered", "Kanto"),
    "pokecrystal": ("pret_pokecrystal", "Johto/Kanto"),
    "pokeemerald": ("pret_pokeemerald", "Hoenn"),
    "pokefirered": ("pret_pokefirered", "Kanto/Sevii"),
    "pokeplatinum": ("pret_pokeplatinum", "Sinnoh"),
    "pokeheartgold": ("pret_pokeheartgold", "Johto/Kanto"),
}

DIR_ALIASES = {
    "pret_pokered": "red",
    "pret_pokecrystal": "crys",
    "pret_pokeemerald": "emer",
    "pret_pokefirered": "fire",
    "pret_pokeplatinum": "plat",
    "pret_pokeheartgold": "hgss",
}

SPECIAL_SPECIES = {
    "NIDORAN_M": "nidoran_male",
    "NIDORAN_F": "nidoran_female",
    "MR_MIME": "mr_mime",
    "MR__MIME": "mr_mime",
    "MIME_JR": "mime_jr",
    "FARFETCH_D": "farfetchd",
    "FARFETCHD": "farfetchd",
    "HO_OH": "ho_oh",
    "PORYGON_Z": "porygon_z",
}

SPECIAL_MOVES = {
    "NO_MOVE": "",
    "MOVE_NONE": "",
    "NONE": "",
    "PSYCHIC_M": "psychic",
    "MOVE_PSYCHIC_M": "psychic",
    "SELF_DESTRUCT": "selfdestruct",
    "MOVE_SELF_DESTRUCT": "selfdestruct",
    "ANCIENT_POWER": "ancientpower",
    "MOVE_ANCIENT_POWER": "ancientpower",
    "DYNAMIC_PUNCH": "dynamicpunch",
    "MOVE_DYNAMIC_PUNCH": "dynamicpunch",
    "FAINT_ATTACK": "feintattack",
    "MOVE_FAINT_ATTACK": "feintattack",
    "SMELLING_SALT": "smellingsalts",
    "MOVE_SMELLING_SALT": "smellingsalts",
}


def clean_id(value: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9_.:-]+", "_", value.strip().lower())
    return value.strip("_")


def title_words(value: str) -> str:
    words = [w for w in re.split(r"[_\s]+", value.strip()) if w]
    aliases = {"pkmn": "Pokemon", "rs": "", "m": "Male", "f": "Female", "t": ""}
    result = []
    for word in words:
        low = word.lower()
        mapped = aliases.get(low)
        if mapped is None:
            mapped = word.capitalize()
        if mapped:
            result.append(mapped)
    return " ".join(result) or "Trainer"


def species_slug(raw: str) -> str:
    raw = raw.strip().rstrip(",")
    raw = raw.removeprefix("SPECIES_")
    if raw in SPECIAL_SPECIES:
        return SPECIAL_SPECIES[raw]
    return re.sub(r"_+", "_", raw.lower()).strip("_")


def move_slug(raw: str) -> str:
    raw = raw.strip().rstrip(",")
    if raw in SPECIAL_MOVES:
        return SPECIAL_MOVES[raw]
    raw = raw.removeprefix("MOVE_")
    if raw in SPECIAL_MOVES:
        return SPECIAL_MOVES[raw]
    return raw.lower().replace("_", "")


def clean_message(text: str) -> str:
    text = text.replace("{TRNAME}", "").replace("\\f", " ").replace("\\r", " ")
    text = text.replace("\\n", " ").replace("\n", " ").replace("\r", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def pokemon(species: str, level: int, moves: list[str] | None = None) -> dict:
    return {
        "species": f"cobblemon:{species_slug(species)}",
        "level": max(1, min(100, int(level))),
        "gender": "GENDERLESS",
        "nature": "",
        "ability": "",
        "moveset": [m for m in (move_slug(x) for x in (moves or [])) if m][:4],
        "heldItem": "",
        "shiny": False,
        "aspects": [],
    }


def definition(
    source: str,
    region: str,
    key: str,
    title: str,
    name: str,
    team: list[dict],
    dialogue: list[str] | None = None,
) -> dict | None:
    if not team:
        return None
    min_level = min(mon["level"] for mon in team)
    max_level = max(mon["level"] for mon in team)
    full_name = " ".join(part for part in [title.strip(), name.strip()] if part).strip()
    if not full_name:
        full_name = title or "Trainer"
    return {
        "id": clean_id(f"{source}_{key}"),
        "name": full_name,
        "title": title.strip() or "Trainer",
        "gender": "any",
        "archetype": clean_id(title or "trainer"),
        "region": region,
        "source": source,
        "skinSet": "",
        "minLevel": min_level,
        "maxLevel": max_level,
        "team": team,
        "dialogue": (dialogue or [f"{full_name} is ready for a battle."])[:12],
    }


def write_defs(target: Path, source: str, defs: list[dict]) -> int:
    legacy = target / f"imported_{source}"
    if legacy.exists():
        shutil.rmtree(legacy)
    out = target / f"imp_{DIR_ALIASES.get(source, source)}"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True, exist_ok=True)
    written = 0
    seen: set[str] = set()
    for item in defs:
        if not item or not item.get("team"):
            continue
        item_id = item["id"]
        if item_id in seen:
            digest = hashlib.sha1(json.dumps(item["team"], sort_keys=True).encode()).hexdigest()[:8]
            item_id = f"{item_id}_{digest}"
            item["id"] = item_id
        seen.add(item_id)
        digest = hashlib.sha1(item_id.encode()).hexdigest()[:10]
        file_stem = clean_id(item_id)[:24].strip("_")
        (out / f"{file_stem}_{digest}.json").write_text(json.dumps(item, indent=2), encoding="utf-8")
        written += 1
    return written


def asm_tokens(line: str) -> list[str]:
    line = line.split(";", 1)[0]
    if "db" not in line:
        return []
    line = line.split("db", 1)[1].strip()
    return [tok.strip().rstrip(",") for tok in line.split(",") if tok.strip()]


def int_token(token: str) -> int | None:
    token = token.strip()
    if token == "$FF":
        return 255
    if token in {"-1", "0"}:
        return int(token)
    try:
        return int(token)
    except ValueError:
        return None


def parse_pokered(root: Path) -> list[dict]:
    path = root / "data/trainers/parties.asm"
    if not path.exists():
        return []
    defs: list[dict] = []
    current = ""
    group_index = 0
    last_comment = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.endswith("Data:") and not line.startswith(("Trainer", ";")):
            current = line[:-5]
            group_index = 0
            last_comment = ""
            continue
        if not current:
            continue
        if line.startswith(";"):
            last_comment = line[1:].strip()
            continue
        tokens = asm_tokens(line)
        if not tokens:
            continue
        team: list[dict] = []
        if tokens[0] == "$FF":
            values = tokens[1:]
            for i in range(0, len(values) - 1, 2):
                level = int_token(values[i])
                spec = values[i + 1]
                if level is None or spec == "0":
                    break
                team.append(pokemon(spec, level))
        else:
            level = int_token(tokens[0])
            if level is None:
                continue
            for spec in tokens[1:]:
                if spec == "0":
                    break
                team.append(pokemon(spec, level))
        if not team:
            continue
        group_index += 1
        title = title_words(current)
        name = "" if current.lower() in {"brock", "misty", "lance"} else f"{group_index:03d}"
        key = f"{clean_id(current)}_{group_index:03d}"
        dialogue = [f"{title} from {last_comment or 'Kanto'} is ready for a battle."]
        item = definition("pret_pokered", "Kanto", key, title, name, team, dialogue)
        if item:
            defs.append(item)
    return defs


def parse_pokecrystal(root: Path) -> list[dict]:
    path = root / "data/trainers/parties.asm"
    if not path.exists():
        return []
    defs: list[dict] = []
    group = ""
    comment_title = ""
    active: dict | None = None
    active_type = ""
    index = 0
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.endswith("Group:") and not line.startswith(";"):
            group = line[:-6]
            continue
        if line.startswith(";"):
            match = re.search(r";\s*([A-Z0-9_]+)\s+\((\d+)\)", line)
            if match:
                comment_title = title_words(match.group(1))
                index = int(match.group(2))
            continue
        if line.startswith("db -1"):
            if active:
                title = active["title"]
                name = active["name"]
                key = f"{clean_id(group)}_{index:03d}_{clean_id(name)}"
                item = definition("pret_pokecrystal", "Johto/Kanto", key, title, name.title(), active["team"], active["dialogue"])
                if item:
                    defs.append(item)
            active = None
            active_type = ""
            continue
        if 'db "' in line and "TRAINERTYPE_" in line:
            name = line.split('"', 2)[1].replace("@", "").replace("?", "Rival").strip()
            active_type = re.search(r"TRAINERTYPE_[A-Z_]+", line).group(0)
            title = comment_title or title_words(group)
            active = {"title": title, "name": name, "team": [], "dialogue": [f"{title} {name.title()} is ready for a battle."]}
            continue
        if not active:
            continue
        tokens = asm_tokens(line)
        if len(tokens) < 2:
            continue
        level = int_token(tokens[0])
        if level is None:
            continue
        spec = tokens[1]
        moves: list[str] = []
        if "MOVES" in active_type:
            offset = 3 if "ITEM" in active_type else 2
            moves = tokens[offset : offset + 4]
        active["team"].append(pokemon(spec, level, moves))
    return defs


def parse_c_parties(path: Path) -> dict[str, list[dict]]:
    if not path.exists():
        return {}
    text = path.read_text(encoding="utf-8", errors="ignore")
    parties: dict[str, list[dict]] = {}
    pattern = re.compile(r"static const struct [^{]+ sParty_([A-Za-z0-9_]+)\[\]\s*=\s*\{(.*?)\n\};", re.S)
    for match in pattern.finditer(text):
        name = match.group(1)
        body = match.group(2)
        team = []
        for mon in re.finditer(r"\{(.*?\.species\s*=\s*SPECIES_[A-Z0-9_]+.*?)(?:\n\s*\},|\n\s*\})", body, re.S):
            block = mon.group(1)
            level_match = re.search(r"\.lvl\s*=\s*(\d+)", block)
            species_match = re.search(r"\.species\s*=\s*(SPECIES_[A-Z0-9_]+)", block)
            if not level_match or not species_match:
                continue
            moves_match = re.search(r"\.moves\s*=\s*\{([^}]+)\}", block)
            moves = []
            if moves_match:
                moves = [m.strip() for m in moves_match.group(1).split(",")]
            team.append(pokemon(species_match.group(1), int(level_match.group(1)), moves))
        if team:
            parties[name] = team
    return parties


def parse_gen3(root: Path, source: str, region: str) -> list[dict]:
    parties = parse_c_parties(root / "src/data/trainer_parties.h")
    trainers_path = root / "src/data/trainers.h"
    if not parties or not trainers_path.exists():
        return []
    text = trainers_path.read_text(encoding="utf-8", errors="ignore")
    defs: list[dict] = []
    entry_re = re.compile(r"\[(TRAINER_[A-Z0-9_]+)\]\s*=\s*\{(.*?)\n\s*\},", re.S)
    for match in entry_re.finditer(text):
        trainer_key = match.group(1)
        body = match.group(2)
        party_match = re.search(r"sParty_([A-Za-z0-9_]+)", body)
        if not party_match:
            continue
        party_key = party_match.group(1)
        team = parties.get(party_key, [])
        if not team:
            continue
        class_match = re.search(r"\.trainerClass\s*=\s*(TRAINER_CLASS_[A-Z0-9_]+)", body)
        name_match = re.search(r'\.trainerName\s*=\s*_\("([^"]*)"\)', body)
        title = title_words((class_match.group(1) if class_match else "TRAINER_CLASS_TRAINER").removeprefix("TRAINER_CLASS_"))
        name = (name_match.group(1) if name_match else "").strip()
        if not name:
            stripped = trainer_key.removeprefix("TRAINER_")
            name = title_words(stripped.split("_")[-1] if "_" in stripped else stripped)
        key = clean_id(trainer_key.removeprefix("TRAINER_"))
        item = definition(source, region, key, title, name.title(), team, [f"{title} {name.title()} is ready for a battle."])
        if item:
            defs.append(item)
    return defs


def parse_pokeplatinum(root: Path) -> list[dict]:
    data_dir = root / "res/trainers/data"
    if not data_dir.exists():
        return []
    defs: list[dict] = []
    for path in sorted(data_dir.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        party = data.get("party") or []
        team = [pokemon(mon.get("species", ""), mon.get("level", 1), mon.get("moves") or []) for mon in party if mon.get("species")]
        if not team:
            continue
        title = title_words(str(data.get("class", "TRAINER_CLASS_TRAINER")).removeprefix("TRAINER_CLASS_"))
        name = str(data.get("name", "")).strip().title()
        messages = []
        for msg in data.get("messages") or []:
            if msg.get("type") in {"TRMSG_PRE_BATTLE", "TRMSG_POST_BATTLE"}:
                val = msg.get("en_US", "")
                messages.append(clean_message("".join(val) if isinstance(val, list) else str(val)))
        key = path.stem
        item = definition("pret_pokeplatinum", "Sinnoh", key, title, name, team, [m for m in messages if m])
        if item:
            defs.append(item)
    return defs


def parse_pokeheartgold(root: Path) -> list[dict]:
    path = root / "files/poketool/trainer/trainers.json"
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    defs: list[dict] = []
    for index, trainer in enumerate(data.get("trainers") or []):
        party = trainer.get("party") or []
        team = [pokemon(mon.get("species", ""), mon.get("level", 1), mon.get("moves") or []) for mon in party if mon.get("species")]
        if not team:
            continue
        title = title_words(str(trainer.get("class", "TRAINERCLASS_TRAINER")).removeprefix("TRAINERCLASS_"))
        name = str(trainer.get("name", "")).replace("{TRNAME}", "").strip()
        messages = []
        for msg in trainer.get("messages") or []:
            if msg.get("type") in {"TRMSG_INTRO", "TRMSG_LAST_POKE", "TRMSG_AFTER"}:
                messages.append(clean_message(str(msg.get("message", ""))))
        key_base = f"{index:04d}_{clean_id(title)}_{clean_id(name)}"
        item = definition("pret_pokeheartgold", "Johto/Kanto", key_base, title, name, team, [m for m in messages if m])
        if item:
            defs.append(item)
    return defs


def parse_sources(sources_root: Path) -> dict[str, list[dict]]:
    return {
        "pret_pokered": parse_pokered(sources_root / "pokered"),
        "pret_pokecrystal": parse_pokecrystal(sources_root / "pokecrystal"),
        "pret_pokeemerald": parse_gen3(sources_root / "pokeemerald", "pret_pokeemerald", "Hoenn"),
        "pret_pokefirered": parse_gen3(sources_root / "pokefirered", "pret_pokefirered", "Kanto/Sevii"),
        "pret_pokeplatinum": parse_pokeplatinum(sources_root / "pokeplatinum"),
        "pret_pokeheartgold": parse_pokeheartgold(sources_root / "pokeheartgold"),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources-root", type=Path, default=Path(os.environ.get("TEMP", ".")) / "ckdm-trainer-sources")
    parser.add_argument("--target", type=Path, default=DEFAULT_TARGET)
    args = parser.parse_args()

    args.target.mkdir(parents=True, exist_ok=True)
    parsed = parse_sources(args.sources_root)
    total = 0
    for source, defs in parsed.items():
        count = write_defs(args.target, source, defs)
        total += count
        print(f"{source}: parsed={len(defs)} written={count}")
    print(f"total_written={total}")
    print(f"target={args.target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

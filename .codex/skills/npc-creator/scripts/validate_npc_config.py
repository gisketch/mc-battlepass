#!/usr/bin/env python3
import argparse
import pathlib
import sys
import tomllib


def load(path):
    with pathlib.Path(path).open("rb") as handle:
        return tomllib.load(handle)


def list_ids(root, subdir):
    path = root / subdir
    if not path.exists():
        return set()
    return {item.stem for item in path.glob("*.toml")}


def is_trainer(data):
    npc_id = str(data.get("id", ""))
    title = str(data.get("title", "")).lower()
    return (
        npc_id.startswith(("trainer_", "gym_", "elite_", "johto_", "hoenn_"))
        or "champion" in npc_id
        or any(word in title for word in ("gym", "elite", "champion", "rival"))
    )


def require(errors, condition, message):
    if not condition:
        errors.append(message)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--npc-file", required=True)
    parser.add_argument("--config-root")
    args = parser.parse_args()

    npc_file = pathlib.Path(args.npc_file)
    config_root = pathlib.Path(args.config_root) if args.config_root else npc_file.parent.parent
    data = load(npc_file)
    errors = []
    warnings = []

    npc_id = str(data.get("id", "")).strip()
    require(errors, npc_id, "missing id")
    require(errors, npc_id == npc_file.stem, f"id '{npc_id}' does not match file stem '{npc_file.stem}'")
    for field in ("name", "title", "skin", "body_type", "body_model"):
        require(errors, str(data.get(field, "")).strip(), f"missing {field}")

    personality = data.get("personality", {})
    require(errors, str(personality.get("llm_prompt", "")).strip(), "missing personality.llm_prompt")
    require(errors, personality.get("traits"), "missing personality.traits")
    require(errors, str(personality.get("speech_style", "")).strip(), "missing personality.speech_style")

    schedule = data.get("schedule", {})
    activities = schedule.get("activities", [])
    require(errors, activities, "missing schedule.activities")
    if activities and not any(entry.get("activity") == "sleep" for entry in activities):
        warnings.append("schedule has no sleep activity")

    housing = data.get("housing", {})
    if housing.get("can_move_in", True):
        camper = data.get("camper_messages", {})
        if not camper.get("needs_house_balloon") and not camper.get("needs_house_dialog"):
            warnings.append("move-in NPC has no camper_messages needs_house lines")

    store = str(data.get("store", "")).strip()
    if store:
        store_ids = list_ids(config_root, "stores")
        require(errors, store in store_ids, f"store '{store}' not found under stores/")

    class_id = str(data.get("class", "")).strip()
    if class_id:
        class_ids = list_ids(config_root, "roles/classes")
        if class_ids:
            require(errors, class_id in class_ids, f"class '{class_id}' not found under roles/classes/")
        require(errors, data.get("work_blocks"), "class mentor needs work_blocks for Training")

    gifts = data.get("gifts", {})
    if not is_trainer(data):
        require(errors, gifts.get("loved") or gifts.get("liked"), "resident NPC needs gift loved/liked pools")
        require(errors, data.get("wake_messages"), "resident NPC needs wake_messages")
        require(errors, data.get("hurt_messages"), "resident NPC needs hurt_messages")

    missions = data.get("missions", {})
    unique = data.get("unique_quests", {})
    if missions.get("enabled", True) and not (missions.get("pool") or unique):
        warnings.append("missions enabled but no explicit unique_quests or missions.pool")

    chat = data.get("chat", {})
    require(errors, chat.get("call_names"), "missing chat.call_names")
    voice = data.get("voice", {})
    require(errors, voice.get("animalese_pitch"), "missing voice.animalese_pitch")

    if errors:
        print("NPC config invalid:")
        for error in errors:
            print(f"- {error}")
        if warnings:
            print("Warnings:")
            for warning in warnings:
                print(f"- {warning}")
        raise SystemExit(1)

    print(f"NPC config ok: {npc_id}")
    if warnings:
        print("Warnings:")
        for warning in warnings:
            print(f"- {warning}")


if __name__ == "__main__":
    main()

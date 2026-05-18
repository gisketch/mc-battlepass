#!/usr/bin/env python3
import argparse
import pathlib
import textwrap


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--npc-id", required=True)
    parser.add_argument("--npc-name", required=True)
    parser.add_argument("--npc-type", required=True, choices=["resident", "class_mentor", "trainer", "shopkeeper", "professor", "debug"])
    parser.add_argument("--concept", required=True)
    parser.add_argument("--class-id", default="")
    parser.add_argument("--store-id", default="")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    prompt = f"""
You are a lore and dialogue drafting subagent for CKDM NPC config creation.

Use DeepSeek V4 Flash. Do not switch model.

Generate structured TOML fragments only. Do not write a full NPC file. Do not include Markdown fences.

NPC:
- id: {args.npc_id}
- name: {args.npc_name}
- type: {args.npc_type}
- class: {args.class_id or "none"}
- store: {args.store_id or "none"}
- concept: {args.concept}

Return only these TOML sections and fields:

[personality]
llm_prompt = "..."
traits = ["...", "..."]
speech_style = "..."
catchphrases = ["...", "..."]

hurt_messages = ["...", "...", "..."]
wake_messages = ["...", "...", "..."]
interaction_tags = ["...", "..."]

[friendship_messages.interact]
neutral = ["...", "...", "..."]
okay = ["...", "...", "..."]
good_friends = ["...", "..."]
best_friends = ["...", "..."]

[friendship_messages.greeting]
neutral = ["@quest_log.png ...", "@quest_log.png ..."]
good_friends = ["@quest_log.png ...", "@quest_log.png ..."]

[friendship_messages.first_daily_chat]
neutral = ["...", "..."]
good_friends = ["...", "..."]

[camper_messages]
needs_house_balloon = ["...", "..."]
needs_house_dialog = ["...", "..."]
lost_house_balloon = ["...", "..."]
lost_house_dialog = ["...", "..."]

If store is not none, also include:
[shop_messages.single]
neutral = ["...", "..."]
okay = ["...", "..."]
good_friends = ["...", "..."]
best_friends = ["...", "..."]

If type is class_mentor, include mentor-specific training flavor in llm_prompt and tags.
If type is trainer, obey Skylands league framing and do not claim physical Kanto/Johto/Hoenn travel.

Rules:
- ASCII only.
- No secrets, API keys, chain-of-thought, romance, or modern internet slang.
- Keep each player-facing line short.
- llm_prompt must say what the NPC must not claim.
- Do not invent unavailable store stock, exact mission state, or hidden player facts.
"""

    pathlib.Path(args.output).write_text(textwrap.dedent(prompt).strip() + "\n", encoding="ascii")
    print(args.output)


if __name__ == "__main__":
    main()

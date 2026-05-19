You are a lore and dialogue drafting subagent for CKDM NPC config creation.

Use DeepSeek V4 Flash. Do not switch model.

Generate structured TOML fragments only. Do not write a full NPC file. Do not include Markdown fences.

NPC:
- id: rick_sanchez
- name: Rick Sanchez
- type: resident
- class: none
- store: none
- concept: Rick Sanchez as a future Oritech expert in Chow Kingdom. Plain resident NPC for now, no store, no class, no gameplay unlocks yet. He arrived because CKDM's Oritech-like industrial tech and unstable portal signals interested him. He should be brilliant, abrasive, funny, protective in his own reluctant way, and must not claim current license functionality exists yet.

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

# NPC Conversations

NPC conversation has two channels:

- Local dialog: player is near the NPC and uses the NPC screen.
- World chat: future channel where normal player chat can mention an NPC by trigger name and the NPC replies in chat/Discord.

Both channels should share the same memory and prompt foundation, but they should not share the exact same context shape. Local dialog has physical context. World chat has channel and mention context.

## Local Concurrent Talk

Current behavior:

- Each NPC has at most one active talk session.
- Clicking TALK registers the player into that NPC talk session before they send a message.
- If another player right-clicks that NPC during the session, they do not get the normal dialog. They see only JOIN CONVERSATION and BYE.
- JOIN CONVERSATION adds that player to the same session and opens the text input.
- Every participant can send messages into the same pending turn.
- If the NPC is already waiting for an LLM reply and another participant sends a message, the old LLM response token is marked stale.
- The next LLM request is rebuilt with all pending participant messages and asks for one combined NPC reply to the whole conversation.
- When the reply returns, every still-joined participant gets the same NPC reply. Each participant keeps their own response token, so stale client replies are still ignored correctly.
- Closing the dialog leaves the talk session. If the last participant leaves while the NPC is thinking, the active LLM response is canceled/staled.
- Player logout removes that player from all active NPC talk sessions.

Important implementation files:

- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcLlmService.kt`: owns talk sessions, pending turns, stale token cancellation, group prompt input, and group reply fan-out.
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcFeature.kt`: opens JOIN CONVERSATION/BYE dialog when an NPC already has a talk session.
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcClient.kt`: renders join mode, sends `join_talk`, keeps input open, and leaves with `cancel_llm` on close.
- `src/main/kotlin/dev/gisketch/chowkingdom/npc/NpcNetwork.kt`: carries dialog mode and initial talk-mode flags to the client.

## Prompt Rule

Local concurrent talk should not run one LLM request per player. That creates crossed answers and reply races.

Use one pending-turn queue per NPC talk session:

```text
Players in this conversation: Glenn, Alex
New player messages to answer together:
- Glenn: Are you open today?
- Alex: Also, what do you sell?
Reply once as the NPC to the whole conversation.
```

The normal NPC prompt still includes personality, friendship, world context, store context, recent global events, durable memories, hurt context, and recent history. The group block replaces the single-player message input for that LLM call.

## World Chat

World chat uses the same NPC LLM engine, memory store, and response sanitizer, but it has a separate channel context.

Config field:

```toml
[chat]
enabled = true
call_names = ["finn", "dude"]
minecraft_chat = true
discord_chat = true
```

Use `call_names`, not `trigger_words`, because the field means names or nicknames that can address the NPC.

Trigger rules:

- Minecraft chat triggers when a message mentions `@finn`, `finn,`, `hey finn`, `yo finn`, `hello finn`, `hi finn`, or another configured `call_names` form.
- Discord chat uses the same call names. Linked online Discord users use their Minecraft player context; unlinked users use a Discord guest context.
- Prefer explicit `@name` or name-at-start patterns first. Avoid matching any random word in the middle of a sentence until false positives are understood.
- If multiple NPCs match, pick the strongest exact `@call_name` match. If still ambiguous, do not answer.
- Respect a per-player and per-NPC cooldown separate from local dialog cooldown.
- Each NPC has one active world-chat LLM request. A newer world-chat message to the same NPC marks the older response stale.

World chat context should include:

- Channel: `minecraft_chat` or `discord_chat`.
- Speaker: player name and linked Discord mention if known.
- Addressed NPC id/name and matched call name.
- Recent world chat lines from the whole chat group, bounded and sanitized. This is not only the target player's own history; it can include many players and NPC replies.
- Nearby physical context only if the player is in-game and online.
- Store context if the question mentions stock, shop, price, buy, sell, open, close, schedule, or work.
- Same durable player memories and global memories as local dialog.
- Same recent global events as local dialog.

Implemented behavior:

- Minecraft-origin NPC world-chat replies broadcast to Minecraft chat as `NPC > Player: message`.
- Minecraft-origin replies also relay to Discord through the NPC webhook identity when Discord is enabled.
- Discord-origin replies broadcast to Minecraft chat and also reply in Discord through the NPC webhook identity.
- Discord replies are normal webhook chat messages, not embeds.
- While a world-chat LLM reply is pending, Minecraft players see an NPC world-chat entry like `Finn is thinking...` with the NPC chat head. Discord receives a temporary NPC webhook message that is deleted before the final reply when Discord allows it.
- If the source player has a linked Discord account, the Discord NPC reply mentions that user.
- If the source Discord user is unlinked, Minecraft shows their Discord display name with the Discord chat icon, and Discord receives a normal name-prefixed NPC reply.
- If a Discord user replies to a tracked NPC webhook world-chat message, that reply routes back to the same NPC even without a call name.
- Minecraft world-chat output renders speaker names in bold white and the separator/message in gray.
- Minecraft NPC world-chat output is delivered through a client payload so the local client can add the chat line and draw Chat Heads-style face icons over reserved slots. NPC heads use the NPC skin crop; linked Minecraft players use the same QuickSkin head PNG path as the custom tab HUD, then fall back to the vanilla player skin. Discord guests use the existing Discord chat icon.
- The prompt includes the latest shared world-chat buffer across Minecraft chat, Discord chat, and NPC world-chat replies.
- Store prompts include the NPC store id, active stock key, current visible offers, prices, and stock status. If all active offers are sold out, the prompt still lists the active catalog as out of stock instead of hiding the store inventory.

Discord reply tracking works by sending NPC world-chat webhooks with Discord `wait=true`, capturing the created Discord message id, and keeping a bounded in-memory `message_id -> npc_id` map. Inbound Discord messages with `message_reference.message_id` use that map before falling back to call-name matching.

World chat does not include local dialog-only assumptions:

- Do not assume the player is near the NPC.
- Do not assume the NPC can see the player's held item unless the player is online and nearby enough for that to matter.
- Do not show NPC world balloons for remote chat unless a nearby live NPC should visibly speak too.
- Do not open the NPC dialog screen.

Keep local dialog Discord behavior separate. Local NPC dialog uses embeds and friendship footer display; world chat uses normal webhook content.

## Future Shape

Keep local dialog and world chat as separate entrypoints into a shared prompt service:

- `LocalDialogInput`: close-range NPC screen talk, supports group participants and pending turns.
- `WorldChatInput`: remote chat mention, supports chat channel, matched call name, Discord link, and recent chat context.

Both can produce the same LLM response schema:

```json
{"message":"NPC reply here","memorable":null}
```

Memory writes should stay sparse. Store only facts that matter later, such as player preferences, important promises, deaths, milestones, boss kills, completed missions, and explicit player revelations.
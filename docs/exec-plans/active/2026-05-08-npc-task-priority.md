# NPC Task Priority Notes

Goal: one NPC brain, many player intents.

Priority stack:

1. Critical: attacked, dying, sleeping safety, stuck recovery.
2. Direct interaction: player opened dialog or is talking to NPC.
3. Claimable pending: gift delivery, rent contract, store transaction.
4. Conversation reply: LLM response in progress.
5. Ambient social: plaza meetup, NPC micro interaction, greetings.
6. Routine: work, home, sleep.

Rules:

- NPC can focus one target at a time.
- Same-priority tie-break: active dialog, then closest player, then oldest task.
- Shared conversations merge multiple players into one NPC reply.
- Ambient tasks fill empty time only.
- Higher priority tasks cancel lower priority work cleanly.
- Lower priority tasks wait or expire instead of causing NPC jitter.

Useful future model:

- `NpcTask(ownerPlayerId?, kind, priority, createdAt, expiresAt, interruptPolicy)`
- `NpcFocusState(npcUuid -> focusKind, playerUuid?, untilTick)`
- Debug line: `focus=gift player=Glenn until=123456 queued=2`

Implemented tick order:

1. `Critical`: brain overrides such as hurt response and hazard avoidance.
2. `ContractFollow`: unhoused NPC follows a player holding their rent contract.
3. `NpcInteraction`: NPC-to-NPC short interactions and plaza social behavior.
4. `OutgoingGift`: NPC approaches player for a pending or newly scheduled gift.
5. `Greeting`: ambient nearby-player greeting.
6. `TalkingPause`: NPC keeps current talking state instead of starting routine movement.
7. `Routine`: schedule movement for work, meetup, home, and sleep.

Auto tasks add a short cooldown after they start, so gift, greet, and NPC interaction do not chain immediately into each other.

# NPC LLM Leave Keeps Response

## Goal

Closing NPC dialog or switching to shop/gift/work should leave the UI, not cancel the LLM request. Final NPC reply should still show as a world balloon.

## Acceptance Criteria

- ESC/BYE while an LLM reply is pending does not stale the response token.
- Buy/gift/work actions do not cancel the pending NPC reply.
- Closed-player gets final response as balloon if still nearby.
- Open dialog participants still get normal dialog response.
- True logout cleanup can still cancel.
- Docs updated.
- Build passes.

## Progress Log

- 2026-05-10: Plan created.
- 2026-05-10: Added non-canceling dialog leave path and detached pending-response tracking.
- 2026-05-10: Updated NPC conversation docs.
- 2026-05-10: `./gradlew.bat build --console=plain` passed.
- 2026-05-10: `bash ./scripts/check-sonata.sh` passed.
# NPC LLM Settings Samples

Only the `llm` block inside `settings.json` is active. Copy one of these into `settings.json.llm`.

## DeepSeek

```json
{
  "enabled": true,
  "provider": "openai_compatible",
  "base_url": "https://api.deepseek.com",
  "model": "deepseek-chat",
  "api_key": "PUT_YOUR_DEEPSEEK_KEY_HERE",
  "cooldown_seconds": 4,
  "max_reply_chars": 280,
  "max_recent_turns": 8,
  "request_timeout_seconds": 20,
  "rate_limited_message": "Give me a second to gather my thoughts.",
  "error_message": "Sorry, my thoughts wandered for a second. What were we talking about?",
  "fallback_message": "Sorry, my thoughts wandered for a second. What were we talking about?"
}
```

## Gemini Flash

```json
{
  "enabled": true,
  "provider": "gemini",
  "base_url": "https://generativelanguage.googleapis.com",
  "model": "gemini-2.0-flash",
  "api_key": "PUT_YOUR_GEMINI_KEY_HERE",
  "cooldown_seconds": 4,
  "max_reply_chars": 280,
  "max_recent_turns": 8,
  "request_timeout_seconds": 20,
  "rate_limited_message": "Give me a second to gather my thoughts.",
  "error_message": "Sorry, my thoughts wandered for a second. What were we talking about?",
  "fallback_message": "Sorry, my thoughts wandered for a second. What were we talking about?"
}
```

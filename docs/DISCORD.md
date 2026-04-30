# Discord Integration

Discord integration relays server-side multiplayer events to one Discord webhook.

## Config

Config file:

```text
config/gisketchs_chowkingdom_mod/discord/webhook.json
```

Default config is generated disabled:

```json
{
  "enabled": false,
  "webhook_url": "",
  "webhook_username": "Chow Kingdom",
  "avatar_url": "",
  "player_chat_identity": true,
  "minecraft_avatar_url_template": "https://mc-heads.net/avatar/{uuid}/128",
  "debug_avatar_resolution": true,
  "player_avatar_urls": {},
  "quick_skin_avatar_url_template": "",
  "quick_skin_avatar_server": {
    "enabled": false,
    "bind_host": "0.0.0.0",
    "port": 8765,
    "public_base_url": ""
  },
  "relay_chat": true,
  "relay_join_leave": false,
  "relay_deaths": true,
  "relay_battlepass_completions": true,
  "relay_status": true,
  "status_interval_seconds": 300,
  "discord_to_minecraft": {
    "enabled": false,
    "mode": "gateway",
    "bot_token": "",
    "channel_id": "",
    "poll_interval_seconds": 3,
    "message_format": "\ue100 {author}: {message}",
    "bot_presence_enabled": true,
    "bot_presence_interval_seconds": 60,
    "bot_presence_status": "online",
    "bot_presence_activity_type": "watching",
    "bot_presence_format": "{online}/{max} players - {tps} TPS{season_status}"
  },
  "formatting": {
    "chat_message": "{message}",
    "chat_fallback_message": "**{player}**: {message}",
    "join_title": "Player Joined",
    "join_description": "{player} joined. {online}/{max} players online",
    "join_color": "#57F287",
    "leave_title": "Player Left",
    "leave_description": "{player} left. {online}/{max} players online",
    "leave_color": "#ED4245",
    "death_title": "Player Died",
    "death_description": "{death_message}",
    "death_color": "#ED4245",
    "battlepass_title": "[[{battlepass}]] {scope} Mission Complete",
    "battlepass_description": "{player} completed \"{mission}\" {scope_lower} mission. {player_raw} now has {xp} XP.",
    "battlepass_color": "#FEE75C",
    "status_message": "Server status: {online}/{max} players online | TPS {tps}"
  }
}
```

Set `enabled` to `true` and paste a Discord webhook URL into `webhook_url`.

## Current Behavior

- Player chat relays through the webhook using the player's Minecraft name and avatar URL when `player_chat_identity` is enabled.
- If `player_chat_identity` is disabled, player chat relays as `**PlayerName**: message` through the configured webhook identity.
- When Quick Skin is installed server-side or in an integrated server, Chow Kingdom can read Quick Skin's active `skin_id` and texture bytes for the player's chat avatar.
- If `quick_skin_avatar_server.enabled` and `quick_skin_avatar_server.public_base_url` are set, the mod serves cropped head PNGs at `/quickskin/avatar/<uuid>.png` and uses that public URL as the Discord webhook avatar.
- `quick_skin_avatar_url_template` can override the built-in avatar server URL if you already have an external image/proxy service.
- If Quick Skin is not available or no Quick Skin skin is active, chat falls back to `minecraft_avatar_url_template`.
- `player_avatar_urls` overrides all automatic avatar sources and works on managed servers that cannot expose an HTTP port.
- Discord mentions are disabled through `allowed_mentions` and `@` is zero-width escaped.
- Join, leave, and death relay use Discord embeds with configurable titles, descriptions, and colors.
- Battlepass mission completions use Discord embeds with player avatar author icons.
- Player chat remains normal webhook content so dynamic username/avatar still behaves like chat.
- Server status posts every `status_interval_seconds` with online count and smoothed TPS.
- Join/leave and server status always use the configured `webhook_username` and `avatar_url`, such as `Chow Kingdom`.
- Join/leave relay exists but defaults off; enable `relay_join_leave` if wanted.
- Webhook sends async so chat/tick thread does not wait for Discord.
- Offline-mode servers usually cannot use Mojang skin UUID services correctly, so `minecraft_avatar_url_template` may show Steve. Use Quick Skin server-side plus the built-in avatar server for offline-mode player avatars.

Formatting template tokens:

```text
{player}        markdown-escaped player name
{player_raw}    raw player name
{uuid}          player UUID
{message}       chat message
{death_message} vanilla death message
{battlepass}    battlepass display name
{scope}         mission scope label
{scope_lower}   mission scope label lowercased
{mission}       completed mission title
{xp}            player's total XP in that battlepass after reward
{online}        online player count
{max}           max player count
{tps}           smoothed TPS
{author}        Discord message author for inbound chat
{discord_author} raw Discord display name for inbound chat
{discord_id}     Discord author id for inbound chat
{season}        Serene Seasons season and day, when available
{season_status} Serene Seasons status prefixed with ` - `, or blank
```

Colors use hex strings like `#57F287` or `#ED4245`.

On a dedicated multiplayer server, Quick Skin must be installed in the server's `mods/` folder too. Client-only Quick Skin can render locally for the player, but Chow Kingdom's server-side Discord relay cannot read that client-only texture cache.

Admin diagnostics:

```text
/chowkingdom discord avatar <player>
/chowkingdom discord avatar-server
/chowkingdom discord inbound
/chowkingdom discord link
/chowkingdom discord linked
/chowkingdom discord unlink
/chowkingdom discord unlink <player>
/chowkingdom discord debug-avatar on
/chowkingdom discord debug-avatar off
/chowkingdom discord reload
```

The avatar command prints whether Quick Skin server classes exist, whether a `skin_id` was found, whether texture bytes are available, and which avatar URL Discord will receive.

`/chowkingdom discord reload` reloads `webhook.json` without restarting the server. It also restarts the avatar HTTP server if bind host or port changed, and resets Discord-to-Minecraft polling state.

## Discord to Minecraft

Webhook URLs cannot read Discord messages. For Discord-to-Minecraft chat, create a Discord bot, add it to the server, give it channel read/message history permission, then configure:

Enable `Message Content Intent` for the bot in the Discord Developer Portal, otherwise Discord sends message events without readable text.

```json
"discord_to_minecraft": {
  "enabled": true,
  "mode": "gateway",
  "bot_token": "YOUR_BOT_TOKEN",
  "channel_id": "123456789012345678",
  "poll_interval_seconds": 3,
  "message_format": "\ue100 {author}: {message}",
  "bot_presence_enabled": true,
  "bot_presence_interval_seconds": 60,
  "bot_presence_status": "online",
  "bot_presence_activity_type": "watching",
  "bot_presence_format": "{online}/{max} players - {tps} TPS{season_status}"
}
```

`\ue100` is mapped to `assets/gisketchs_chowkingdom_mod/textures/gui/fonts/discord.png` through the packaged font provider, so it renders as the Discord icon in Minecraft chat.

When `bot_presence_enabled=true`, the bot updates its Discord presence through the Gateway every `bot_presence_interval_seconds`. The default presence renders like `3/20 players - 20.00 TPS - Spring, Day 1` when Serene Seasons is installed, or omits the season part when it is not.

### Account linking

Players can link their Minecraft account to their Discord account so Discord-to-Minecraft chat renders their Minecraft username instead of their Discord display name.

1. In Minecraft, run `/ck discord link`.
2. Copy the generated `CK-######` code.
3. In the bridged Discord channel, send `!link CK-######` within 10 minutes.

After linking, inbound Discord chat uses `{author}` as the linked Minecraft name. Use `{discord_author}` in `message_format` if you want the raw Discord display name instead. Players can check their link with `/ck discord linked` and remove it with `/ck discord unlink`. Operators can remove a player's link with `/ck discord unlink <player>`.

Links are stored in world data at `data/gisketchs_chowkingdom_mod/discord/account_links.json`, not in the general config.

Default `gateway` mode uses Discord Gateway WebSocket events, matching bridge mods such as Mc2Discord. The Minecraft server opens the bot connection on server start and closes it on server stop. It identifies with `GUILDS`, `GUILD_MESSAGES`, and `MESSAGE_CONTENT` intents, so Discord messages appear in Minecraft near-instantly. `polling` mode is kept as fallback; first poll only records the latest message, so old Discord history is not broadcast into Minecraft. Both modes skip bot/webhook messages to avoid echo loops.

After editing config, run:

```text
/chowkingdom discord reload
/chowkingdom discord inbound
```

If `last_channel_check=http 403`, the bot is online but cannot read that channel. Give the bot `View Channel` and `Read Message History` for the configured channel, or reinvite it with proper permissions. If `last_status` never reaches `gateway message received`, check that the bot is invited to the server, can view the channel, and `Message Content Intent` is enabled. If messages arrive but content is blank, Message Content Intent is the usual cause.

## Local Port Check

If `quick_skin_avatar_server.enabled=true`, the mod serves images on the configured port after Minecraft starts.

Run:

```text
/chowkingdom discord avatar <player>
```

Open the printed `local_quick_skin_avatar_url` in a browser on the same machine. If it shows the skin head, the mod side works.

If browser says `missing Quick Skin skin_id`, Quick Skin has not synced an active skin to the server for that player UUID.
If browser says `missing Quick Skin texture bytes`, the server knows the selected skin id but does not have the image bytes cached yet.
If browser says `failed to render Quick Skin texture`, the cached bytes are not a readable Minecraft skin PNG.

For a managed server, ask the host for an extra exposed TCP port like BlueMap or Simple Voice Chat. Set:

```json
"quick_skin_avatar_server": {
  "enabled": true,
  "bind_host": "0.0.0.0",
  "port": 8765,
  "public_base_url": "http://server-ip-or-domain:8765"
}
```

If host exposes port `12020` on server IP `169.150.243.8`, config becomes:

```json
"quick_skin_avatar_server": {
  "enabled": true,
  "bind_host": "0.0.0.0",
  "port": 12020,
  "public_base_url": "http://169.150.243.8:12020"
}
```

The mod will send Discord per-player avatar URLs like:

```text
http://169.150.243.8:12020/quickskin/avatar/<uuid>.png?skin=<skin_id>
```

Use `https://...` if the host gives reverse proxy/domain support. Plain `http://public-ip:port` can be enough for basic testing if Discord accepts the image URL. Include the scheme; `localhost`, `127.0.0.1`, and `0.0.0.0` are local-only and invalid for Discord.

## Managed Server Setup

Use this when host does not allow extra ports, shell access, Cloudflare Tunnel, Caddy, Node.js, or sidecar apps.

Upload each player skin head image to any stable public image host, then map player name or UUID:

```json
"player_avatar_urls": {
  "Dev": "https://cdn.example.com/dev-head.png",
  "380df991-f603-344c-a090-369bad2a924a": "https://cdn.example.com/dev-head.png"
}
```

Keys can be player name, dashed UUID, or undashed UUID. Values must be public `http` or `https` image URLs Discord can fetch.

For managed hosts, this is easiest and most reliable. Quick Skin still changes in-game skin, but Discord avatar comes from this config map.

## Easiest Local Setup: Cloudflare Tunnel

Use this for singleplayer or local-hosted multiplayer. No VPS needed.

1. Install `cloudflared` from Cloudflare.
2. Enable the built-in avatar server:

```json
"quick_skin_avatar_server": {
  "enabled": true,
  "bind_host": "127.0.0.1",
  "port": 8765,
  "public_base_url": ""
}
```

3. Start Minecraft, then run:

```text
cloudflared tunnel --url http://127.0.0.1:8765
```

4. Copy the printed `https://*.trycloudflare.com` URL into `public_base_url`.
5. Restart Minecraft.
6. Run `/chowkingdom discord avatar-server` and `/chowkingdom discord avatar <player>`.

`public_base_url` must be a real public URL. Placeholder values like `https://your-ngrok-or-cloudflare-url` are ignored and the mod falls back to `minecraft_avatar_url_template`.

## VPS Setup

Use VPS only if Minecraft server runs there 24/7.

Recommended minimal structure:

```text
/opt/chowkingdom/
  server/
    mods/
      chowkingdom.jar
      quick-skin.jar
      architectury.jar
    config/gisketchs_chowkingdom_mod/discord/webhook.json
    run.sh
  caddy/
    Caddyfile
```

Caddy proxy:

```text
avatars.example.com {
  reverse_proxy 127.0.0.1:8765
}
```

Then set:

```json
"public_base_url": "https://avatars.example.com"
```

## Notes

- Keep `webhook_url` secret; anyone with the URL can post to that channel.
- Keep `discord_to_minecraft.bot_token` secret; anyone with the token can control that bot.
- Discord webhook avatars must be public `http` or `https` URLs. If using the built-in Quick Skin avatar server, expose its port through a reverse proxy or port forward, then set `public_base_url` to the public address.
- URL templates support `{uuid}`, `{uuid_no_dash}`, `{name}`, and `{skin_id}`.
- Discord-to-Minecraft chat requires a bot token and channel id; webhook URL alone is send-only.
- TPS is estimated from server tick interval and clamped to 20 TPS.

Example Quick Skin avatar server config:

```json
{
  "enabled": true,
  "bind_host": "0.0.0.0",
  "port": 8765,
  "public_base_url": "https://mc.example.com"
}
```

With that setup, Discord receives player chat avatar URLs like:

```text
https://mc.example.com/quickskin/avatar/00000000-0000-0000-0000-000000000000.png?skin=<quick_skin_skin_id>
```
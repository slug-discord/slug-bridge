# Bridge protocol

The plugin opens an outbound WebSocket to the bot at the URL in `config.yml`
and authenticates with an `Authorization: Bearer <token>` header on the
upgrade request. Anything without a valid token gets closed with WebSocket
code 1008 by the bot.

All frames are UTF-8 JSON objects with a top-level `type` field.

## Plugin to bot events

```json
{"type": "chat",  "player": "Notch", "uuid": "...", "message": "hi"}
{"type": "join",  "player": "Notch", "uuid": "..."}
{"type": "leave", "player": "Notch", "uuid": "..."}
{"type": "death", "player": "Notch", "uuid": "...", "message": "Notch fell from a high place"}
{"type": "console", "line": "[Server thread/INFO]: ..."}
{"type": "heartbeat"}
{"type": "cmd_result", "cmd": "whitelist list", "output": "There are 3 whitelisted players: ..."}
```

### Limits

- Frames larger than 8192 bytes are dropped by the bot.
- Events are rate-limited to 30 per second per connected server. Excess
  frames are silently dropped.
- Any `type` value not in the list above is rejected at the gateway.

## Bot to plugin commands

```json
{"type": "execute", "cmd": "whitelist add Notch"}
{"type": "chat",    "author": "someone-in-discord", "message": "hello from discord"}
```

The plugin runs `execute` commands as the console on the main server thread.
`chat` commands are broadcast in-game as
`§9[§bDiscord§9] §7<author>§r: <message>`.

## Disconnect / reconnect

- The bot sends no periodic pings; idle connections stay up.
- If the plugin WS closes (or the bot restarts), the plugin reconnects with
  exponential backoff (initial doubles up to max on each failure). Set
  `reconnect.initial` to 0 to disable auto-reconnect.

## Writing your own bot

The plugin is bot-agnostic. To drive it from another service:

1. Run a WebSocket server that accepts the upgrade at any path (the plugin
   uses whatever URL you put in `config.yml`).
2. On accept, check the `Authorization: Bearer <token>` header against your
   own issued token list. If invalid, close with policy violation (1008).
3. Read JSON frames, only honor the types listed above. Drop oversized
   frames and rate-limit per-connection.
4. To send in-game chat or run commands, push a JSON frame matching the
   "bot to plugin commands" shape.

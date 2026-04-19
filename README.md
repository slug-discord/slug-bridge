# slug-bridge

A Paper/Spigot plugin that relays server chat, joins and deaths to a Discord
bot over a WebSocket, and runs console commands sent back by the bot. The
plugin only speaks a small JSON protocol (see `PROTOCOL.md`), so any bot that
implements the same contract can drive it.

## Install

1. Download `slug-bridge.jar` from the latest release.
2. Drop it into `plugins/`.
3. Start the server once. You'll get a warning about the missing token and
   a generated `plugins/SlugBridge/config.yml`.
4. Paste the url and token from your bot into `config.yml`.
5. Restart, or run `/reload confirm`. Server log shows `Bridge connected.`
   when it's up.

## Build

Needs JDK 17. Either run `gradle shadowJar` locally, or in Docker:

```
docker run --rm -v "$PWD":/app -w /app gradle:8.5-jdk17 gradle shadowJar --no-daemon
```

Output ends up at `build/libs/slug-bridge.jar`.

## Security

- The token is the only thing authorising this server. Treat it like a
  password. Don't commit `config.yml`. Rotate it if it leaks.
- `execute` messages from the bot run as console, so whoever has admin on
  the bot can run any server command through the bridge.
- Only outbound WS is opened. Nothing is exposed inbound, so this works on
  pterodactyl and other hosted panels without port-forwards.
- The bot side drops frames over 8 KB and rate-limits at 30 events/sec per
  server.

## Config

```yaml
url: "ws://bot.example.com/mc/bridge"
token: "your-token"

events:
  chat: true
  joins: true
  deaths: true
  console: false

reconnect:
  initial: 2
  max: 60
```

## License

MIT.

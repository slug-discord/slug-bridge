package bot.slug.bridge;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

public final class BridgePlugin extends JavaPlugin implements Listener {

    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final StringBuilder buffer = new StringBuilder();
    private HttpClient http;
    private String url;
    private String token;
    private boolean chatEnabled;
    private boolean joinsEnabled;
    private boolean deathsEnabled;
    private int reconnectInitial;
    private int reconnectMax;
    private int reconnectCurrent;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        if (token == null || token.isBlank()) {
            getLogger().warning("Bridge token not set in plugins/SlugBridge/config.yml, plugin idle.");
            return;
        }

        http = HttpClient.newHttpClient();
        getServer().getPluginManager().registerEvents(this, this);
        connect();
    }

    @Override
    public void onDisable() {
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "plugin disable");
            } catch (Exception ignored) {
            }
        }
    }

    private void reloadSettings() {
        this.url = getConfig().getString("url", "");
        this.token = getConfig().getString("token", "");
        this.chatEnabled = getConfig().getBoolean("events.chat", true);
        this.joinsEnabled = getConfig().getBoolean("events.joins", true);
        this.deathsEnabled = getConfig().getBoolean("events.deaths", true);
        this.reconnectInitial = getConfig().getInt("reconnect.initial", 2);
        this.reconnectMax = getConfig().getInt("reconnect.max", 60);
        this.reconnectCurrent = this.reconnectInitial;
    }

    private void connect() {
        if (url == null || url.isBlank()) return;
        getLogger().info("Connecting to bridge " + url);
        http.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(URI.create(url), new Handler())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        getLogger().warning("Bridge connect failed: " + err.getMessage());
                        scheduleReconnect();
                    } else {
                        socket.set(ws);
                        reconnectCurrent = reconnectInitial;
                        getLogger().info("Bridge connected.");
                    }
                });
    }

    private void scheduleReconnect() {
        if (reconnectInitial <= 0) return;
        int delay = reconnectCurrent;
        reconnectCurrent = Math.min(reconnectMax, reconnectCurrent * 2);
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::connect, delay * 20L);
    }

    private void send(Map<String, ?> payload) {
        WebSocket ws = socket.get();
        if (ws == null) return;
        String json = JsonWriter.write(payload);
        try {
            ws.sendText(json, true);
        } catch (Exception e) {
            getLogger().warning("Send failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!chatEnabled) return;
        send(Map.of(
                "type", "chat",
                "player", e.getPlayer().getName(),
                "uuid", e.getPlayer().getUniqueId().toString(),
                "message", e.getMessage()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!joinsEnabled) return;
        send(Map.of(
                "type", "join",
                "player", e.getPlayer().getName(),
                "uuid", e.getPlayer().getUniqueId().toString()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!joinsEnabled) return;
        send(Map.of(
                "type", "leave",
                "player", e.getPlayer().getName(),
                "uuid", e.getPlayer().getUniqueId().toString()
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!deathsEnabled) return;
        send(Map.of(
                "type", "death",
                "player", e.getEntity().getName(),
                "uuid", e.getEntity().getUniqueId().toString(),
                "message", e.getDeathMessage() == null ? "" : e.getDeathMessage()
        ));
    }

    private final class Handler implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                handle(payload);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket ws, ByteBuffer data) {
            ws.request(1);
            return ws.sendPong(data);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            socket.compareAndSet(ws, null);
            getLogger().info("Bridge closed (" + code + "): " + reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable err) {
            socket.compareAndSet(ws, null);
            getLogger().warning("Bridge error: " + err.getMessage());
            scheduleReconnect();
        }
    }

    private void handle(String payload) {
        Map<String, String> msg = JsonReader.readFlat(payload);
        String type = msg.getOrDefault("type", "");
        if ("execute".equals(type)) {
            String cmd = msg.getOrDefault("cmd", "");
            if (cmd.isBlank()) return;
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            });
        } else if ("chat".equals(type)) {
            String author = msg.getOrDefault("author", "discord");
            String message = msg.getOrDefault("message", "");
            if (message.isBlank()) return;
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.broadcastMessage("§9[§bDiscord§9] §7" + author + "§r: " + message);
            });
        }
    }
}

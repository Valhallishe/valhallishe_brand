package net.valhallishe.brand;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

@Plugin(
        id = "valhallishebrand",
        name = "ValhallisheBrand",
        version = "1.1.0",
        description = "Per-server F3 brand and restart notice for the Valhallishe network",
        authors = {"Valhallishe"})
public final class ValhallisheBrand {

    private static final MinecraftChannelIdentifier BRAND =
            MinecraftChannelIdentifier.from("minecraft:brand");

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private Properties config = new Properties();

    @Inject
    public ValhallisheBrand(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        final Player player = event.getPlayer();
        final String authBrand = config.getProperty("auth", "Valhallishe Auth");
        // The player is held in the in-memory LimboAuth limbo, which is not a
        // registered backend server, so no ServerPostConnectEvent is fired for it.
        // Set the brand while the authentication is in progress.
        for (long delay : new long[] {1, 2, 4}) {
            server.getScheduler().buildTask(this, () -> {
                if (player.getCurrentServer().isEmpty()) {
                    sendBrand(player, authBrand);
                }
            }).delay(delay, TimeUnit.SECONDS).schedule();
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        final Player player = event.getPlayer();
        final String serverName = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        final String previousName = event.getPreviousServer() == null
                ? null
                : event.getPreviousServer().getServerInfo().getName();

        final String brand = brandFor(serverName);
        sendBrand(player, brand);
        server.getScheduler().buildTask(this, () -> sendBrand(player, brand))
                .delay(1, TimeUnit.SECONDS)
                .schedule();

        // A player is dropped into the limbo only after having been on a real server
        // when that server goes down (i.e. it is being restarted).
        if (isLimbo(serverName) && previousName != null) {
            showRestartTitle(player);
            server.getScheduler().buildTask(this, () -> showRestartTitle(player))
                    .delay(1, TimeUnit.SECONDS)
                    .schedule();
        } else if (!isLimbo(serverName) && isLimbo(previousName)) {
            player.clearTitle();
        }
    }

    private boolean isLimbo(String serverName) {
        return serverName != null && serverName.equalsIgnoreCase("limbo");
    }

    private String brandFor(String serverName) {
        if (serverName == null) {
            return config.getProperty("default", "Valhallishe Survival");
        }
        return config.getProperty(
                serverName.toLowerCase(),
                config.getProperty("default", "Valhallishe Survival"));
    }

    private void sendBrand(Player player, String brand) {
        if (!player.isActive()) {
            return;
        }
        try {
            player.sendPluginMessage(BRAND, encode(brand));
        } catch (Exception exception) {
            logger.warn("Failed to send brand '{}' to {}", brand, player.getUsername(), exception);
        }
    }

    private void showRestartTitle(Player player) {
        if (!player.isActive() || !Boolean.parseBoolean(config.getProperty("restart-title-enabled", "true"))) {
            return;
        }
        try {
            final MiniMessage mini = MiniMessage.miniMessage();
            final Component main = mini.deserialize(
                    config.getProperty("restart-title-main", "<red><bold>СЕРВЕР ПЕРЕЗАГРУЖАЕТСЯ"));
            final Component subtitle = mini.deserialize(
                    config.getProperty("restart-title-sub", "<gray>Пожалуйста, подождите..."));
            final long staySeconds = Long.parseLong(config.getProperty("restart-title-seconds", "3600"));
            final Title title = Title.title(
                    main,
                    subtitle,
                    Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofSeconds(staySeconds),
                            Duration.ofMillis(1000)));
            player.showTitle(title);
        } catch (Exception exception) {
            logger.warn("Failed to show restart title to {}", player.getUsername(), exception);
        }
    }

    private static byte[] encode(String value) {
        final byte[] utf = value.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = utf.length;
        while ((length & ~0x7F) != 0) {
            out.write((length & 0x7F) | 0x80);
            length >>>= 7;
        }
        out.write(length);
        out.write(utf, 0, utf.length);
        return out.toByteArray();
    }

    private void loadConfig() {
        final Properties defaults = new Properties();
        defaults.setProperty("default", "Valhallishe Survival");
        defaults.setProperty("auth", "Valhallishe Auth");
        defaults.setProperty("survival", "Valhallishe Survival");
        defaults.setProperty("limbo", "Valhallishe Limbo");
        defaults.setProperty("creative", "Valhallishe Survival");
        defaults.setProperty("restart-title-enabled", "true");
        defaults.setProperty("restart-title-main", "<red><bold>СЕРВЕР ПЕРЕЗАГРУЖАЕТСЯ");
        defaults.setProperty("restart-title-sub", "<gray>Пожалуйста, подождите...");
        defaults.setProperty("restart-title-seconds", "3600");
        try {
            Files.createDirectories(dataDirectory);
            final Path file = dataDirectory.resolve("config.properties");
            final Properties loaded = new Properties();
            if (Files.exists(file)) {
                try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    loaded.load(reader);
                }
            }
            boolean changed = false;
            for (String key : defaults.stringPropertyNames()) {
                if (!loaded.containsKey(key)) {
                    loaded.setProperty(key, defaults.getProperty(key));
                    changed = true;
                }
            }
            if (changed || !Files.exists(file)) {
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    loaded.store(writer, "ValhallisheBrand - F3 brand per server + restart notice");
                }
            }
            final Properties merged = new Properties();
            merged.putAll(defaults);
            merged.putAll(loaded);
            this.config = merged;
        } catch (IOException exception) {
            logger.warn("Could not load config, using defaults", exception);
            this.config = defaults;
        }
    }
}

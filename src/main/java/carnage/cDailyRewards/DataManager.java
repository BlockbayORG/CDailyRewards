package carnage.cDailyRewards;

import carnage.cDailyRewards.storage.PlayerData;
import carnage.cDailyRewards.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Caches data of online players. All storage IO runs on one dedicated thread, so SQLite and file writes never race.
 */
public final class DataManager implements Listener {

    private final CDailyRewards plugin;
    private final Storage storage;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CDailyRewards-IO");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(CDailyRewards plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void runIo(Runnable task) {
        io.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Storage task failed", e);
            }
        });
    }

    /** Online player's data, or null if it has not loaded (should not happen after join). */
    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    public void save(PlayerData data) {
        PlayerData snapshot = data.copy();
        runIo(() -> {
            try {
                storage.save(snapshot);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not save " + snapshot.uuid(), e);
            }
        });
    }

    /**
     * Runs {@code action} on the main thread with the player's data (online or offline), then saves it.
     */
    public void edit(UUID uuid, Consumer<PlayerData> action) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            action.accept(cached);
            save(cached);
            return;
        }
        runIo(() -> {
            PlayerData loaded;
            try {
                loaded = storage.load(uuid);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load " + uuid, e);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Player may have joined meanwhile; prefer the live copy.
                PlayerData target = cache.getOrDefault(uuid, loaded);
                action.accept(target);
                save(target);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        try {
            PlayerData data = io.submit(() -> storage.load(uuid)).get(10, TimeUnit.SECONDS);
            cache.put(uuid, data);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not load data for " + uuid, e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        // Fallback if pre-login loading failed or the player was cached out by a reload.
        UUID uuid = event.getPlayer().getUniqueId();
        if (!cache.containsKey(uuid)) {
            cache.put(uuid, new PlayerData(uuid));
            runIo(() -> {
                try {
                    PlayerData loaded = storage.load(uuid);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (Bukkit.getPlayer(uuid) != null) cache.put(uuid, loaded);
                    });
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Could not load data for " + uuid, e);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = cache.remove(event.getPlayer().getUniqueId());
        if (data != null) save(data);
    }

    /** Loads data for players already online (plugin enabled mid-game). */
    public void loadOnline() {
        Bukkit.getOnlinePlayers().forEach(p -> {
            UUID uuid = p.getUniqueId();
            try {
                cache.put(uuid, io.submit(() -> storage.load(uuid)).get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not load data for " + uuid, e);
                cache.put(uuid, new PlayerData(uuid));
            }
        });
    }

    public void shutdown() {
        cache.values().forEach(this::save);
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) plugin.getLogger().warning("Storage did not finish saving in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        storage.close();
    }
}

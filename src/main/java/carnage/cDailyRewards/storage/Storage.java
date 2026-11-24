package carnage.cDailyRewards.storage;

import java.util.UUID;

/**
 * Blocking storage. Always called from the plugin's storage thread, never the
 * main thread.
 */
public interface Storage {

    PlayerData load(UUID uuid) throws Exception;

    void save(PlayerData data) throws Exception;

    void close();
}

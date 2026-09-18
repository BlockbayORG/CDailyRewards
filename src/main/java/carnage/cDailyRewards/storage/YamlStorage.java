package carnage.cDailyRewards.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;

public final class YamlStorage implements Storage {

    private final File folder;

    public YamlStorage(File dataFolder) {
        this.folder = new File(dataFolder, "data/players");
        folder.mkdirs();
    }

    @Override
    public PlayerData load(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) return data;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        data.day(Math.max(1, yml.getInt("day", 1)));
        data.streak(yml.getInt("streak"));
        data.totalClaims(yml.getInt("total-claims"));
        data.lastClaim(yml.getLong("last-claim"));
        data.playtimeAtClaim(yml.getLong("playtime-at-claim"));
        data.cooldownSkipped(yml.getBoolean("cooldown-skipped"));
        data.finished(yml.getBoolean("finished"));
        return data;
    }

    @Override
    public void save(PlayerData data) throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("day", data.day());
        yml.set("streak", data.streak());
        yml.set("total-claims", data.totalClaims());
        yml.set("last-claim", data.lastClaim());
        yml.set("playtime-at-claim", data.playtimeAtClaim());
        yml.set("cooldown-skipped", data.cooldownSkipped());
        yml.set("finished", data.finished());
        yml.save(new File(folder, data.uuid() + ".yml"));
    }

    @Override
    public void close() {
    }
}

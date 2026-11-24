package carnage.cDailyRewards.reward;

import carnage.cDailyRewards.util.ItemParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class RewardRegistry {

    private final Map<Integer, RewardDay> days = new HashMap<>();

    public void load(FileConfiguration cfg, int totalDays, Logger log) {
        days.clear();
        ConfigurationSection root = cfg.getConfigurationSection("days");
        if (root == null) {
            log.warning("rewards.yml has no 'days' section");
            return;
        }
        for (String key : root.getKeys(false)) {
            int day;
            try {
                day = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                log.warning("rewards.yml: '" + key + "' is not a day number");
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null)
                continue;
            List<Reward> rewards = RewardTypes.parseList(sec.getMapList("rewards"), "days." + key + ".rewards", log);
            days.put(day, new RewardDay(day, sec.getBoolean("milestone", false), rewards,
                    ItemParser.of(sec.getConfigurationSection("icon"), log), sec.getString("broadcast")));
        }
        for (int d = 1; d <= totalDays; d++) {
            if (!days.containsKey(d))
                log.warning("rewards.yml: day " + d + " has no rewards");
        }
    }

    public RewardDay get(int day) {
        return days.getOrDefault(day, new RewardDay(day, false, List.of(), null, null));
    }
}

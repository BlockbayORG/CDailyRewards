package carnage.cDailyRewards.menu;

import carnage.cDailyRewards.util.ItemParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Parsed menus/main.yml. */
public final class MenuConfig {

    public record Button(ItemParser item, List<String> actions) {
    }

    public final String title;
    public final int rows;
    public final int updateInterval;
    public final String openSound;
    /** Slot of every layout char, row-major. */
    public final char[] layout;
    public final List<Integer> daySlots = new ArrayList<>();
    public final Map<Character, Button> buttons = new HashMap<>();
    public final Map<String, ItemParser> dayStates = new HashMap<>();
    public final Map<String, ItemParser> milestoneStates = new HashMap<>();

    public MenuConfig(FileConfiguration cfg, Logger log) {
        this.title = cfg.getString("title", "Daily Rewards");
        this.updateInterval = Math.max(0, cfg.getInt("update-interval", 20));
        this.openSound = cfg.getString("open-sound", "");

        List<String> rowsList = cfg.getStringList("layout");
        if (rowsList.isEmpty() || rowsList.size() > 6) {
            log.warning("menus/main.yml: layout needs 1-6 rows, using default");
            rowsList = List.of("#########", "#DDDDDDD#", "#DDDDDDD#", "#DDDDDDD#", "#DDDDDDD#", "#########");
        }
        this.rows = rowsList.size();
        this.layout = new char[rows * 9];
        for (int r = 0; r < rows; r++) {
            String row = rowsList.get(r);
            for (int c = 0; c < 9; c++) {
                char ch = c < row.length() ? row.charAt(c) : ' ';
                layout[r * 9 + c] = ch;
                if (ch == 'D')
                    daySlots.add(r * 9 + c);
            }
        }
        if (daySlots.isEmpty())
            log.warning("menus/main.yml: layout has no 'D' day slots");

        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                if (key.length() != 1) {
                    log.warning("menus/main.yml: item key '" + key + "' must be one character");
                    continue;
                }
                ConfigurationSection sec = items.getConfigurationSection(key);
                buttons.put(key.charAt(0), new Button(ItemParser.of(sec, log), sec.getStringList("actions")));
            }
        }
        loadStates(cfg.getConfigurationSection("day-states"), dayStates, log);
        loadStates(cfg.getConfigurationSection("milestone-states"), milestoneStates, log);
    }

    private static void loadStates(ConfigurationSection sec, Map<String, ItemParser> into, Logger log) {
        if (sec == null)
            return;
        for (String state : sec.getKeys(false))
            into.put(state, ItemParser.of(sec.getConfigurationSection(state), log));
    }
}

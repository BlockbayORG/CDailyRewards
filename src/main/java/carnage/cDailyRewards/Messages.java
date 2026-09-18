package carnage.cDailyRewards;

import carnage.cDailyRewards.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class Messages {

    private final FileConfiguration cfg;
    private final String prefix;

    public Messages(FileConfiguration cfg) {
        this.cfg = cfg;
        this.prefix = cfg.getString("prefix", "");
    }

    public void send(CommandSender to, String key, Map<String, String> vars) {
        Player p = to instanceof Player pl ? pl : null;
        if (cfg.isList(key)) {
            List<String> lines = cfg.getStringList(key);
            for (String line : lines) to.sendMessage(Text.parse(Text.replace(line, vars), p));
            return;
        }
        String msg = cfg.getString(key);
        if (msg == null || msg.isEmpty()) return;
        to.sendMessage(Text.parse(prefix + Text.replace(msg, vars), p));
    }

    public String raw(String key) {
        return cfg.getString(key, "");
    }
}

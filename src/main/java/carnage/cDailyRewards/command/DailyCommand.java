package carnage.cDailyRewards.command;

import carnage.cDailyRewards.CDailyRewards;
import carnage.cDailyRewards.menu.DailyMenu;
import carnage.cDailyRewards.storage.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** /daily [claim|preview] */
public final class DailyCommand implements TabExecutor {

    private final CDailyRewards plugin;

    public DailyCommand(CDailyRewards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only", Map.of());
            return true;
        }
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null)
            return true;
        plugin.claims().checkMissed(player, d);

        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "claim" -> {
                if (!check(player, "cdr.claim"))
                    return true;
                plugin.claims().claim(player);
            }
            case "preview" -> {
                if (!check(player, "cdr.preview"))
                    return true;
                new DailyMenu(plugin, player, true).open();
            }
            default -> {
                if (!check(player, "cdr.use"))
                    return true;
                new DailyMenu(plugin, player, false).open();
            }
        }
        return true;
    }

    private boolean check(Player player, String perm) {
        if (player.hasPermission(perm))
            return true;
        plugin.messages().send(player, "no-permission", Map.of());
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1)
            return List.of("claim", "preview").stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        return List.of();
    }
}

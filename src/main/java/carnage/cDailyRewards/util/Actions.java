package carnage.cDailyRewards.util;

import carnage.cDailyRewards.reward.RewardTypes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Runs "[type] argument" action lines, shared by vouchers and menu buttons.
 * Unknown types are passed to {@code custom} (e.g. [close], [page] for menus).
 */
public final class Actions {

    private Actions() {
    }

    public static void run(Player player, List<String> actions, UnaryOperator<String> vars, Consumer<String[]> custom) {
        for (String raw : actions) {
            String line = vars.apply(raw).trim();
            if (!line.startsWith("[")) continue;
            int end = line.indexOf(']');
            if (end < 0) continue;
            String type = line.substring(1, end).toLowerCase(Locale.ROOT);
            String arg = line.substring(end + 1).trim();
            switch (type) {
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(arg));
                case "player", "command" -> player.performCommand(strip(arg));
                case "message" -> player.sendMessage(Text.parse(arg, player));
                case "broadcast" -> Bukkit.broadcast(Text.parse(arg, player));
                case "sound" -> {
                    String[] p = arg.split(" ");
                    float vol = p.length > 1 ? parseFloat(p[1]) : 1f;
                    float pitch = p.length > 2 ? parseFloat(p[2]) : 1f;
                    RewardTypes.playSound(player, p[0], vol, pitch);
                }
                default -> {
                    if (custom != null) custom.accept(new String[]{type, arg});
                }
            }
        }
    }

    private static String strip(String cmd) {
        return cmd.startsWith("/") ? cmd.substring(1) : cmd;
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 1f;
        }
    }
}

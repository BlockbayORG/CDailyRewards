package carnage.cDailyRewards.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static boolean papi;

    private Text() {
    }

    public static void init() {
        papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String replace(String s, Map<String, String> vars) {
        if (s == null) return "";
        for (Map.Entry<String, String> e : vars.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return s;
    }

    public static String papi(String s, OfflinePlayer player) {
        if (papi && player != null && s.indexOf('%') >= 0) {
            return PlaceholderAPI.setPlaceholders(player, s);
        }
        return s;
    }

    /** Parses MiniMessage, or legacy & codes when the string has no MiniMessage tags. */
    public static Component parse(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        if ((s.indexOf('&') >= 0 || s.indexOf('§') >= 0) && s.indexOf('<') < 0) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(s.replace('§', '&'));
        }
        return MM.deserialize(s);
    }

    public static Component parse(String s, Player player) {
        return parse(papi(s, player));
    }

    /** Item names and lore: no default italics. */
    public static Component item(String s, Player player) {
        return parse(s, player).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) return "now";
        long secs = millis / 1000;
        long d = secs / 86400, h = (secs % 86400) / 3600, m = (secs % 3600) / 60, s = secs % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0 || d > 0) sb.append(h).append("h ");
        if (m > 0 || h > 0 || d > 0) sb.append(m).append("m ");
        if (d == 0) sb.append(s).append('s');
        return sb.toString().trim();
    }
}

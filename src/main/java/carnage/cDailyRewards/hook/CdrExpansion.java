package carnage.cDailyRewards.hook;

import carnage.cDailyRewards.CDailyRewards;
import carnage.cDailyRewards.ClaimService;
import carnage.cDailyRewards.storage.PlayerData;
import carnage.cDailyRewards.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * %cdr_day% %cdr_streak% %cdr_total_claims% %cdr_time_left% %cdr_can_claim%
 * %cdr_days%
 */
public final class CdrExpansion extends PlaceholderExpansion {

    private final CDailyRewards plugin;

    public CdrExpansion(CDailyRewards plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "cdr";
    }

    @Override
    public String getAuthor() {
        return "carnage";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equals("days"))
            return String.valueOf(plugin.settings().days);
        if (player == null)
            return "";
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null)
            return "";
        return switch (params) {
            case "day" -> String.valueOf(d.day());
            case "streak" -> String.valueOf(d.streak());
            case "total_claims", "total" -> String.valueOf(d.totalClaims());
            case "time_left" -> Text.formatDuration(plugin.claims().timeLeft(d));
            case "can_claim" -> String.valueOf(plugin.claims().state(d) == ClaimService.State.AVAILABLE);
            default -> null;
        };
    }
}

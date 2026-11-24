package carnage.cDailyRewards.reward;

import org.bukkit.configuration.ConfigurationSection;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One reward entry of a day. Subclasses do the actual giving; this class
 * handles the shared options.
 */
public abstract class Reward {

    private final double chance;
    private final String permission;
    private final String displayName;
    private final boolean display;
    protected final boolean multiply;

    protected Reward(ConfigurationSection sec) {
        this.chance = sec.getDouble("chance", 100);
        this.permission = sec.getString("permission");
        this.displayName = sec.getString("display-name");
        this.display = sec.getBoolean("display", true);
        this.multiply = sec.getBoolean("multiply", true);
    }

    /** Rolls chance and permission, then gives. */
    public final void tryGive(RewardContext ctx) {
        if (permission != null && !permission.isEmpty() && !ctx.player().hasPermission(permission))
            return;
        if (chance < 100 && ThreadLocalRandom.current().nextDouble(100) >= chance)
            return;
        give(ctx);
    }

    protected abstract void give(RewardContext ctx);

    /** Auto-generated menu line when no display-name is set. */
    protected abstract String defaultDisplay();

    /** Menu lore line, or null when hidden. */
    public String displayLine() {
        if (!display)
            return null;
        String line = displayName != null ? displayName : defaultDisplay();
        if (line == null)
            return null;
        if (chance < 100)
            line += " <dark_gray>(" + trim(chance) + "%)";
        return "<dark_gray>• </dark_gray>" + line;
    }

    protected int scale(int amount, RewardContext ctx) {
        return multiply ? (int) Math.round(amount * ctx.multiplier()) : amount;
    }

    protected double scale(double amount, RewardContext ctx) {
        return multiply ? amount * ctx.multiplier() : amount;
    }

    protected static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}

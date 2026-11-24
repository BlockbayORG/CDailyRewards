package carnage.cDailyRewards.reward;

import carnage.cDailyRewards.util.ItemParser;
import carnage.cDailyRewards.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * All built-in reward types and the factory that reads them from config.
 */
public final class RewardTypes {

    private RewardTypes() {
    }

    public static Reward parse(ConfigurationSection sec, Logger log) {
        String type = sec.getString("type", "").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "command" -> new CommandReward(sec);
            case "item" -> new ItemReward(sec, log);
            case "voucher" -> new VoucherReward(sec);
            case "money" -> new MoneyReward(sec);
            case "xp" -> new XpReward(sec);
            case "message" -> new MessageReward(sec, false);
            case "broadcast" -> new MessageReward(sec, true);
            case "sound" -> new SoundReward(sec);
            case "random-pool" -> new RandomPoolReward(sec, log);
            default -> {
                log.warning("Unknown reward type '" + type + "' at " + sec.getCurrentPath());
                yield null;
            }
        };
    }

    /** Parses a YAML list of maps into rewards. */
    public static List<Reward> parseList(List<Map<?, ?>> list, String path, Logger log) {
        List<Reward> out = new ArrayList<>();
        int i = 0;
        for (Map<?, ?> map : list) {
            MemoryConfiguration mc = new MemoryConfiguration();
            ConfigurationSection sec = mc.createSection(path + "[" + i++ + "]", map);
            Reward r = parse(sec, log);
            if (r != null)
                out.add(r);
        }
        return out;
    }

    static final class CommandReward extends Reward {
        private final String command;
        private final String as;

        CommandReward(ConfigurationSection sec) {
            super(sec);
            this.command = sec.getString("command", "");
            this.as = sec.getString("as", "console").toLowerCase(Locale.ROOT);
        }

        @Override
        protected void give(RewardContext ctx) {
            String cmd = ctx.apply(command);
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            try {
                if (as.equals("player"))
                    ctx.player().performCommand(cmd);
                else
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (Exception e) {
                ctx.plugin().getLogger().warning("Reward command failed for " + ctx.player().getName() + ": " + cmd
                        + " (" + e.getMessage() + ")");
            }
        }

        @Override
        protected String defaultDisplay() {
            return null;
        }
    }

    static final class ItemReward extends Reward {
        private final ItemParser item;

        ItemReward(ConfigurationSection sec, Logger log) {
            super(sec);
            ConfigurationSection itemSec = sec.getConfigurationSection("item");
            this.item = ItemParser.of(itemSec != null ? itemSec : sec, log);
        }

        @Override
        protected void give(RewardContext ctx) {
            ItemStack stack = item.build(ctx.player(), scale(item.amount(), ctx), ctx::apply, null);
            ctx.giveItem(stack);
        }

        @Override
        protected String defaultDisplay() {
            String name = item.name() != null ? item.name() : "<lang:" + item.material().getItemTranslationKey() + ">";
            return "<white>" + item.amount() + "x </white>" + name;
        }
    }

    static final class VoucherReward extends Reward {
        private final String id;
        private final int amount;

        VoucherReward(ConfigurationSection sec) {
            super(sec);
            this.id = sec.getString("id", "");
            this.amount = Math.max(1, sec.getInt("amount", 1));
        }

        @Override
        protected void give(RewardContext ctx) {
            var voucher = ctx.plugin().vouchers().get(id);
            if (voucher == null) {
                ctx.plugin().getLogger().warning("Reward references unknown voucher '" + id + "'");
                return;
            }
            int n = scale(amount, ctx);
            for (int i = 0; i < n; i++)
                ctx.giveItem(ctx.plugin().vouchers().create(voucher, ctx.player()));
        }

        @Override
        protected String defaultDisplay() {
            return "<white>" + amount + "x </white>" + "%voucher_name_" + id + "%";
        }

        public String id() {
            return id;
        }
    }

    static final class MoneyReward extends Reward {
        private final double min;
        private final double max;

        MoneyReward(ConfigurationSection sec) {
            super(sec);
            String raw = sec.getString("amount", "0");
            double lo, hi;
            try {
                if (raw.contains("-")) {
                    String[] p = raw.split("-", 2);
                    lo = Double.parseDouble(p[0].trim());
                    hi = Double.parseDouble(p[1].trim());
                } else {
                    lo = hi = Double.parseDouble(raw.trim());
                }
            } catch (NumberFormatException e) {
                lo = hi = 0;
            }
            this.min = Math.min(lo, hi);
            this.max = Math.max(lo, hi);
        }

        @Override
        protected void give(RewardContext ctx) {
            var eco = ctx.plugin().economy();
            if (eco == null) {
                ctx.plugin().getLogger().warning("Money reward skipped: Vault economy not found");
                return;
            }
            double amount = min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
            eco.depositPlayer(ctx.player(), Math.round(scale(amount, ctx) * 100) / 100.0);
        }

        @Override
        protected String defaultDisplay() {
            String amt = min == max ? trim(min) : trim(min) + "-" + trim(max);
            return "<green>$" + amt;
        }
    }

    static final class XpReward extends Reward {
        private final int levels;
        private final int points;

        XpReward(ConfigurationSection sec) {
            super(sec);
            this.levels = sec.getInt("levels", 0);
            this.points = sec.getInt("points", 0);
        }

        @Override
        protected void give(RewardContext ctx) {
            if (levels > 0)
                ctx.player().giveExpLevels(scale(levels, ctx));
            if (points > 0)
                ctx.player().giveExp(scale(points, ctx));
        }

        @Override
        protected String defaultDisplay() {
            return levels > 0 ? "<aqua>" + levels + " XP levels" : "<aqua>" + points + " XP";
        }
    }

    static final class MessageReward extends Reward {
        private final String text;
        private final boolean broadcast;

        MessageReward(ConfigurationSection sec, boolean broadcast) {
            super(sec);
            this.text = sec.getString("text", "");
            this.broadcast = broadcast;
        }

        @Override
        protected void give(RewardContext ctx) {
            var msg = Text.parse(ctx.apply(text), ctx.player());
            if (broadcast)
                Bukkit.broadcast(msg);
            else
                ctx.player().sendMessage(msg);
        }

        @Override
        protected String defaultDisplay() {
            return null;
        }
    }

    static final class SoundReward extends Reward {
        private final String sound;
        private final float volume;
        private final float pitch;

        SoundReward(ConfigurationSection sec) {
            super(sec);
            this.sound = sec.getString("sound", "ENTITY_PLAYER_LEVELUP");
            this.volume = (float) sec.getDouble("volume", 1);
            this.pitch = (float) sec.getDouble("pitch", 1);
        }

        @Override
        protected void give(RewardContext ctx) {
            playSound(ctx.player(), sound, volume, pitch);
        }

        @Override
        protected String defaultDisplay() {
            return null;
        }
    }

    static final class RandomPoolReward extends Reward {
        private final List<Reward> pool = new ArrayList<>();
        private final List<Double> weights = new ArrayList<>();
        private final int picks;
        private final boolean unique;

        RandomPoolReward(ConfigurationSection sec, Logger log) {
            super(sec);
            this.picks = Math.max(1, sec.getInt("picks", 1));
            this.unique = sec.getBoolean("unique", true);
            int i = 0;
            for (Map<?, ?> map : sec.getMapList("pool")) {
                MemoryConfiguration mc = new MemoryConfiguration();
                ConfigurationSection s = mc.createSection(sec.getCurrentPath() + ".pool[" + i++ + "]", map);
                Reward r = parse(s, log);
                if (r == null)
                    continue;
                pool.add(r);
                weights.add(Math.max(0, s.getDouble("weight", 1)));
            }
        }

        @Override
        protected void give(RewardContext ctx) {
            List<Reward> options = new ArrayList<>(pool);
            List<Double> w = new ArrayList<>(weights);
            for (int p = 0; p < picks && !options.isEmpty(); p++) {
                double total = w.stream().mapToDouble(Double::doubleValue).sum();
                if (total <= 0)
                    return;
                double roll = ThreadLocalRandom.current().nextDouble(total);
                int idx = 0;
                while (idx < w.size() - 1 && (roll -= w.get(idx)) >= 0)
                    idx++;
                options.get(idx).tryGive(ctx);
                if (unique) {
                    options.remove(idx);
                    w.remove(idx);
                }
            }
        }

        @Override
        protected String defaultDisplay() {
            return "<light_purple>" + picks + "x random reward";
        }
    }

    /**
     * Plays a sound by Bukkit field name (ENTITY_PLAYER_LEVELUP) or key
     * (entity.player.levelup).
     */
    public static void playSound(org.bukkit.entity.Player player, String name, float volume, float pitch) {
        if (name == null || name.isEmpty())
            return;
        try {
            if (name.contains(".") || name.contains(":")) {
                player.playSound(
                        Sound.sound(Key.key(name.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, volume, pitch));
                return;
            }
            Object field = org.bukkit.Sound.class.getField(name.toUpperCase(Locale.ROOT)).get(null);
            player.playSound(player.getLocation(), (org.bukkit.Sound) field, volume, pitch);
        } catch (Exception ignored) {
            // unknown sound name, skip quietly
        }
    }
}

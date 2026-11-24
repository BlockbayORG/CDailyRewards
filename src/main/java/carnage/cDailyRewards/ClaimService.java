package carnage.cDailyRewards;

import carnage.cDailyRewards.Settings.CooldownMode;
import carnage.cDailyRewards.reward.Reward;
import carnage.cDailyRewards.reward.RewardContext;
import carnage.cDailyRewards.reward.RewardDay;
import carnage.cDailyRewards.reward.RewardTypes;
import carnage.cDailyRewards.storage.PlayerData;
import carnage.cDailyRewards.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Cooldown, missed-day and claim rules. Main thread only.
 */
public final class ClaimService {

    public enum State {
        AVAILABLE, COOLDOWN, FINISHED
    }

    private static final long DAY_MS = 86_400_000L;

    private final CDailyRewards plugin;
    private final Set<UUID> dropped = new HashSet<>();

    public ClaimService(CDailyRewards plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ---- time rules ----

    /** Index of the reset period a moment falls in (FIXED_RESET mode). */
    private long period(long millis) {
        ZonedDateTime z = Instant.ofEpochMilli(millis).atZone(s().zone).minusHours(s().resetTime.getHour())
                .minusMinutes(s().resetTime.getMinute());
        return z.toLocalDate().toEpochDay();
    }

    /** Epoch millis when the player can claim next. */
    public long nextClaimAt(PlayerData d) {
        if (d.lastClaim() == 0 || d.cooldownSkipped())
            return 0;
        if (s().cooldownMode == CooldownMode.ROLLING_24H)
            return d.lastClaim() + DAY_MS;
        long nextPeriod = period(d.lastClaim()) + 1;
        return java.time.LocalDate.ofEpochDay(nextPeriod).atTime(s().resetTime).atZone(s().zone).toInstant()
                .toEpochMilli();
    }

    public long timeLeft(PlayerData d) {
        return Math.max(0, nextClaimAt(d) - System.currentTimeMillis());
    }

    public State state(PlayerData d) {
        if (d.finished())
            return State.FINISHED;
        return System.currentTimeMillis() >= nextClaimAt(d) ? State.AVAILABLE : State.COOLDOWN;
    }

    private boolean missed(PlayerData d, long now) {
        if (d.lastClaim() == 0 || d.finished() || d.cooldownSkipped())
            return false;
        if (s().cooldownMode == CooldownMode.ROLLING_24H) {
            return now - d.lastClaim() > 2 * DAY_MS + s().graceMillis;
        }
        return period(now - s().graceMillis) - period(d.lastClaim()) >= 2;
    }

    /**
     * Applies on-miss rules when the player skipped a whole day. Call before
     * showing or claiming.
     *
     * @return true if progress changed
     */
    public boolean checkMissed(Player player, PlayerData d) {
        if (!missed(d, System.currentTimeMillis()))
            return false;
        switch (s().onMiss) {
            case RESET -> {
                d.day(1);
                d.streak(0);
                d.lastClaim(0);
            }
            case KEEP -> {
                d.streak(0);
                d.lastClaim(0);
            }
            case GO_BACK_N -> {
                d.day(Math.max(1, d.day() - s().goBackDays));
                d.streak(0);
                d.lastClaim(0);
            }
            case PAUSE -> d.lastClaim(0);
        }
        plugin.data().save(d);
        if (s().onMiss != Settings.OnMiss.PAUSE)
            plugin.messages().send(player, "streak-lost", Map.of());
        return true;
    }

    /** Minutes of playtime still needed, 0 if none. */
    public int playtimeMissing(Player player, PlayerData d) {
        if (s().requirePlaytime <= 0 || player.hasPermission("cdr.bypass.playtime"))
            return 0;
        long played = player.getStatistic(Statistic.PLAY_ONE_MINUTE) - d.playtimeAtClaim();
        long needed = s().requirePlaytime * 1200L - played;
        return needed <= 0 ? 0 : (int) Math.ceil(needed / 1200.0);
    }

    public double multiplier(Player player) {
        double best = 1;
        for (Map.Entry<String, Double> e : s().multipliers.entrySet()) {
            if (player.hasPermission("cdr.multiplier." + e.getKey()))
                best = Math.max(best, e.getValue());
        }
        return best;
    }

    // ---- claiming ----

    public void claim(Player player) {
        PlayerData d = plugin.data().get(player.getUniqueId());
        if (d == null)
            return;
        checkMissed(player, d);

        State state = state(d);
        if (state == State.FINISHED) {
            plugin.messages().send(player, "finished", Map.of());
            return;
        }
        if (state == State.COOLDOWN && !player.hasPermission("cdr.bypass.cooldown")) {
            plugin.messages().send(player, "not-ready", Map.of("%time_left%", Text.formatDuration(timeLeft(d))));
            return;
        }
        int missing = playtimeMissing(player, d);
        if (missing > 0) {
            plugin.messages().send(player, "need-playtime", Map.of("%minutes%", String.valueOf(missing)));
            return;
        }
        if (!s().dropWhenFull && player.getInventory().firstEmpty() == -1) {
            plugin.messages().send(player, "inventory-full", Map.of());
            return;
        }

        int day = d.day();
        // Update progress first, so an exception in a reward can't allow a second
        // claim.
        d.lastClaim(System.currentTimeMillis());
        d.cooldownSkipped(false);
        d.playtimeAtClaim(player.getStatistic(Statistic.PLAY_ONE_MINUTE));
        d.streak(d.streak() + 1);
        d.totalClaims(d.totalClaims() + 1);
        if (day >= s().days) {
            switch (s().afterLastDay) {
                case RESTART -> d.day(1);
                case LOOP_LAST -> d.day(s().days);
                case STOP -> d.finished(true);
            }
        } else {
            d.day(day + 1);
        }
        plugin.data().save(d);

        giveDay(player, day);
        RewardDay rd = plugin.rewards().get(day);
        if (rd.broadcast() != null && !rd.broadcast().isEmpty()) {
            Bukkit.broadcast(Text.parse(
                    rd.broadcast().replace("%player%", player.getName()).replace("%day%", String.valueOf(day)),
                    player));
        }
        playEffects(player);
        plugin.messages().send(player, "claimed", Map.of("%day%", String.valueOf(day)));
        if (dropped.remove(player.getUniqueId()))
            plugin.messages().send(player, "inventory-full-dropped", Map.of());
    }

    /** Gives a day's rewards without touching progress. */
    public void giveDay(Player player, int day) {
        RewardContext ctx = new RewardContext(plugin, player, day, multiplier(player));
        for (Reward r : plugin.rewards().get(day).rewards()) {
            try {
                r.tryGive(ctx);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Reward failed for " + player.getName() + " on day " + day, e);
            }
        }
    }

    public void markDropped(Player player) {
        dropped.add(player.getUniqueId());
    }

    private void playEffects(Player player) {
        RewardTypes.playSound(player, s().claimSound, 1f, 1f);
        if (!s().claimParticle.isEmpty()) {
            try {
                Particle particle = Particle.valueOf(s().claimParticle.toUpperCase(Locale.ROOT));
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), s().claimParticleCount,
                        0.5, 0.8, 0.5, 0.1);
            } catch (Exception ignored) {
                // invalid particle or one that needs data
            }
        }
    }
}

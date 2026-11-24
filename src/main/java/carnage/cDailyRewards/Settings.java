package carnage.cDailyRewards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class Settings {

    public enum CooldownMode {
        ROLLING_24H, FIXED_RESET
    }

    public enum OnMiss {
        RESET, KEEP, PAUSE, GO_BACK_N
    }

    public enum AfterLastDay {
        RESTART, LOOP_LAST, STOP
    }

    public final String storage;
    public final int days;
    public final CooldownMode cooldownMode;
    public final LocalTime resetTime;
    public final ZoneId zone;
    public final OnMiss onMiss;
    public final int goBackDays;
    public final long graceMillis;
    public final AfterLastDay afterLastDay;
    public final int requirePlaytime;
    public final boolean joinNotify;
    public final long joinNotifyDelay;
    public final String joinNotifySound;
    public final boolean dropWhenFull;
    public final Map<String, Double> multipliers = new LinkedHashMap<>();
    public final String claimSound;
    public final String claimParticle;
    public final int claimParticleCount;

    public Settings(FileConfiguration c, Logger log) {
        this.storage = c.getString("storage", "YAML").toUpperCase(Locale.ROOT);
        this.days = Math.max(1, c.getInt("days", 30));
        this.cooldownMode = parse(CooldownMode.class, c.getString("cooldown-mode"), CooldownMode.ROLLING_24H, log);
        LocalTime reset;
        try {
            reset = LocalTime.parse(c.getString("reset-time", "00:00"));
        } catch (Exception e) {
            log.warning("Invalid reset-time, using 00:00");
            reset = LocalTime.MIDNIGHT;
        }
        this.resetTime = reset;
        ZoneId z;
        try {
            String tz = c.getString("timezone", "");
            z = tz.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            log.warning("Invalid timezone, using server default");
            z = ZoneId.systemDefault();
        }
        this.zone = z;
        this.onMiss = parse(OnMiss.class, c.getString("on-miss"), OnMiss.RESET, log);
        this.goBackDays = Math.max(0, c.getInt("go-back-days", 3));
        this.graceMillis = (long) (c.getDouble("grace-hours", 0) * 3_600_000L);
        this.afterLastDay = parse(AfterLastDay.class, c.getString("after-last-day"), AfterLastDay.RESTART, log);
        this.requirePlaytime = Math.max(0, c.getInt("require-playtime", 0));
        this.joinNotify = c.getBoolean("join-notify.enabled", true);
        this.joinNotifyDelay = c.getLong("join-notify.delay-ticks", 60);
        this.joinNotifySound = c.getString("join-notify.sound", "");
        this.dropWhenFull = !"DENY".equalsIgnoreCase(c.getString("full-inventory", "DROP"));
        ConfigurationSection m = c.getConfigurationSection("multipliers");
        if (m != null)
            for (String k : m.getKeys(false))
                multipliers.put(k, m.getDouble(k, 1));
        this.claimSound = c.getString("claim-effects.sound", "");
        this.claimParticle = c.getString("claim-effects.particle", "");
        this.claimParticleCount = c.getInt("claim-effects.particle-count", 30);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E def, Logger log) {
        if (value == null)
            return def;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Invalid value '" + value + "' for " + type.getSimpleName() + ", using " + def);
            return def;
        }
    }
}

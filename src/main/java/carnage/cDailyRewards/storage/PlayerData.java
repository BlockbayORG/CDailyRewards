package carnage.cDailyRewards.storage;

import java.util.UUID;

/**
 * Daily progress of one player. Only touched on the main thread; storage gets a
 * {@link #copy()}.
 */
public final class PlayerData {

    private final UUID uuid;
    /** Next day to claim, 1-based. */
    private int day = 1;
    private int streak;
    private int totalClaims;
    /** Epoch millis of the last claim, 0 = never. */
    private long lastClaim;
    /** PLAY_ONE_MINUTE statistic (ticks) at the last claim. */
    private long playtimeAtClaim;
    /** Set by admin skipcooldown. */
    private boolean cooldownSkipped;
    /** Finished the calendar with after-last-day: STOP. */
    private boolean finished;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerData copy() {
        PlayerData c = new PlayerData(uuid);
        c.day = day;
        c.streak = streak;
        c.totalClaims = totalClaims;
        c.lastClaim = lastClaim;
        c.playtimeAtClaim = playtimeAtClaim;
        c.cooldownSkipped = cooldownSkipped;
        c.finished = finished;
        return c;
    }

    public void reset() {
        day = 1;
        streak = 0;
        lastClaim = 0;
        playtimeAtClaim = 0;
        cooldownSkipped = false;
        finished = false;
    }

    public UUID uuid() {
        return uuid;
    }
    public int day() {
        return day;
    }
    public void day(int day) {
        this.day = day;
    }
    public int streak() {
        return streak;
    }
    public void streak(int streak) {
        this.streak = streak;
    }
    public int totalClaims() {
        return totalClaims;
    }
    public void totalClaims(int totalClaims) {
        this.totalClaims = totalClaims;
    }
    public long lastClaim() {
        return lastClaim;
    }
    public void lastClaim(long lastClaim) {
        this.lastClaim = lastClaim;
    }
    public long playtimeAtClaim() {
        return playtimeAtClaim;
    }
    public void playtimeAtClaim(long playtimeAtClaim) {
        this.playtimeAtClaim = playtimeAtClaim;
    }
    public boolean cooldownSkipped() {
        return cooldownSkipped;
    }
    public void cooldownSkipped(boolean cooldownSkipped) {
        this.cooldownSkipped = cooldownSkipped;
    }
    public boolean finished() {
        return finished;
    }
    public void finished(boolean finished) {
        this.finished = finished;
    }
}

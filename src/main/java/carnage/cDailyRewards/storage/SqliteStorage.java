package carnage.cDailyRewards.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** SQLite storage using the driver bundled with Paper. Single connection, used from one thread. */
public final class SqliteStorage implements Storage {

    private final Connection connection;

    public SqliteStorage(File dataFolder) throws SQLException {
        dataFolder.mkdirs();
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS cdr_players ("
                    + "uuid TEXT PRIMARY KEY,"
                    + "day INTEGER NOT NULL DEFAULT 1,"
                    + "streak INTEGER NOT NULL DEFAULT 0,"
                    + "total_claims INTEGER NOT NULL DEFAULT 0,"
                    + "last_claim INTEGER NOT NULL DEFAULT 0,"
                    + "playtime_at_claim INTEGER NOT NULL DEFAULT 0,"
                    + "cooldown_skipped INTEGER NOT NULL DEFAULT 0,"
                    + "finished INTEGER NOT NULL DEFAULT 0)");
        }
    }

    @Override
    public PlayerData load(UUID uuid) throws SQLException {
        PlayerData data = new PlayerData(uuid);
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM cdr_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.day(Math.max(1, rs.getInt("day")));
                    data.streak(rs.getInt("streak"));
                    data.totalClaims(rs.getInt("total_claims"));
                    data.lastClaim(rs.getLong("last_claim"));
                    data.playtimeAtClaim(rs.getLong("playtime_at_claim"));
                    data.cooldownSkipped(rs.getInt("cooldown_skipped") != 0);
                    data.finished(rs.getInt("finished") != 0);
                }
            }
        }
        return data;
    }

    @Override
    public void save(PlayerData d) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO cdr_players (uuid, day, streak, total_claims, last_claim, playtime_at_claim, cooldown_skipped, finished) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, d.uuid().toString());
            ps.setInt(2, d.day());
            ps.setInt(3, d.streak());
            ps.setInt(4, d.totalClaims());
            ps.setLong(5, d.lastClaim());
            ps.setLong(6, d.playtimeAtClaim());
            ps.setInt(7, d.cooldownSkipped() ? 1 : 0);
            ps.setInt(8, d.finished() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}

package dev.ensisdev.enspillars.storage

import dev.ensisdev.enspillars.stats.PlayerStats
import java.util.UUID

/**
 * Oyuncu satırı: istatistik + para + seçili kozmetikler.
 * uuid birincil anahtardır.
 */
object PlayerTable {
    const val TABLE = "enspillars_players"
    private val COLS = listOf(
        "uuid", "name", "games", "wins", "draws", "losses", "kills", "deaths",
        "coins", "points", "win_streak", "max_streak", "play_seconds", "last_game",
        "cage", "killmessage", "deathcry"
    )
    private val UPDATE = COLS.filterNot { it == "uuid" }

    fun create(db: DatabaseManager) {
        db.connection()?.use { c ->
            c.createStatement().use {
                it.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS $TABLE (" +
                        "uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32), " +
                        "games INT DEFAULT 0, wins INT DEFAULT 0, draws INT DEFAULT 0, losses INT DEFAULT 0, " +
                        "kills INT DEFAULT 0, deaths INT DEFAULT 0, coins INT DEFAULT 0, points INT DEFAULT 0, " +
                        "win_streak INT DEFAULT 0, max_streak INT DEFAULT 0, " +
                        "play_seconds BIGINT DEFAULT 0, last_game BIGINT DEFAULT 0, " +
                        "cage VARCHAR(64) DEFAULT '', killmessage VARCHAR(64) DEFAULT '', deathcry VARCHAR(64) DEFAULT '')"
                )
            }
        }
    }

    data class Row(val stats: PlayerStats, val name: String, val cage: String, val killMessage: String, val deathCry: String)

    fun load(db: DatabaseManager, uuid: UUID): Row? {
        val c = db.connection() ?: return null
        c.use {
            it.prepareStatement("SELECT * FROM $TABLE WHERE uuid=?").use { ps ->
                ps.setString(1, uuid.toString())
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    val s = PlayerStats(
                        rs.getInt("games"), rs.getInt("wins"), rs.getInt("draws"), rs.getInt("losses"),
                        rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("coins"), rs.getInt("points"),
                        rs.getInt("win_streak"), rs.getInt("max_streak"),
                        rs.getLong("play_seconds"), rs.getLong("last_game")
                    )
                    return Row(s, rs.getString("name") ?: "", rs.getString("cage") ?: "",
                        rs.getString("killmessage") ?: "", rs.getString("deathcry") ?: "")
                }
            }
        }
    }

    fun updateCosmetics(db: DatabaseManager, uuid: UUID, cage: String, killMessage: String, deathCry: String) {
        val c = db.connection() ?: return
        c.use {
            it.prepareStatement("UPDATE $TABLE SET cage=?, killmessage=?, deathcry=? WHERE uuid=?").use { ps ->
                ps.setString(1, cage); ps.setString(2, killMessage); ps.setString(3, deathCry)
                ps.setString(4, uuid.toString())
                ps.executeUpdate()
            }
        }
    }

    fun save(db: DatabaseManager, uuid: UUID, name: String, s: PlayerStats, cage: String, killMessage: String, deathCry: String) {
        val c = db.connection() ?: return
        c.use {
            it.prepareStatement(db.dialect.upsert(TABLE, COLS, UPDATE)).use { ps ->
                var i = 1
                ps.setString(i++, uuid.toString()); ps.setString(i++, name)
                ps.setInt(i++, s.games); ps.setInt(i++, s.wins); ps.setInt(i++, s.draws); ps.setInt(i++, s.losses)
                ps.setInt(i++, s.kills); ps.setInt(i++, s.deaths); ps.setInt(i++, s.coins); ps.setInt(i++, s.points)
                ps.setInt(i++, s.winStreak); ps.setInt(i++, s.maxWinStreak)
                ps.setLong(i++, s.playSeconds); ps.setLong(i++, s.lastGame)
                ps.setString(i++, cage); ps.setString(i++, killMessage); ps.setString(i, deathCry)
                ps.executeUpdate()
            }
        }
    }
}

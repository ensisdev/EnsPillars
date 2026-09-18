package dev.ensisdev.enspillars.storage

import java.util.UUID

/**
 * Satın alınmış kozmetikler: (uuid, type, id). type: cage|killmessage|deathcry.
 * Config'den kaldırılan ID'ler senkron sırasında temizlenir.
 */
object OwnedTable {
    const val TABLE = "enspillars_owned"

    fun create(db: DatabaseManager) {
        db.connection()?.use { c ->
            c.createStatement().use {
                it.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS $TABLE (" +
                        "uuid VARCHAR(36), type VARCHAR(16), id VARCHAR(64), " +
                        "PRIMARY KEY (uuid, type, id))"
                )
            }
        }
    }

    fun load(db: DatabaseManager, uuid: UUID, type: String): Set<String> {
        val out = mutableSetOf<String>()
        val c = db.connection() ?: return out
        c.use {
            it.prepareStatement("SELECT id FROM $TABLE WHERE uuid=? AND type=?").use { ps ->
                ps.setString(1, uuid.toString()); ps.setString(2, type)
                ps.executeQuery().use { rs -> while (rs.next()) out += rs.getString(1) }
            }
        }
        return out
    }

    fun sync(db: DatabaseManager, uuid: UUID, type: String, owned: Set<String>) {
        val c = db.connection() ?: return
        c.use {
            if (owned.isEmpty()) {
                it.prepareStatement("DELETE FROM $TABLE WHERE uuid=? AND type=?").use { ps ->
                    ps.setString(1, uuid.toString()); ps.setString(2, type); ps.executeUpdate()
                }
            } else {
                val placeholders = owned.joinToString(",") { "?" }
                it.prepareStatement("DELETE FROM $TABLE WHERE uuid=? AND type=? AND id NOT IN ($placeholders)").use { ps ->
                    ps.setString(1, uuid.toString()); ps.setString(2, type)
                    var i = 3
                    owned.forEach { id -> ps.setString(i++, id) }
                    ps.executeUpdate()
                }
            }
            it.prepareStatement(db.dialect.insertIgnore(TABLE, listOf("uuid", "type", "id"))).use { ps ->
                owned.forEach { id ->
                    ps.setString(1, uuid.toString()); ps.setString(2, type); ps.setString(3, id)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }
}

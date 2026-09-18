package dev.ensisdev.enspillars.storage

/** MySQL / SQLite arası upsert farkını gizler. */
enum class SqlDialect {
    MYSQL {
        override fun upsert(table: String, cols: List<String>, updateCols: List<String>): String {
            val all = cols.joinToString(",")
            val ph = cols.joinToString(",") { "?" }
            val upd = updateCols.joinToString(",") { "$it=VALUES($it)" }
            return "INSERT INTO $table ($all) VALUES ($ph) ON DUPLICATE KEY UPDATE $upd"
        }
        override fun insertIgnore(table: String, cols: List<String>): String {
            val all = cols.joinToString(",")
            val ph = cols.joinToString(",") { "?" }
            return "INSERT IGNORE INTO $table ($all) VALUES ($ph)"
        }
    },
    SQLITE {
        override fun upsert(table: String, cols: List<String>, updateCols: List<String>): String {
            val all = cols.joinToString(",")
            val ph = cols.joinToString(",") { "?" }
            val upd = updateCols.joinToString(",") { "$it=excluded.$it" }
            return "INSERT INTO $table ($all) VALUES ($ph) ON CONFLICT(uuid) DO UPDATE SET $upd"
        }
        override fun insertIgnore(table: String, cols: List<String>): String {
            val all = cols.joinToString(",")
            val ph = cols.joinToString(",") { "?" }
            return "INSERT INTO $table ($all) VALUES ($ph) ON CONFLICT DO NOTHING"
        }
    };

    abstract fun upsert(table: String, cols: List<String>, updateCols: List<String>): String
    abstract fun insertIgnore(table: String, cols: List<String>): String
}

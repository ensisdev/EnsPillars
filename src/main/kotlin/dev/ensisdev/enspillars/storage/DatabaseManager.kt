package dev.ensisdev.enspillars.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.ensisdev.enspillars.EnsPillarsPlugin
import java.io.File
import java.sql.Connection

/**
 * HikariCP destekli veritabanı yöneticisi. Bağlantı kurulamazsa
 * YAML moduna sessizce düşer, oyun asla başlamamazlık etmez.
 */
class DatabaseManager(private val plugin: EnsPillarsPlugin) {
    enum class Backend { YAML, SQLITE, MYSQL }

    val backend: Backend =
        runCatching { Backend.valueOf(plugin.config.getString("storage.type", "YAML")!!.uppercase()) }
            .getOrDefault(Backend.YAML)
    val dialect: SqlDialect = if (backend == Backend.MYSQL) SqlDialect.MYSQL else SqlDialect.SQLITE

    @Volatile private var ds: HikariDataSource? = null
    fun ready(): Boolean = backend != Backend.YAML && ds?.isClosed == false

    fun connect(): Boolean {
        if (backend == Backend.YAML) return false
        return runCatching {
            val cfg = HikariConfig()
            if (backend == Backend.MYSQL) {
                val host = plugin.config.getString("storage.mysql.host", "localhost")
                val port = plugin.config.getInt("storage.mysql.port", 3306)
                val db = plugin.config.getString("storage.mysql.database", "enspillars")
                cfg.jdbcUrl = "jdbc:mysql://$host:$port/$db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                cfg.username = plugin.config.getString("storage.mysql.username", "root")
                cfg.password = plugin.config.getString("storage.mysql.password", "")
                cfg.maximumPoolSize = plugin.config.getInt("storage.mysql.pool-size", 5).coerceIn(1, 20)
                cfg.driverClassName = "com.mysql.cj.jdbc.Driver"
            } else {
                cfg.jdbcUrl = "jdbc:sqlite:${File(plugin.dataFolder, "data.db").absolutePath}"
                cfg.driverClassName = "org.sqlite.JDBC"
                cfg.maximumPoolSize = 1
            }
            cfg.poolName = "EnsPillars"
            cfg.connectionTimeout = 10000
            ds = HikariDataSource(cfg)
            PlayerTable.create(this)
            OwnedTable.create(this)
            plugin.logger.info("Veritabanı hazır: $backend")
            true
        }.onFailure { plugin.logger.severe("Veritabanı bağlantısı başarısız, YAML moduna düşüldü: ${it.message}") }
            .getOrDefault(false)
    }

    fun connection(): Connection? = runCatching {
        val d = ds ?: return null
        if (d.isClosed) return null
        d.connection
    }.getOrNull()

    fun disconnect() {
        runCatching { ds?.close() }
        ds = null
    }
}

package dev.ensisdev.enspillars

import dev.ensisdev.enspillars.api.ApiRegistry
import dev.ensisdev.enspillars.api.EnsPillarsAPI
import dev.ensisdev.enspillars.command.info.InfoCommand
import dev.ensisdev.enspillars.command.PofCommand
import dev.ensisdev.enspillars.config.ConfigurationService
import dev.ensisdev.enspillars.cosmetic.CosmeticsService
import dev.ensisdev.enspillars.game.ArenaManager
import dev.ensisdev.enspillars.gui.ArenaMenu
import dev.ensisdev.enspillars.gui.BossBarService
import dev.ensisdev.enspillars.gui.ScoreboardService
import dev.ensisdev.enspillars.gui.ShopMenu
import dev.ensisdev.enspillars.hotbar.HotbarService
import dev.ensisdev.enspillars.integration.PlaceholderBridge
import dev.ensisdev.enspillars.killmessage.DeathCries
import dev.ensisdev.enspillars.killmessage.KillMessages
import dev.ensisdev.enspillars.listener.GameListener
import dev.ensisdev.enspillars.loot.LootService
import dev.ensisdev.enspillars.party.PartyService
import dev.ensisdev.enspillars.platform.PlatformBridge
import dev.ensisdev.enspillars.replay.ReplayService
import dev.ensisdev.enspillars.setup.ArenaSetupService
import dev.ensisdev.enspillars.setup.SetupMode
import dev.ensisdev.enspillars.snapshot.SnapshotStore
import dev.ensisdev.enspillars.stats.StatsService
import dev.ensisdev.enspillars.storage.DatabaseManager
import dev.ensisdev.enspillars.storage.StorageService
import dev.ensisdev.enspillars.util.MessageService
import org.bukkit.plugin.java.JavaPlugin

class EnsPillarsPlugin : JavaPlugin() {
    companion object {
        /** Beklenen config şeması (config.yml -> config-version). */
        const val EXPECTED_CONFIG_VERSION = 1
        /** bStats plugin ID (https://bstats.org/plugin/bukkit/EnsPillars/34112). 0 = pasif. */
        const val BSTATS_ID = 34112
    }

    lateinit var configuration: ConfigurationService; private set
    lateinit var messages: MessageService; private set
    lateinit var storage: StorageService; private set
    lateinit var platform: PlatformBridge; private set
    lateinit var arenas: ArenaManager; private set
    lateinit var loot: LootService; private set
    lateinit var setup: ArenaSetupService; private set
    lateinit var cosmetics: CosmeticsService; private set
    lateinit var stats: StatsService; private set
    lateinit var menu: ArenaMenu; private set
    lateinit var boards: ScoreboardService; private set
    lateinit var bossbars: BossBarService; private set
    lateinit var shop: ShopMenu; private set
    lateinit var shopCatalog: dev.ensisdev.enspillars.shop.ShopCatalog; private set
    lateinit var deathCries: DeathCries; private set
    lateinit var hotbar: HotbarService; private set
    lateinit var setupMode: SetupMode; private set
    lateinit var replay: ReplayService; private set
    lateinit var placeholders: PlaceholderBridge; private set
    lateinit var parties: PartyService; private set
    lateinit var snapshots: SnapshotStore; private set
    lateinit var db: DatabaseManager; private set
    lateinit var killMessages: KillMessages; private set
    lateinit var confirms: dev.ensisdev.enspillars.command.ConfirmService; private set

    override fun onEnable() {
        saveDefaultConfig()
        if (config.getInt("config-version", 0) != EXPECTED_CONFIG_VERSION) {
            logger.warning("config-version uyumsuz (beklenen=$EXPECTED_CONFIG_VERSION). Mevcut config ezilmedi; yeni anahtarları CHANGELOG'a bakarak elle birleştir.")
        }
        saveResource("messages.yml", false)
        saveResource("scoreboards.yml", false)
        saveResource("hotbar.yml", false)
        saveResource("shop.yml", false)
        saveResource("commands.yml", false)
        saveResource("arenas/example.yml", false)
        configuration = ConfigurationService(this)
        messages = MessageService(this)
        confirms = dev.ensisdev.enspillars.command.ConfirmService(this)
        storage = StorageService(this)
        platform = PlatformBridge(this)
        cosmetics = CosmeticsService(this)
        stats = StatsService(this)
        db = DatabaseManager(this)
        db.connect()
        killMessages = KillMessages(this)
        deathCries = DeathCries(this)
        shopCatalog = dev.ensisdev.enspillars.shop.ShopCatalog(this)
        hotbar = HotbarService(this)
        setup = ArenaSetupService(this)
        setupMode = SetupMode(this)
        snapshots = SnapshotStore(this)
        loot = LootService(this)
        loot.reload()
        arenas = ArenaManager(this)
        menu = ArenaMenu(this)
        boards = ScoreboardService(this)
        bossbars = BossBarService(this)
        shop = ShopMenu(this)
        placeholders = PlaceholderBridge(this)
        placeholders.refresh()
        placeholders.register()
        if (placeholders.isHooked()) runCatching {
            dev.ensisdev.enspillars.integration.EnsPillarsExpansion(this).register()
            logger.info("PlaceholderAPI expansion registered.")
        }
        parties = PartyService(config.getInt("settings.max-party-size", 5).coerceIn(2, 40))
        arenas.loadAll()
        arenas.warnSharedWorlds()
        stats.startAutosave()
        val residue = runCatching { arenas.cleanupResidue() }.getOrDefault(0)
        replay = ReplayService(this)

        val info = InfoCommand(this)
        getCommand("enspillars")?.apply { setExecutor(info); tabCompleter = info }
        val pof = PofCommand(this)
        getCommand("pof")?.apply { setExecutor(pof); tabCompleter = pof }
        applyCommandConfig()
        server.pluginManager.registerEvents(GameListener(this), this)
        ApiRegistry.register(object : EnsPillarsAPI {
            override fun arenas() = this@EnsPillarsPlugin.arenas.all().map { it.snapshot() }
            override fun arena(id: String) = this@EnsPillarsPlugin.arenas.get(id)?.snapshot()
            override fun version() = description.version
        })
        logger.info("EnsPillars ${description.version} enabled. Arenas=${arenas.size} residue=$residue")
        if (BSTATS_ID != 0) {
            runCatching { org.bstats.bukkit.Metrics(this, BSTATS_ID) }
                .onFailure { logger.warning("bStats başlatılamadı: ${it.message}") }
        }
        dev.ensisdev.enspillars.util.UpdateChecker(this).checkAsync()
    }

    override fun onDisable() {
        if (::replay.isInitialized) replay.shutdown()
        if (::arenas.isInitialized) arenas.shutdown()
        if (::boards.isInitialized) boards.hideAll()
        if (::bossbars.isInitialized) bossbars.hideAll()
        if (::stats.isInitialized) { stats.stopAutosave(); stats.saveAllSync() }
        else if (::storage.isInitialized) storage.flush()
        if (::db.isInitialized) db.disconnect()
        ApiRegistry.clear()
    }

    fun reloadPlugin() {
        reloadConfigOnly()
        reloadMessages()
        reloadShop()
        reloadBoards()
        reloadHotbar()
        reloadArenas()
    }

    fun reloadConfigOnly() {
        reloadConfig()
        configuration.reload()
        if (::loot.isInitialized) loot.reload()
    }

    fun reloadMessages() = messages.reload()
    fun reloadArenas() = arenas.loadAll()
    fun reloadShop() { if (::shopCatalog.isInitialized) shopCatalog.reload() }
    fun reloadBoards() { if (::boards.isInitialized) boards.reload() }
    fun reloadHotbar() { if (::hotbar.isInitialized) hotbar.reload() }

    /** commands.yml: alias/description uygular, enabled bayrağını okur. */
    fun isCommandEnabled(name: String): Boolean =
        runCatching {
            val y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File(dataFolder, "commands.yml"))
            y.getBoolean("commands.$name.enabled", true)
        }.getOrDefault(true)

    private fun applyCommandConfig() {
        runCatching {
            val y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File(dataFolder, "commands.yml"))
            listOf("enspillars", "pof").forEach { name ->
                getCommand(name)?.let { cmd ->
                    y.getString("commands.$name.description")?.takeIf { it.isNotBlank() }?.let { cmd.description = it }
                    val aliases = y.getStringList("commands.$name.aliases").filter { it.isNotBlank() }
                    runCatching { cmd.aliases = aliases }
                }
            }
        }.onFailure { logger.warning("commands.yml uygulanamadı: ${it.message}") }
    }

    fun placeholdersReady() = ::placeholders.isInitialized
    fun dbReady() = ::db.isInitialized && runCatching { db.ready() }.getOrDefault(false)
    fun cosmeticsReady() = ::cosmetics.isInitialized
}

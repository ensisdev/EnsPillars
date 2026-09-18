package dev.ensisdev.enspillars.gui

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.cosmetic.CosmeticsService
import dev.ensisdev.enspillars.shop.ShopEntry
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

/** Mağaza ana menüsü: 3 kategori + bakiye. */
class ShopMenu(plugin: EnsPillarsPlugin) : Menu(plugin, "§8§lMağaza", 27) {
    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val coins = plugin.stats.load(viewer.uniqueId).coins
        inv.setItem(11, item(Material.END_PORTAL_FRAME, "§bKafesler", listOf("§7Kafes görünümleri"), true))
        inv.setItem(13, item(Material.DIAMOND_SWORD, "§cKill Mesajları", listOf("§7Öldürme anonsların")))
        inv.setItem(15, item(Material.TOTEM_OF_UNDYING, "§dÖlüm Sesleri", listOf("§7Elendiğinde çalan ses")))
        inv.setItem(22, item(Material.SUNFLOWER, "§6Bakiye: §e$coins", emptyList(), true))
        inv.setItem(26, item(Material.BARRIER, "§cKapat"))
        filler(inv, Material.BLACK_STAINED_GLASS_PANE)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        when (e.rawSlot) {
            11 -> CageShopMenu(plugin).open(p)
            13 -> KillMessageShopMenu(plugin).open(p)
            15 -> DeathCryShopMenu(plugin).open(p)
            26 -> p.closeInventory()
        }
    }
}

/**
 * Jenerik sayfalı kozmetik menüsü: satın al / kuşan akışı tek yerde.
 * Alt sınıflar yalnızca tanım haritasını ve tipi verir.
 */
abstract class CosmeticShopMenu(plugin: EnsPillarsPlugin, title: String, val type: String) : Menu(plugin, title, 54) {
    private val slots = (10..16).toList() + (19..25).toList() + (28..34).toList() + (37..43).toList()

    abstract fun entries(): Map<String, ShopEntry>
    abstract fun selectedId(viewer: Player): String
    open fun isSelected(viewer: Player, def: ShopEntry) = selectedId(viewer).equals(def.id, true)

    override fun fill(inv: Inventory, viewer: Player, page: Int) {
        val list = entries().values.toList()
        val pages = maxOf(1, (list.size + slots.size - 1) / slots.size)
        val pg = page.coerceIn(1, pages)
        list.drop((pg - 1) * slots.size).take(slots.size).forEachIndexed { i, def ->
            val selected = isSelected(viewer, def)
            val owned = plugin.cosmetics.owns(viewer.uniqueId, type, def.id) || def.price <= 0
            val hasPerm = def.permission.isBlank() || viewer.hasPermission(def.permission)
            val status = when {
                selected -> plugin.messages.get("shop.status-selected")
                !owned -> plugin.messages.get("shop.status-locked", mapOf("price" to def.price))
                !hasPerm -> plugin.messages.get("shop.status-no-permission")
                else -> plugin.messages.get("shop.status-unlocked")
            }
            inv.setItem(slots[i], item(def.material, def.name, def.lore + listOf("", status), selected || def.glow))
        }
        if (pg > 1) inv.setItem(45, item(Material.ARROW, "§eÖnceki sayfa"))
        inv.setItem(48, item(Material.ARROW, "§7Geri dön"))
        if (pg < pages) inv.setItem(53, item(Material.ARROW, "§eSonraki sayfa"))
        inv.setItem(49, item(Material.BARRIER, "§cKapat"))
        filler(inv)
    }

    override fun click(e: InventoryClickEvent) {
        super.click(e)
        val p = e.whoClicked as? Player ?: return
        val page = Menu.pageOf(e)
        when (e.rawSlot) {
            45 -> open(p, page - 1)
            53 -> open(p, page + 1)
            48 -> ShopMenu(plugin).open(p)
            49 -> p.closeInventory()
            else -> {
                val idx = slots.indexOf(e.rawSlot)
                if (idx < 0) return
                val list = entries().values.toList()
                val def = list.drop((page - 1) * slots.size).getOrNull(idx) ?: return
                buyOrEquip(p, def)
                open(p, page)
            }
        }
    }

    private fun buyOrEquip(p: Player, def: ShopEntry) {
        val M = plugin.messages
        if (def.permission.isNotBlank() && !p.hasPermission(def.permission)) {
            M.send(p, "shop.no-permission", mapOf("item" to def.name)); deny(p); return
        }
        if (plugin.cosmetics.owns(p.uniqueId, type, def.id) || def.price <= 0) {
            if (plugin.cosmetics.equip(p.uniqueId, type, def.id, free = true)) {
                M.send(p, "shop.equipped", mapOf("item" to def.name)); confirm(p)
            }
            return
        }
        val st = plugin.stats.load(p.uniqueId)
        if (st.coins < def.price) {
            M.send(p, "shop.no-coins", mapOf("price" to def.price)); deny(p); return
        }
        plugin.stats.addCoins(p.uniqueId, -def.price)
        plugin.cosmetics.give(p.uniqueId, type, def.id)
        plugin.cosmetics.equip(p.uniqueId, type, def.id, free = true)
        M.send(p, "shop.bought", mapOf("item" to def.name, "price" to def.price)); confirm(p)
    }

    private fun confirm(p: Player) = p.playSound(p.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
    private fun deny(p: Player) = p.playSound(p.location, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f)
}

class CageShopMenu(plugin: EnsPillarsPlugin) :
    CosmeticShopMenu(plugin, "§8§lKafesler", CosmeticsService.CAGE) {
    override fun entries() = plugin.shopCatalog.cages
    override fun selectedId(viewer: Player) =
        plugin.cosmetics.selected(viewer.uniqueId).lowercase()
    override fun isSelected(viewer: Player, def: dev.ensisdev.enspillars.shop.ShopEntry) =
        super.isSelected(viewer, def) || (!plugin.cosmetics.hasCageSelection(viewer.uniqueId) && def.id == "default")
}

class KillMessageShopMenu(plugin: EnsPillarsPlugin) :
    CosmeticShopMenu(plugin, "§8§lKill Mesajları", CosmeticsService.KILLMESSAGE) {
    override fun entries() = plugin.shopCatalog.killMessages
    override fun selectedId(viewer: Player) = plugin.cosmetics.selectedKillMessage(viewer.uniqueId)
}

class DeathCryShopMenu(plugin: EnsPillarsPlugin) :
    CosmeticShopMenu(plugin, "§8§lÖlüm Sesleri", CosmeticsService.DEATHCRY) {
    override fun entries() = plugin.shopCatalog.deathCries
    override fun selectedId(viewer: Player) = plugin.cosmetics.selectedDeathCry(viewer.uniqueId)
}

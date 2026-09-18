package dev.ensisdev.enspillars.listener
import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
class GameListener(private val plugin:EnsPillarsPlugin):Listener{
 @EventHandler fun breakBlock(e:BlockBreakEvent){plugin.arenas.engineFor(e.player.uniqueId)?.let{if(!it.handleBreak(e.player,e.block))e.isCancelled=true else { it.captureBlock(e.block); plugin.replay.event(it.arenaIdForReplay(), "BLOCK_BREAK", "${e.player.uniqueId}|${e.block.x}|${e.block.y}|${e.block.z}|${e.block.type.name}") }}}
 @EventHandler fun place(e:BlockPlaceEvent){plugin.arenas.engineFor(e.player.uniqueId)?.let{if(!it.handleBuild(e.player,e.block))e.isCancelled=true else { it.captureBlock(e.blockPlaced); plugin.replay.event(it.arenaIdForReplay(), "BLOCK_PLACE", "${e.player.uniqueId}|${e.blockPlaced.x}|${e.blockPlaced.y}|${e.blockPlaced.z}|${e.blockPlaced.type.name}") }}}
 @EventHandler fun chat(e:AsyncPlayerChatEvent){
  val en=plugin.arenas.engineFor(e.player.uniqueId)
  en?.let{plugin.server.scheduler.runTask(plugin,Runnable{plugin.replay.event(it.arenaIdForReplay(),"CHAT","${e.player.uniqueId}|${e.message}")})}
  if(en==null||!plugin.config.getBoolean("chat.per-arena-chat",true))return
  e.isCancelled=true
  val formatted=en.chatFormat(e.player,e.message)
  val targets=en.chatRecipients(e.player.uniqueId)
  plugin.server.scheduler.runTask(plugin,Runnable{targets.forEach{it.sendMessage(formatted)}})
 }
 @EventHandler fun damage(e:EntityDamageEvent){val p=e.entity as? Player?:return;val en=plugin.arenas.engineFor(p.uniqueId)?:return;if(!en.isAlive(p.uniqueId)||!en.combatAllowed())e.isCancelled=true;if(en.isPreGame(p.uniqueId)&&plugin.config.getBoolean("pregame-protection.damage",true))e.isCancelled=true}
 @EventHandler fun damageBy(e:EntityDamageByEntityEvent){val v=e.entity as? Player?:return;val en=plugin.arenas.engineFor(v.uniqueId)?:return;val a=(e.damager as? Player)?:((e.damager as? org.bukkit.entity.Projectile)?.shooter as? Player);if(a==null){if(!en.combatAllowed()||!en.isAlive(v.uniqueId))e.isCancelled=true;return};if(!en.sameGame(a.uniqueId)||!en.isAlive(v.uniqueId)||!en.isAlive(a.uniqueId)||!en.combatAllowed())e.isCancelled=true else plugin.replay.event(en.arenaIdForReplay(), "HIT", "${a.uniqueId}|${v.uniqueId}|${"%.3f".format(e.finalDamage)}") }
 @EventHandler fun death(e:PlayerDeathEvent){plugin.arenas.engineFor(e.entity.uniqueId)?.onDeath(e)}
 @EventHandler fun respawn(e:PlayerRespawnEvent){plugin.arenas.engineFor(e.player.uniqueId)?.spectatorLocation()?.let{e.respawnLocation=it}}
 @EventHandler fun move(e:PlayerMoveEvent){plugin.arenas.engineFor(e.player.uniqueId)?.let{en->if(en.movementLocked(e.player.uniqueId)&&e.to!=null&&(e.to!!.x!=e.from.x||e.to!!.z!=e.from.z)){e.isCancelled=true};en.handleMove(e.player)}}
 /** Kafes/countdown kilidinde ender incisi ve chorus ile kaçışı engelle. */
 @EventHandler fun teleport(e:PlayerTeleportEvent){plugin.arenas.engineFor(e.player.uniqueId)?.let{en->if(en.movementLocked(e.player.uniqueId)&&e.cause in setOf(PlayerTeleportEvent.TeleportCause.ENDER_PEARL,PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT))e.isCancelled=true}}
 @EventHandler fun join(e:PlayerJoinEvent){runCatching{plugin.snapshots.restore(e.player)};if(plugin.config.getBoolean("general.auto-join",false)&&e.player.hasPermission("enspillars.use")){plugin.server.scheduler.runTaskLater(plugin,Runnable{runCatching{plugin.arenas.autojoin(e.player)}},20L)}}
 @EventHandler fun quit(e:PlayerQuitEvent){plugin.setupMode.handleQuit(e.player);plugin.arenas.leave(e.player);plugin.stats.saveAndEvict(e.player.uniqueId);plugin.cosmetics.evict(e.player.uniqueId)}
 @EventHandler fun interact(e:PlayerInteractEvent){if(e.action==org.bukkit.event.block.Action.PHYSICAL)return;if(plugin.setupMode.inSetup(e.player.uniqueId)){runCatching{plugin.setupMode.handleInteract(e)};return};runCatching{plugin.hotbar.use(e)}}
 @EventHandler fun drop(e:PlayerDropItemEvent){plugin.arenas.engineFor(e.player.uniqueId)?.let{if(it.isPreGame(e.player.uniqueId)&&plugin.config.getBoolean("pregame-protection.item-drop",true))e.isCancelled=true}}
 @EventHandler fun pickup(e:EntityPickupItemEvent){val p=e.entity as? Player?:return;plugin.arenas.engineFor(p.uniqueId)?.let{if(it.isPreGame(p.uniqueId)&&plugin.config.getBoolean("pregame-protection.item-pickup",true))e.isCancelled=true}}
 @EventHandler fun hunger(e:FoodLevelChangeEvent){val p=e.entity as? Player?:return;plugin.arenas.engineFor(p.uniqueId)?.let{if(it.isPreGame(p.uniqueId)&&plugin.config.getBoolean("pregame-protection.hunger",true))e.isCancelled=true}}
 @EventHandler fun portal(e:PlayerPortalEvent){plugin.arenas.engineFor(e.player.uniqueId)?.let{if(it.isInGame(e.player.uniqueId)&&plugin.config.getBoolean("pregame-protection.portal-travel",true))e.isCancelled=true}}
 @EventHandler fun click(e:InventoryClickEvent){dev.ensisdev.enspillars.gui.Menu.dispatch(e)}
 @EventHandler fun explode(e:EntityExplodeEvent){val w=e.location.world?:return;val target=plugin.arenas.all().firstNotNullOfOrNull{plugin.arenas.engine(it.id)?.takeIf{en->it.center()?.world?.uid==w.uid}}?:return;e.blockList().toList().forEach(target::captureBlock)}
 @EventHandler fun blockBurn(e:BlockBurnEvent){val b=e.block;val w=b.world;val near=plugin.arenas.all().any{a->val c=a.center();c!=null&&c.world?.uid==w.uid&&(Math.abs(c.x-b.x)+Math.abs(c.z-b.z))<a.borderSize};if(near)e.isCancelled=true}
}

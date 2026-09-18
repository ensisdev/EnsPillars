package dev.ensisdev.enspillars.game

import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.api.GameState
import dev.ensisdev.enspillars.api.events.ArenaItemGiveEvent
import dev.ensisdev.enspillars.api.events.ArenaJoinEvent
import dev.ensisdev.enspillars.api.events.ArenaQuitEvent
import dev.ensisdev.enspillars.api.events.ArenaStateChangeEvent
import dev.ensisdev.enspillars.api.events.PlayerEliminationEvent
import dev.ensisdev.enspillars.api.events.WinEvent
import dev.ensisdev.enspillars.modifier.ModifierService
import dev.ensisdev.enspillars.vote.VoteService
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.LinkedHashMap
import kotlin.math.max

class GameEngine(private val plugin:EnsPillarsPlugin,private val arena:Arena){
 private var task:BukkitTask?=null; private var countdown=0; private var elapsed=0; private var lootCountdown=arena.itemDelaySeconds; private var celebration=0; private var celebrationLoc:Location?=null; private val fragile=LinkedHashMap<String,Int>(); private var randomFilled=0; private var mode=arena.defaultGameMode.uppercase(); private var map=arena.defaultMapMode.uppercase(); private val players=linkedMapOf<UUID,ArenaPlayer>(); private val spectators=linkedSetOf<UUID>(); private val originalBlocks=LinkedHashMap<String,Pair<Material,org.bukkit.block.data.BlockData>>(); private var border:WorldBorder?=null; private var previousBorder:Triple<Double,Pair<Double,Double>,Double>?=null; val vote=VoteService(this); private val modifier=ModifierService(plugin)
 fun playerCount() = players.size
 fun state() = arena.state
 fun countdown() = countdown
 fun aliveCount() = players.values.count { !it.eliminated }
 private fun call(e:org.bukkit.event.Event){runCatching{plugin.server.pluginManager.callEvent(e)}.onFailure{plugin.logger.warning("Event hatası: ${it.message}")}}
 private fun transition(to:GameState):Boolean{val from=arena.state;if(!arena.tryTransition(to))return false;call(ArenaStateChangeEvent(arena,from,to));return true}
 private fun force(to:GameState){val from=arena.state;arena.forceState(to);if(from!=to)call(ArenaStateChangeEvent(arena,from,to))}
 fun join(p:Player):Boolean{
  val je=ArenaJoinEvent(arena,p);call(je);if(je.isCancelled)return false
  if(!arena.addPlayer(p.uniqueId))return false;players[p.uniqueId]=ArenaPlayer(p.uniqueId);plugin.cosmetics.load(p.uniqueId);plugin.snapshots.save(p);prepare(p);plugin.hotbar.give(p,"lobby");plugin.boards.show(p,boardLines(p));plugin.replay.event(arena.id,"JOIN",p.uniqueId.toString());if(players.size>=arena.minPlayers)startCountdown();return true
 }
 fun leave(u:UUID){
  if(spectators.remove(u)){
   plugin.server.getPlayer(u)?.let{p->plugin.hotbar.cleanup(p);if(plugin.isEnabled){plugin.arenas.endLocation(arena)?.let(p::teleport)}}
   return
  }
  val wasLive=arena.state in setOf(GameState.ACTIVE,GameState.FINAL)
  arena.removePlayer(u);players.remove(u);vote.remove(u)
  plugin.server.getPlayer(u)?.let{p->call(ArenaQuitEvent(arena,p));plugin.snapshots.restore(p);plugin.hotbar.cleanup(p);plugin.boards.hide(p);plugin.bossbars.hide(p);if(plugin.isEnabled){plugin.arenas.endLocation(arena)?.let(p::teleport)}}
  if(wasLive)plugin.stats.recordLoss(u)
  if(players.isEmpty())resetNow() else if(arena.state in setOf(GameState.STARTING,GameState.PRE_GAME,GameState.CAGED)&&players.size<arena.minPlayers)toWaiting() else checkWinner()
 }
 fun onQuit(u:UUID){leave(u)}
 /** Yetkili zorla başlatma: beklemeyi 3 saniyeye indirir. */
 fun forceStart():Boolean{
  if(arena.state==GameState.WAITING){
   if(players.size<arena.minPlayers||arena.spawnCount()<players.size)return false
   startCountdown();countdown=3;return task!=null
  }
  if(arena.state==GameState.STARTING){countdown=minOf(countdown,3);return true}
  return false
 }
 private fun startCountdown(){if(task!=null||arena.state!=GameState.WAITING)return;if(arena.spawnCount()<players.size){announce("&cArena için yeterli spawn yok (${players.size}/${arena.spawnCount()}). Kurulum bekleniyor...");plugin.server.scheduler.runTaskLater(plugin,Runnable{if(arena.state==GameState.WAITING&&players.size>=arena.minPlayers)startCountdown()},100L);return};countdown=arena.startingCountdown;if(!transition(GameState.STARTING))return;task=plugin.server.scheduler.runTaskTimer(plugin,Runnable{tick()},0L,20L)}
 private fun tick(){refreshBoards();when(arena.state){GameState.STARTING->{if(players.size<arena.minPlayers){toWaiting();return};if(countdown<=0){if(!transition(GameState.PRE_GAME))return;countdown=arena.preGameTime;return};announce("&eOyun &b$countdown &esaniye içinde başlayacak!");countdown--};GameState.PRE_GAME->{if(players.size<arena.minPlayers){toWaiting();return};if(countdown<=0){cage();return};countdown--};GameState.CAGED->{if(countdown<=0){release();return};countdown--};GameState.ACTIVE->{elapsed++;lootCountdown--;plugin.stats.tickPlaytime(players.keys);modifier.tick(this);applyGameModeTick();updateBossBars();if(lootCountdown<=0){giveLoot();lootCountdown=arena.itemDelaySeconds};if(elapsed>=arena.durationSeconds){finishDraw();return};checkWinner()};GameState.FINAL->checkWinner();GameState.ENDING->{if(celebration>0){celebration--;celebrate();refreshBoards();return};resetNow()};else->Unit}}
 private fun applyVoteModes(){if(!arena.voteEnabled)return;mode=vote.winningGame(arena.defaultGameMode).uppercase();map=vote.winningMap(arena.defaultMapMode).uppercase()}
 private fun applyGameModeTick(){when(mode){ "SHUFFLE" -> { val iv=plugin.config.getInt("game-modes.shuffle.interval-seconds",20).coerceAtLeast(1); if(elapsed>0&&elapsed%iv==0) players.values.filterNot{it.eliminated}.mapNotNull{plugin.server.getPlayer(it.uuid)}.forEach{modifier.shuffleInventory(it)} }; "SWAP" -> { val iv=plugin.config.getInt("game-modes.swap.interval-seconds",30).coerceAtLeast(1); if(elapsed>0&&elapsed%iv==0) swapAlivePositions() } }}
 private fun cage(){
  applyVoteModes()
  if(arena.center()==null){announce("&cArena merkezi bulunamadı, oyun iptal.");plugin.logger.warning("Arena ${arena.id}: center yok, oyun iptal edildi.");toWaiting();return}
  if(!transition(GameState.CAGED))return;countdown=3
  players.values.filterNot{it.eliminated}.forEachIndexed{idx,ap->plugin.server.getPlayer(ap.uuid)?.let{p->arena.spawn(idx)?.let{loc->p.teleport(loc);createCage(loc,ap.uuid)};p.inventory.clear();plugin.hotbar.give(p,"game");p.health=p.maxHealth;p.foodLevel=20;p.fireTicks=0}}
  plugin.replay.start(arena.id,players.keys)
  plugin.logger.info("Maç başladı: arena=${arena.id} oyuncular=${players.size} mod=$mode/$map")
  announce("&6Hazır! &fKafesler açılıyor...")
 }
 private fun release(){players.values.filterNot{it.eliminated}.forEach{ap->plugin.server.getPlayer(ap.uuid)?.let{p->removeCage(p.location);p.gameMode=GameMode.SURVIVAL;plugin.messages.title(p,"titles.game-start-h","titles.game-start-f");showBossBar(p);if(plugin.config.getBoolean("display.elder-guardian-effect",true))runCatching{p.playSound(p.location,Sound.ENTITY_ELDER_GUARDIAN_CURSE,1f,1f)};p.sendTitle("§bPILLARS","§7Bol şans!",5,20,5)}};setupBorder();if(!transition(GameState.ACTIVE))return;elapsed=0;lootCountdown=arena.itemDelaySeconds;announce("&aOyun başladı! &eMod: &f$mode &7| &eHarita: &f$map")}
 private fun boardLines(p:Player):List<String>{
  val st=plugin.stats.load(p.uniqueId)
  val time=when(arena.state){
   GameState.STARTING,GameState.PRE_GAME,GameState.CAGED->countdown.toString()
   GameState.ACTIVE->maxOf(0,arena.durationSeconds-elapsed).toString()
   else->""
  }
  return plugin.boards.render(arena.state.name,mapOf(
   "player" to p.name,"players" to players.size.toString(),"max" to arena.maxPlayers.toString(),
   "time" to time,"alive" to aliveCount().toString(),
   "kills" to (players[p.uniqueId]?.kills?.toString()?:"0"),
   "wins" to st.wins.toString(),"points" to st.points.toString(),"coins" to st.coins.toString(),
   "mode" to "$mode/$map"))
 }
 private fun refreshBoards(){players.keys.mapNotNull(plugin.server::getPlayer).forEach{plugin.boards.update(it,boardLines(it))}}
 private fun bossText() = plugin.messages.get("bossbar.item",mapOf("time" to lootCountdown))
 private fun showBossBar(p:Player){plugin.bossbars.show(p,bossText(),lootCountdown.toDouble()/arena.itemDelaySeconds.coerceAtLeast(1))}
 private fun updateBossBars(){players.values.filterNot{it.eliminated}.mapNotNull{plugin.server.getPlayer(it.uuid)}.forEach{plugin.bossbars.update(it,bossText(),lootCountdown.toDouble()/arena.itemDelaySeconds.coerceAtLeast(1))}}
 private fun giveLoot(){
  val alive=players.values.filterNot{it.eliminated}
  val targets=if(!arena.lootPerPlayer)listOfNotNull(alive.randomOrNull())else alive
  targets.forEach{ap->plugin.server.getPlayer(ap.uuid)?.let{p->
   val (chosen,stack)=plugin.loot.roll()?:return@let
   val ev=ArenaItemGiveEvent(arena,p,mutableListOf(stack),mode,false);call(ev)
   if(ev.isCancelled)return@let
   ev.items.forEach{plugin.loot.give(p,it)}
   if(ev.items.isNotEmpty())p.sendActionBar("§eRastgele eşya: §f${chosen.material.name}")
   plugin.replay.event(arena.id,"LOOT","${p.uniqueId}|${chosen.material.name}")
  }}
 }
 fun onDeath(e:PlayerDeathEvent){if(arena.state !in setOf(GameState.ACTIVE,GameState.FINAL))return;e.deathMessage=null;if(arena.keepInventory){e.keepInventory=true;e.drops.clear();e.droppedExp=0};val p=e.entity;val k=p.killer;plugin.server.scheduler.runTask(plugin,Runnable{eliminate(p,k)})}
 private fun eliminate(p:Player,k:Player?){if(arena.state==GameState.ENDING)return;val ap=players[p.uniqueId]?:return;if(ap.eliminated)return;ap.eliminated=true;ap.deaths++;k?.let{players[it.uniqueId]?.apply{kills++;points+=1};rewardCommands("kill",it)};plugin.stats.recordDeath(p.uniqueId,k?.uniqueId);val cause=p.lastDamageCause?.cause;call(PlayerEliminationEvent(arena,p,k,cause));p.gameMode=GameMode.SPECTATOR;plugin.hotbar.give(p,"spectate");plugin.messages.title(p,"titles.elimination-h","titles.elimination-f",mapOf("player" to p.name,"killer" to (k?.name?:"?")));plugin.bossbars.hide(p);plugin.deathCries.play(players.keys.mapNotNull(plugin.server::getPlayer),p);plugin.replay.event(arena.id,"ELIMINATION","${p.uniqueId}|${k?.uniqueId}");announceKill(p,k,cause);checkWinner()}
 /** Faz 2'de kill-message şablonlarıyla değişecek. */
 fun announceKill(p:Player,k:Player?,cause:org.bukkit.event.entity.EntityDamageEvent.DamageCause?){announce(plugin.killMessages.format(k,p,cause))}
 private fun checkWinner(){if(arena.state !in setOf(GameState.ACTIVE,GameState.FINAL))return;val alive=players.values.filterNot{it.eliminated};if(alive.size<=1){if(arena.state==GameState.ACTIVE&&!transition(GameState.FINAL))return;val w=alive.firstOrNull()?.let{plugin.server.getPlayer(it.uuid)};finish(w)}else if(alive.size==2&&arena.state==GameState.ACTIVE){if(transition(GameState.FINAL)){announce("&cSon iki oyuncu! &eFinal başladı!");alive.mapNotNull{plugin.server.getPlayer(it.uuid)}.forEach{plugin.messages.title(it,"titles.final-h","titles.final-f")}}}}
 private fun finish(w:Player?){if(arena.state!=GameState.FINAL&&!transition(GameState.FINAL))return;celebration=plugin.config.getInt("win-celebration.duration-seconds",5).coerceIn(0,60);celebrationLoc=w?.location?.clone()?:arena.center();if(w!=null){players[w.uniqueId]?.let{it.points+=3};plugin.stats.recordWin(w.uniqueId);rewardCommands("win",w);plugin.hotbar.give(w,"winner");players.keys.mapNotNull(plugin.server::getPlayer).forEach{plugin.messages.title(it,"titles.win-h","titles.win-f",mapOf("player" to w.name))};announce("&6🏆 &f${w.name} &6kazandı!")}else{players.keys.forEach{plugin.stats.recordDraw(it);plugin.server.getPlayer(it)?.let{p->rewardCommands("draw",p);plugin.messages.title(p,"titles.draw-h","titles.draw-f")}}};plugin.logger.info("Maç bitti: arena=${arena.id} kazanan=${w?.name?:"BERABERE"}");call(WinEvent(arena,w));plugin.replay.event(arena.id,"FINISH",w?.uniqueId?.toString()?:("DRAW"));transition(GameState.ENDING)}
 /** Kutlama fazı: havai fişek + parçacık + şimşek (hasarsız). */
 private fun celebrate(){
  val loc=celebrationLoc?.clone()?:return;val w=loc.world?:return
  if(plugin.config.getBoolean("win-celebration.fireworks.enabled",true)){
   val count=plugin.config.getInt("win-celebration.fireworks.count",2).coerceIn(0,10)
   val radius=plugin.config.getDouble("win-celebration.fireworks.radius",3.0).coerceIn(0.0,16.0)
   val colors=plugin.config.getStringList("win-celebration.fireworks-colors").mapNotNull{fireworkColor(it)}.ifEmpty{listOf(org.bukkit.Color.RED,org.bukkit.Color.YELLOW)}
   repeat(count+1){
    runCatching{
     val at=loc.clone().add((Math.random()-.5)*2*radius,2.0,(Math.random()-.5)*2*radius)
     val fw=w.spawn(at,org.bukkit.entity.Firework::class.java)
     val meta=fw.fireworkMeta
     meta.addEffect(org.bukkit.FireworkEffect.builder().withColor(colors.random()).with(org.bukkit.FireworkEffect.Type.BALL).flicker(true).trail(true).build())
     meta.power=1;fw.fireworkMeta=meta
    }
   }
  }
  if(plugin.config.getBoolean("win-celebration.particles.enabled",true)){
   runCatching{
    val type=dev.ensisdev.enspillars.platform.VersionHelper.particle(plugin.config.getString("win-celebration.particles.type","HAPPY_VILLAGER")?:"HAPPY_VILLAGER")
    val count=plugin.config.getInt("win-celebration.particles.count",30).coerceIn(0,200)
    w.spawnParticle(type,loc.clone().add(0.0,1.0,0.0),count,0.8,1.0,0.8)
   }
  }
  if(plugin.config.getBoolean("win-celebration.lightning.enabled",true)){
   val chance=plugin.config.getInt("win-celebration.lightning.chance",33).coerceIn(0,100)
   if(java.util.concurrent.ThreadLocalRandom.current().nextInt(100)<chance)runCatching{w.strikeLightningEffect(loc)}
  }
 }
 private fun fireworkColor(name:String)=when(name.uppercase()){
  "RED"->org.bukkit.Color.RED;"YELLOW"->org.bukkit.Color.YELLOW;"LIME"->org.bukkit.Color.LIME;"AQUA"->org.bukkit.Color.AQUA;
  "FUCHSIA"->org.bukkit.Color.FUCHSIA;"WHITE"->org.bukkit.Color.WHITE;"GOLD"->org.bukkit.Color.ORANGE;"BLUE"->org.bukkit.Color.BLUE;
  "GREEN"->org.bukkit.Color.GREEN;"PURPLE"->org.bukkit.Color.PURPLE;else->null
 }
 /** rewards.<tip>.commands listesini konsoldan çalıştırır (%player% destekli). */
 private fun rewardCommands(type:String,p:Player?){plugin.config.getStringList("rewards.$type-commands").forEach{cmd->val c=cmd.replace("%player%",p?.name?:"");if(c.isBlank())return@forEach;runCatching{plugin.server.dispatchCommand(plugin.server.consoleSender,c)}.onFailure{plugin.logger.warning("Ödül komutu çalışmadı: $c")}}}
 private fun finishDraw(){if(arena.state in setOf(GameState.ACTIVE,GameState.FINAL)){players.keys.forEach{plugin.stats.recordDraw(it)};announce("&eSüre doldu! Oyun berabere bitti.");celebration=plugin.config.getInt("win-celebration.duration-seconds",5).coerceIn(0,60);celebrationLoc=arena.center();force(GameState.ENDING)}}
 private fun resetNow(){task?.cancel();task=null;restoreBlocks();restoreBorder();cleanupArenaEntities();plugin.replay.stop(arena.id);val online=plugin.isEnabled;players.keys.toList().forEach{plugin.server.getPlayer(it)?.let{p->plugin.snapshots.restore(p);plugin.hotbar.cleanup(p);plugin.boards.hide(p);plugin.bossbars.hide(p);p.fallDistance=0f;if(online)plugin.arenas.endLocation(arena)?.let(p::teleport)}};spectators.toList().forEach{plugin.server.getPlayer(it)?.let{p->plugin.hotbar.cleanup(p);if(online)plugin.arenas.endLocation(arena)?.let(p::teleport)}};players.clear();spectators.clear();fragile.clear();randomFilled=0;elapsed=0;lootCountdown=arena.itemDelaySeconds;force(if(arena.enabled)GameState.WAITING else GameState.DISABLED);vote.clear()}
 private fun toWaiting(){task?.cancel();task=null;restoreBlocks();restoreBorder();cleanupArenaEntities();val online=plugin.isEnabled;players.keys.toList().forEach{plugin.server.getPlayer(it)?.let{p->plugin.snapshots.restore(p);plugin.hotbar.cleanup(p);plugin.boards.hide(p);plugin.bossbars.hide(p);p.fallDistance=0f;if(online)plugin.arenas.endLocation(arena)?.let(p::teleport)}};spectators.toList().forEach{plugin.server.getPlayer(it)?.let{p->plugin.hotbar.cleanup(p);if(online)plugin.arenas.endLocation(arena)?.let(p::teleport)}};players.keys.forEach{arena.removePlayer(it)};players.clear();spectators.clear();fragile.clear();randomFilled=0;force(if(arena.enabled)GameState.WAITING else GameState.DISABLED);vote.clear()}
 private fun prepare(p:Player){
  p.gameMode=GameMode.ADVENTURE;p.inventory.clear();p.inventory.setArmorContents(null);p.inventory.setItemInOffHand(null)
  p.activePotionEffects.forEach{runCatching{p.removePotionEffect(it.type)}}
  p.totalExperience=0;p.level=0;p.exp=0f
  p.allowFlight=false;p.isFlying=false
  p.fireTicks=0;p.fallDistance=0f;p.health=p.maxHealth;p.foodLevel=20;p.saturation=20f
  runCatching{if(!arena.naturalRegeneration)p.saturatedRegenRate=Int.MAX_VALUE else p.saturatedRegenRate=10}
 }
 private fun announce(m:String){players.keys.mapNotNull(plugin.server::getPlayer).forEach{it.sendMessage(ChatColor.translateAlternateColorCodes('&',m))}}
 private fun setupBorder(){val c=arena.center()?:return;val w=c.world?:return;val b=w.worldBorder;previousBorder=Triple(b.size,b.center.x to b.center.z,b.damageAmount);b.setCenter(c.x,c.z);b.size=arena.borderSize;b.damageAmount=0.0;border=b}
 fun shrinkBorder(){if(!arena.borderShrinkEnabled||border==null)return;val progress=(elapsed.toDouble()/arena.borderShrinkSeconds).coerceIn(0.0,1.0);border!!.size=max(arena.borderShrinkFinal,arena.borderSize-(arena.borderSize-arena.borderShrinkFinal)*progress)}
 private fun blockKey(w:String,x:Int,y:Int,z:Int)="$w:$x:$y:$z"
 private fun createCage(l:Location,owner:UUID){val w=l.world?:return;val b=l.block.location;val mat=plugin.cosmetics.material(owner);for(x in -1..1)for(y in 0..3)for(z in -1..1)if(x!=0||z!=0||y==3){val bl=w.getBlockAt(b.blockX+x,b.blockY+y,b.blockZ+z);if(bl.type.isAir){originalBlocks[blockKey(w.name,bl.x,bl.y,bl.z)]=bl.type to bl.blockData.clone();bl.type=mat}}}
 private fun removeCage(l:Location){val w=l.world?:return;val b=l.block.location;for(x in -1..1)for(y in 0..3)for(z in -1..1){val bl=w.getBlockAt(b.blockX+x,b.blockY+y,b.blockZ+z);val key=blockKey(w.name,bl.x,bl.y,bl.z);if(originalBlocks.containsKey(key)){bl.type=Material.AIR}else if(bl.type.name.contains("GLASS"))bl.type=Material.AIR}}
 fun captureBlock(b:Block){val key=blockKey(b.world.name,b.x,b.y,b.z);if(!originalBlocks.containsKey(key))originalBlocks[key]=b.type to b.blockData.clone()}
 fun trackedBlocks()=originalBlocks.size
 /** Manuel rollback: aktif oyunda reddedilir, yoksa takip edilen blokları geri yükler. */
 fun rollbackBlocks():Boolean{
  if(arena.state==GameState.ACTIVE||arena.state==GameState.FINAL)return false
  restoreBlocks()
  return true
 }
 private fun restoreBlocks(){originalBlocks.entries.reversed().forEach{(key,data)->runCatching{val li=key.lastIndexOf(':');if(li<=0)return@forEach;val li2=key.lastIndexOf(':',li-1);if(li2<=0)return@forEach;val li3=key.indexOf(':');if(li3<=0||li3>=li2)return@forEach;val worldName=key.substring(0,li3);val x=key.substring(li3+1,li2).toInt();val y=key.substring(li2+1,li).toInt();val z=key.substring(li+1).toInt();plugin.server.getWorld(worldName)?.getBlockAt(x,y,z)?.apply{type=data.first;blockData=data.second}}.onFailure{plugin.logger.warning("Restore failed for $key: ${it.message}")}};originalBlocks.clear()}
 private fun restoreBorder(){previousBorder?.let{border?.apply{runCatching{size=it.first;setCenter(it.second.first,it.second.second);damageAmount=it.third}}};border=null;previousBorder=null}
 /** Maçtan kalan eşya/TNT/mermi/XP kürelerini arena çevresinden temizler. */
 private fun cleanupArenaEntities(){
  val c=arena.center()?:return;val w=c.world?:return
  val r=(arena.borderSize/2.0+16.0).coerceIn(16.0,256.0)
  runCatching{
   w.getNearbyEntities(c,r,256.0,r){e->
    e is org.bukkit.entity.Item || e is org.bukkit.entity.ExperienceOrb ||
    e is org.bukkit.entity.TNTPrimed || e is org.bukkit.entity.Projectile ||
    e is org.bukkit.entity.FallingBlock
   }.forEach{runCatching{it.remove()}}
  }.onFailure{plugin.logger.warning("Entity temizliği başarısız (${arena.id}): ${it.message}")}
 }
 fun shutdown(){resetNow()}
 fun spectatorLocation()=arena.spectator();fun combatAllowed()=arena.pvp&&arena.state in setOf(GameState.ACTIVE,GameState.FINAL); /** Devam eden maçı izleyici olarak takip et (envanterine dokunulmaz). */
 fun spectate(p:Player):Boolean{
  if(arena.state !in setOf(GameState.ACTIVE,GameState.FINAL))return false
  val point=arena.spectator()?:return false
  if(isInGame(p.uniqueId))return false
  spectators+=p.uniqueId
  p.gameMode=GameMode.SPECTATOR;p.teleport(point)
  plugin.hotbar.give(p,"spectate")
  return true
 }
 fun spectatorIds(): Set<UUID> = spectators.toSet()
 fun sameGame(u:UUID)=players.containsKey(u);fun isAlive(u:UUID)=players[u]?.eliminated==false;fun movementLocked(u:UUID)=players.containsKey(u)&&arena.state in setOf(GameState.STARTING,GameState.PRE_GAME,GameState.CAGED);fun isInGame(u:UUID)=players.containsKey(u)||spectators.contains(u)
 fun isPreGame(u:UUID)=isInGame(u)&&arena.state in setOf(GameState.WAITING,GameState.STARTING,GameState.PRE_GAME,GameState.CAGED)
 /** Sohbet alıcıları: spectator-only açıksa ve gönderen izleyiciyse sadece izleyiciler. */
 fun chatRecipients(sender:UUID):List<Player>{
  val senderSpec=spectators.contains(sender)||players[sender]?.eliminated==true
  val ids=if(plugin.config.getBoolean("chat.spectator-chat",true)&&senderSpec)
   spectators+players.filterValues{it.eliminated}.keys
  else players.keys+spectators
  return ids.mapNotNull(plugin.server::getPlayer)
 }
 fun chatFormat(p:Player,msg:String):String{
  if(!plugin.config.getBoolean("chat.formatting-enabled",true))return "<${p.name}> $msg"
  val st=plugin.stats.load(p.uniqueId)
  val fmt=plugin.config.getString("chat.format","&8• %player% → ")?: "&8• %player% → "
  return ChatColor.translateAlternateColorCodes('&',fmt.replace("%player%",p.name).replace("%points%",st.points.toString()))+msg
 }
 fun handleMove(p:Player){if(!isAlive(p.uniqueId))return;if(arena.state in setOf(GameState.ACTIVE,GameState.FINAL)&&p.location.y<arena.heightMin){p.health=0.0;return};if(arena.state in setOf(GameState.ACTIVE,GameState.FINAL)&&p.location.y>arena.heightMax){val loc=p.location.clone();loc.y=arena.heightMax.toDouble();p.velocity=org.bukkit.util.Vector(0,0,0);p.teleport(loc)}}
 fun handleBuild(p:Player,b:Block)=arena.state==GameState.ACTIVE&&arena.allowBuild&&isAlive(p.uniqueId)
 fun handleBreak(p:Player,b:Block)=arena.state==GameState.ACTIVE&&arena.allowBreak&&isAlive(p.uniqueId)
 fun playerIdsForReplay(): Set<UUID> = players.keys.toSet()
 fun arenaIdForReplay(): String = arena.id
 fun mapMode()=map;fun gameMode()=mode;fun elapsedSeconds()=elapsed
 fun setModes(game:String,mapMode:String){mode=game.uppercase();map=mapMode.uppercase()}
 fun raiseLava(interval:Int){val c=arena.center()?:return;val w=c.world?:return;val step=(elapsed/interval.coerceAtLeast(1)).coerceAtMost(40);val y=c.blockY+step;for(x in (c.blockX-10)..(c.blockX+10))for(z in (c.blockZ-10)..(c.blockZ+10)){val b=w.getBlockAt(x,y,z);if(b.type.isAir){captureBlock(b);b.type=Material.LAVA}}}
 fun processFragile(breakAfter:Int){
  val every=breakAfter.coerceAtLeast(1)
  val seen=HashSet<String>()
  players.values.filterNot{it.eliminated}.mapNotNull{plugin.server.getPlayer(it.uuid)}.forEach{p->
   val b=p.location.clone().subtract(0.0,1.0,0.0).block
   if(!b.type.isSolid||b.type==Material.BEDROCK||b.type==Material.OBSIDIAN)return@forEach
   if(plugin.config.getStringList("blacklisted-blocks").any{it.equals(b.type.name,true)})return@forEach
   val key=blockKey(b.world.name,b.x,b.y,b.z)
   seen+=key
   val stood=(fragile[key]?:0)+1
   if(stood>=every){
    fragile.remove(key);captureBlock(b)
    runCatching{
     b.world.spawnParticle(dev.ensisdev.enspillars.platform.VersionHelper.particle("BLOCK_CRACK"),b.location.clone().add(0.5,0.5,0.5),20,0.3,0.3,0.3,b.blockData)
     b.world.playSound(b.location,Sound.BLOCK_WOOD_BREAK,1f,1f)
    }
    b.type=Material.AIR
   }else{
    fragile[key]=stood
    if(stood%2==0)runCatching{
     b.world.spawnParticle(dev.ensisdev.enspillars.platform.VersionHelper.particle("BLOCK_CRACK"),b.location.clone().add(0.5,1.0,0.5),5,0.2,0.1,0.2,b.blockData)
     b.world.playSound(b.location,Sound.BLOCK_WOOD_HIT,0.6f,1.4f)
    }
   }
  }
  fragile.keys.retainAll(seen)
 }
 /** Ablockalypse: sınır dairesi içinde havaya tick başına 5 rastgele blok. */
 fun fillRandomBlocks(){
  if(randomFilled>=50000)return
  val c=arena.center()?:return;val w=c.world?:return
  val r=(arena.borderSize/2.0).coerceAtMost(64.0)
  val banned=plugin.config.getStringList("blacklisted-blocks").mapNotNull{Material.matchMaterial(it)}.toSet()
  val pool=listOf(Material.STONE,Material.COBBLESTONE,Material.OAK_PLANKS,Material.DIRT,Material.SAND,Material.GRAVEL,Material.MOSSY_COBBLESTONE,Material.BRICKS).filterNot{banned.contains(it)}
  if(pool.isEmpty())return
  val rnd=java.util.concurrent.ThreadLocalRandom.current()
  repeat(5){
   if(randomFilled>=50000)return
   val x=c.blockX+rnd.nextInt(-r.toInt(),r.toInt()+1)
   val z=c.blockZ+rnd.nextInt(-r.toInt(),r.toInt()+1)
   val dx=(x-c.blockX).toDouble();val dz=(z-c.blockZ).toDouble()
   if(dx*dx+dz*dz>r*r)return@repeat
   val y=(c.blockY-10+rnd.nextInt(21)).coerceIn(w.minHeight,w.maxHeight-1)
   val b=w.getBlockAt(x,y,z)
   if(!b.type.isAir)return@repeat
   captureBlock(b);b.type=pool[rnd.nextInt(pool.size)];randomFilled++
  }
 }
 fun tntRain(){val c=arena.center()?:return;val w=c.world?:return;players.values.filterNot{it.eliminated}.forEach{ap->plugin.server.getPlayer(ap.uuid)?.let{p->val e=w.spawnEntity(p.location.clone().add((Math.random()-.5)*8,12.0,(Math.random()-.5)*8),EntityType.PRIMED_TNT);e.velocity=org.bukkit.util.Vector(0.0,-0.1,0.0)}}}
 fun swapAlivePositions(){val ps=players.values.filterNot{it.eliminated}.mapNotNull{plugin.server.getPlayer(it.uuid)};if(ps.size<2)return;val locs=ps.map{it.location.clone()};ps.forEachIndexed{i,p->p.teleport(locs[(i+1)%locs.size])}}
}

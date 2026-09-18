package dev.ensisdev.enspillars.stats
import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StatsService(private val plugin:EnsPillarsPlugin){
 private val cache=ConcurrentHashMap<UUID,PlayerStats>(); private val names=ConcurrentHashMap<UUID,String>(); private val file get()=File(plugin.dataFolder,"players.yml")
 private var autosave:org.bukkit.scheduler.BukkitTask?=null
 private fun useSql() = plugin.dbReady()
 fun load(uuid:UUID):PlayerStats=cache.computeIfAbsent(uuid){
  if(useSql()){dev.ensisdev.enspillars.storage.PlayerTable.load(plugin.db,uuid)?.let{row->if(row.name.isNotEmpty())names[uuid]=row.name;return@computeIfAbsent row.stats} }
  readDisk(uuid)
 }
 private fun readDisk(uuid:UUID):PlayerStats{ return runCatching{val y=YamlConfiguration.loadConfiguration(file);val p=uuid.toString();PlayerStats(y.getInt("$p.games"),y.getInt("$p.wins"),y.getInt("$p.draws"),y.getInt("$p.losses"),y.getInt("$p.kills"),y.getInt("$p.deaths"),y.getInt("$p.coins"),y.getInt("$p.points"),y.getInt("$p.win-streak"),y.getInt("$p.max-win-streak"),y.getLong("$p.play-seconds"),y.getLong("$p.last-game"))}.getOrDefault(PlayerStats()) }
 /** Disk yazımı async yapılır; yazılan değerler çağrı anında kopyalanır (yarış önleme). */
 fun save(uuid:UUID){
  val s=cache[uuid]?.copy()?:return
  val name=plugin.server.getPlayer(uuid)?.name?.also{names[uuid]=it}?:names[uuid] ?: ""
  val sel=if(plugin.cosmeticsReady())plugin.cosmetics.selectedIds(uuid)else Triple("","","")
  if(useSql()){
   plugin.server.scheduler.runTaskAsynchronously(plugin,Runnable{runCatching{dev.ensisdev.enspillars.storage.PlayerTable.save(plugin.db,uuid,name,s,sel.first,sel.second,sel.third);plugin.cosmetics.persistOwned(uuid)}.onFailure{plugin.logger.warning("Stats save failed: ${it.message}")}})
  } else {
   plugin.server.scheduler.runTaskAsynchronously(plugin,Runnable{runCatching{writeEntry(s,uuid.toString())}.onFailure{plugin.logger.warning("Stats save failed: ${it.message}")}})
  }
  plugin.storage.markDirty()
 }
 private fun writeEntry(s:PlayerStats,p:String){val y=YamlConfiguration.loadConfiguration(file);y.set("$p.games",s.games);y.set("$p.wins",s.wins);y.set("$p.draws",s.draws);y.set("$p.losses",s.losses);y.set("$p.kills",s.kills);y.set("$p.deaths",s.deaths);y.set("$p.coins",s.coins);y.set("$p.points",s.points);y.set("$p.win-streak",s.winStreak);y.set("$p.max-win-streak",s.maxWinStreak);y.set("$p.play-seconds",s.playSeconds);y.set("$p.last-game",s.lastGame);y.save(file)}
 fun saveAllSync(){
  if(useSql()){
   cache.toMap().forEach{(uuid,s)->val name=plugin.server.getPlayer(uuid)?.name?:names[uuid]?:runCatching{org.bukkit.Bukkit.getOfflinePlayer(uuid).name?:""}.getOrDefault("");val sel=if(plugin.cosmeticsReady())plugin.cosmetics.selectedIds(uuid)else Triple("","","");runCatching{dev.ensisdev.enspillars.storage.PlayerTable.save(plugin.db,uuid,name,s.copy(),sel.first,sel.second,sel.third)}.onFailure{plugin.logger.warning("Stats save failed: ${it.message}")}}
   cache.keys.forEach{plugin.cosmetics.persistOwnedSync(it)}
  } else {
   cache.toMap().forEach{(uuid,s)->runCatching{writeEntry(s.copy(),uuid.toString())}.onFailure{plugin.logger.warning("Stats save failed: ${it.message}")}}
  }
  plugin.storage.flush()
 }
 fun saveAll(){cache.keys.toList().forEach(::save)}
 /** Kaydet ve bellekten at (quit sonrası sızıntıyı önler; leaderboard diskten doldurulur). */
 fun saveAndEvict(uuid:UUID){save(uuid);cache.remove(uuid)}
 fun startAutosave(){stopAutosave();val seconds=plugin.config.getInt("settings.auto-save-seconds",60).coerceIn(0,3600);if(seconds<=0)return;autosave=plugin.server.scheduler.runTaskTimerAsynchronously(plugin,Runnable{saveAll()},seconds*20L,seconds*20L)}
 fun stopAutosave(){autosave?.cancel();autosave=null}
 fun addCoins(u:UUID,n:Int){load(u).coins+=n;save(u)}; fun addPoints(u:UUID,n:Int){load(u).points+=n;save(u)}
 fun leaderboard(limit:Int=10):List<Map.Entry<UUID,PlayerStats>>{ if(cache.isEmpty()){runCatching{val y=YamlConfiguration.loadConfiguration(file);y.getKeys(false).forEach{k->runCatching{cache[UUID.fromString(k)]=readDisk(UUID.fromString(k))}}}}
  return cache.entries.sortedByDescending{it.value.points}.take(limit)}
 fun tickPlaytime(uuids: Collection<UUID>){uuids.forEach{val s=load(it);s.playSeconds++;cache[it]=s}}
 fun recordDeath(u:UUID,k:UUID?){val s=load(u);s.deaths++;s.lastGame=System.currentTimeMillis();save(u);if(k!=null){val ks=load(k);ks.kills++;ks.coins+=plugin.config.getInt("rewards.kill.coins",4);ks.points+=plugin.config.getInt("rewards.kill.points",5);save(k)}}
 fun recordWin(u:UUID){val s=load(u);s.games++;s.wins++;s.winStreak++;s.maxWinStreak=maxOf(s.maxWinStreak,s.winStreak);s.coins+=plugin.config.getInt("rewards.win.coins",12);s.points+=plugin.config.getInt("rewards.win.points",8);s.lastGame=System.currentTimeMillis();save(u)}
 fun recordDraw(u:UUID){val s=load(u);s.games++;s.draws++;s.winStreak=0;s.coins+=plugin.config.getInt("rewards.draw.coins",1);s.points+=plugin.config.getInt("rewards.draw.points",1);s.lastGame=System.currentTimeMillis();save(u)}
 /** Maç ortasında oyundan ayrılma/çıkma: mağlubiyet sayılır (quit-exploit önleme). */
 fun recordLoss(u:UUID){val s=load(u);s.games++;s.losses++;s.winStreak=0;s.lastGame=System.currentTimeMillis();save(u)}
}

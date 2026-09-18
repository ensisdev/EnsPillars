package dev.ensisdev.enspillars.cosmetic
import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Kozmetikler: kafes + killmessage + deathcry seçimleri ve "type:id"
 * sahiplik seti. Backend: YAML (cosmetics.yml) veya SQL.
 */
class CosmeticsService(private val plugin:EnsPillarsPlugin){
 companion object { const val CAGE = "cage"; const val KILLMESSAGE = "killmessage"; const val DEATHCRY = "deathcry" }
 private val cage=ConcurrentHashMap<UUID,String>()
 private val killMessage=ConcurrentHashMap<UUID,String>()
 private val deathCry=ConcurrentHashMap<UUID,String>()
 private val owned=ConcurrentHashMap<UUID,MutableSet<String>>()
 private val file get()=File(plugin.dataFolder,"cosmetics.yml")
 private val defaultCage get()=plugin.config.getString("cosmetics.default-cage","WHITE_STAINED_GLASS")?:"WHITE_STAINED_GLASS"
 private fun useSql() = plugin.dbReady()

 fun selected(u:UUID)=cage[u]?:defaultCage
 fun selectedKillMessage(u:UUID)=killMessage[u]?:""
 fun selectedDeathCry(u:UUID)=deathCry[u]?:""
 fun selectedIds(u:UUID)=Triple(selected(u),selectedKillMessage(u),selectedDeathCry(u))

 fun load(u:UUID){
  if(useSql()){
   dev.ensisdev.enspillars.storage.PlayerTable.load(plugin.db,u)?.let{row->
    if(row.cage.isNotEmpty())cage[u]=row.cage
    if(row.killMessage.isNotEmpty())killMessage[u]=row.killMessage
    if(row.deathCry.isNotEmpty())deathCry[u]=row.deathCry
   }
   owned[u]=ConcurrentHashMap.newKeySet<String>().also{set->
    set += dev.ensisdev.enspillars.storage.OwnedTable.load(plugin.db,u,CAGE)
     .map{"$CAGE:$it"} + dev.ensisdev.enspillars.storage.OwnedTable.load(plugin.db,u,KILLMESSAGE)
     .map{"$KILLMESSAGE:$it"} + dev.ensisdev.enspillars.storage.OwnedTable.load(plugin.db,u,DEATHCRY)
     .map{"$DEATHCRY:$it"}
   }
   return
  }
  val y=runCatching{YamlConfiguration.loadConfiguration(file)}.getOrNull() ?: return
  y.getString("${u}.cage")?.let{cage[u]=it}
  y.getString("${u}.killmessage")?.let{killMessage[u]=it}
  y.getString("${u}.deathcry")?.let{deathCry[u]=it}
  owned[u]=y.getStringList("${u}.owned").toMutableSet()
 }

 /** Kafes seçimi (materyal doğrulamalı). */
 fun select(u:UUID,id:String):Boolean{val mat=Material.matchMaterial(id)?:return false;cage[u]=mat.name;persist(u);return true}

 /** Shop üzerinden takma: sahiplik (veya ücretsiz) kontrolü yapar. */
 fun equip(u:UUID,type:String,id:String,free:Boolean):Boolean{
  if(!free && !owns(u,type,id))return false
  when(type){
   CAGE->{
    val mat=Material.matchMaterial(id)
    val catalogId=runCatching{plugin.shopCatalog.cages.keys.firstOrNull{it.equals(id,true)}}.getOrNull()
    if(mat==null&&catalogId==null)return false
    cage[u]=(mat?.name?:catalogId!!.uppercase())
   }
   KILLMESSAGE->killMessage[u]=id; DEATHCRY->deathCry[u]=id; else->return false}
  persist(u);return true
 }
 fun unequip(u:UUID,type:String):Boolean{
  when(type){CAGE->cage.remove(u);KILLMESSAGE->killMessage.remove(u);DEATHCRY->deathCry.remove(u);else->return false}
  persist(u);return true
 }
 fun owns(u:UUID,type:String,id:String)=owned[u]?.contains("$type:$id")==true
 fun give(u:UUID,type:String,id:String){owned.computeIfAbsent(u){ConcurrentHashMap.newKeySet()}+="$type:$id";persist(u)}
 fun take(u:UUID,type:String,id:String){owned[u]?.remove("$type:$id");persist(u)}

 fun material(u:UUID):Material{
  val v=selected(u)
  Material.matchMaterial(v)?.let{return it}
  return runCatching{plugin.shopCatalog.cages[v.lowercase()]?.material?:Material.WHITE_STAINED_GLASS}.getOrDefault(Material.WHITE_STAINED_GLASS)
 }

 fun hasCageSelection(u:UUID)=cage.containsKey(u)

 fun evict(u:UUID){cage.remove(u);killMessage.remove(u);deathCry.remove(u);owned.remove(u)}

 private fun persist(u:UUID){
  if(useSql()){plugin.stats.save(u);persistOwned(u);return}
  plugin.server.scheduler.runTaskAsynchronously(plugin,Runnable{runCatching{
   val y=YamlConfiguration.loadConfiguration(file)
   y.set("${u}.cage",cage[u]);y.set("${u}.killmessage",killMessage[u]);y.set("${u}.deathcry",deathCry[u])
   y.set("${u}.owned",owned[u]?.toList()?:emptyList<String>());y.save(file)
  }})
 }

 /** Sahiplik setini SQL'e yazar (async). saveAllSync içinden senkron çağrılır. */
 fun persistOwned(u:UUID){
  if(!useSql())return
  val set=owned[u]?.toSet()?:emptySet()
  plugin.server.scheduler.runTaskAsynchronously(plugin,Runnable{runCatching{
   dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db,u,CAGE,set.filter{it.startsWith("$CAGE:")}.map{it.removePrefix("$CAGE:")}.toSet())
   dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db,u,KILLMESSAGE,set.filter{it.startsWith("$KILLMESSAGE:")}.map{it.removePrefix("$KILLMESSAGE:")}.toSet())
   dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db,u,DEATHCRY,set.filter{it.startsWith("$DEATHCRY:")}.map{it.removePrefix("$DEATHCRY:")}.toSet())
  }.onFailure{plugin.logger.warning("Owned save failed: ${it.message}")}})
 }

 fun persistOwnedSync(u:UUID){
  if(!useSql())return
  val set=owned[u]?.toSet()?:emptySet()
  runCatching{
   dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db,u,CAGE,set.filter{it.startsWith("$CAGE:")}.map{it.removePrefix("$CAGE:")}.toSet())
   dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db,u,KILLMESSAGE,set.filter{it.startsWith("$KILLMESSAGE:")}.map{it.removePrefix("$KILLMESSAGE:")}.toSet())
   dev.ensisdev.enspillars.storage.OwnedTable.sync(plugin.db,u,DEATHCRY,set.filter{it.startsWith("$DEATHCRY:")}.map{it.removePrefix("$DEATHCRY:")}.toSet())
  }.onFailure{plugin.logger.warning("Owned save failed: ${it.message}")}
 }
}

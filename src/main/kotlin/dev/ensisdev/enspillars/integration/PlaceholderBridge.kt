package dev.ensisdev.enspillars.integration
import dev.ensisdev.enspillars.EnsPillarsPlugin
import org.bukkit.OfflinePlayer
class PlaceholderBridge(private val plugin:EnsPillarsPlugin){
 @Volatile private var hooked=plugin.server.pluginManager.getPlugin("PlaceholderAPI")!=null
 fun refresh(){hooked=plugin.server.pluginManager.getPlugin("PlaceholderAPI")!=null}
 fun isHooked()=hooked
 fun apply(player:OfflinePlayer,text:String):String{if(!hooked)return text;return runCatching{val c=Class.forName("me.clip.placeholderapi.PlaceholderAPI");val m=c.getMethod("setPlaceholders",OfflinePlayer::class.java,String::class.java);m.invoke(null,player,text) as String}.getOrDefault(text)}
 fun format(player:OfflinePlayer,text:String)=apply(player,text)
 fun register(){
  plugin.server.pluginManager.registerEvents(object : org.bukkit.event.Listener {
   @org.bukkit.event.EventHandler fun onPluginEnable(e:org.bukkit.event.server.PluginEnableEvent){
    if(e.plugin.name=="PlaceholderAPI")refresh()
   }
  }, plugin)
 }
}

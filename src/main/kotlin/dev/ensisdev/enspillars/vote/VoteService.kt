package dev.ensisdev.enspillars.vote
import dev.ensisdev.enspillars.game.GameEngine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
class VoteService(private val engine:GameEngine){
 private val game=ConcurrentHashMap<UUID,String>(); private val map=ConcurrentHashMap<UUID,String>()
 private val lastVoteAt=ConcurrentHashMap<UUID,Long>()
 companion object { const val COOLDOWN_MS = 3000L }
 /** Spam koruması: süre dolmadan false döner ve oyu işlemez. */
 fun tryCooldown(u:UUID):Boolean{val now=System.currentTimeMillis();val last=lastVoteAt[u]?:0L;if(now-last<COOLDOWN_MS)return false;lastVoteAt[u]=now;return true}
 fun voteGame(u:UUID,v:String):Boolean{val c=v.trim().uppercase();if(c.isBlank()||c.length>24||!c.matches(Regex("[A-Z0-9_]+")))return false;game[u]=c;return true}
 fun voteMap(u:UUID,v:String):Boolean{val c=v.trim().uppercase();if(c.isBlank()||c.length>24||!c.matches(Regex("[A-Z0-9_]+")))return false;map[u]=c;return true}
 fun remove(u:UUID){game.remove(u);map.remove(u);lastVoteAt.remove(u)}
 fun gameCounts(): Map<String, Int> = game.values.groupingBy{it}.eachCount()
 fun mapCounts(): Map<String, Int> = map.values.groupingBy{it}.eachCount()
 fun myGame(u:UUID)=game[u]
 fun myMap(u:UUID)=map[u]
 fun winningGame(fallback:String)=game.values.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key?:fallback
 fun winningMap(fallback:String)=map.values.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key?:fallback
 fun clear(){game.clear();map.clear()}
}

package dev.ensisdev.enspillars.party
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
class PartyService(private val maxSize: Int = 5){
 private val parties=ConcurrentHashMap<UUID,LinkedHashSet<UUID>>(); private val owner=ConcurrentHashMap<UUID,UUID>()
 private val invites=ConcurrentHashMap<UUID,MutableSet<UUID>>()
 fun create(u:UUID):Boolean{if(owner.containsKey(u))return false;parties[u]=linkedSetOf(u);owner[u]=u;return true}
 fun invite(o:UUID,u:UUID):Boolean{val s=parties[o]?:return false;if(owner[o]!=o)return false;if(s.size>=maxSize.coerceAtLeast(2))return false;if(owner.containsKey(u))return false;invites.computeIfAbsent(u){ConcurrentHashMap.newKeySet()}.add(o);return true}
 fun accept(u:UUID,o:UUID):Boolean{val set=invites[u]?:return false;if(!set.remove(o))return false;val s=parties[o]?:return false;if(s.size>=maxSize.coerceAtLeast(2))return false;if(owner.containsKey(u))return false;s.add(u);owner[u]=o;return true}
 fun decline(u:UUID,o:UUID):Boolean=invites[u]?.remove(o)?:false
 fun leave(u:UUID):Boolean{val o=owner.remove(u)?:return false;val s=parties[o]?:return false;s.remove(u);invites.remove(u);if(o==u||s.isEmpty()){s.toList().forEach{owner.remove(it)};parties.remove(o);invites.forEach{(_,v)->v.remove(o)}};return true}
 fun members(u:UUID)=parties[owner[u]?:u]?.toSet()?:setOf(u)
 fun owner(u:UUID)=owner[u]
 fun pendingInvites(u:UUID): Set<UUID> = invites[u]?.toSet() ?: emptySet()
}

package dev.ensisdev.enspillars.modifier
import dev.ensisdev.enspillars.EnsPillarsPlugin
import dev.ensisdev.enspillars.game.GameEngine
import org.bukkit.Material
import org.bukkit.entity.Player
import kotlin.random.Random

class ModifierService(private val plugin:EnsPillarsPlugin){
 fun tick(engine:GameEngine){ val mode=engine.mapMode(); when(mode){"LAVA_RISE"->{val iv=plugin.config.getInt("map-modes.lava-rise.interval-seconds",8).coerceAtLeast(1);if(engine.elapsedSeconds()>0&&engine.elapsedSeconds()%iv==0) engine.raiseLava(iv)}; "FRAGILE"->{val every=plugin.config.getInt("map-modes.fragile.break-after-seconds",5).coerceAtLeast(1);engine.processFragile(every)}; "TNT_RAIN"->{val iv=plugin.config.getInt("map-modes.tnt-rain.interval-seconds",12).coerceAtLeast(1);if(engine.elapsedSeconds()>0&&engine.elapsedSeconds()%iv==0) engine.tntRain()}; "ABLOCKALYPSE"->engine.fillRandomBlocks(); "UHC"->Unit; "SHRINKING_BORDER"->engine.shrinkBorder(); "NORMAL"->Unit } }
 fun shuffleInventory(p:Player){ val contents=p.inventory.contents.toMutableList(); contents.shuffle(); p.inventory.contents=contents.toTypedArray() }
 fun swapPlayers(engine:GameEngine){ engine.swapAlivePositions() }
}

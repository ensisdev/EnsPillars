package dev.ensisdev.enspillars.game
import java.util.UUID

data class ArenaPlayer(val uuid: UUID, var kills:Int=0, var deaths:Int=0, var eliminated:Boolean=false, var points:Int=0)

package dev.ensisdev.enspillars.stats
import java.util.UUID

data class PlayerStats(var games:Int=0,var wins:Int=0,var draws:Int=0,var losses:Int=0,var kills:Int=0,var deaths:Int=0,var coins:Int=0,var points:Int=0,var winStreak:Int=0,var maxWinStreak:Int=0,var playSeconds:Long=0,var lastGame:Long=0)

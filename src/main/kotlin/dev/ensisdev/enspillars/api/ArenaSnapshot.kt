package dev.ensisdev.enspillars.api
data class ArenaSnapshot(val id: String, val displayName: String, val state: GameState, val players: Int, val maxPlayers: Int, val enabled: Boolean)

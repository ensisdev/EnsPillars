package dev.ensisdev.enspillars.api

object ApiRegistry {
    @Volatile private var instance: EnsPillarsAPI? = null
    fun register(api: EnsPillarsAPI) { if (instance != null) throw IllegalStateException("EnsPillarsAPI already registered"); instance = api }
    fun get(): EnsPillarsAPI? = instance
    fun clear() { instance = null }
}

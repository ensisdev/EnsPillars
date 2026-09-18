package dev.ensisdev.enspillars.storage
import dev.ensisdev.enspillars.EnsPillarsPlugin
class StorageService(private val plugin: EnsPillarsPlugin) {
    @Volatile private var dirty = false
    fun markDirty() { dirty = true }
    fun isDirty() = dirty
    /** Sadece bayrağı temizler; kayıt yapmaz (özyineleme tuzağına düşmemek için). */
    fun flush() { dirty = false }
}

package net.badgersmc.nexus.paper

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatcher that executes on the Paper main thread.
 * If already on the main thread, executes inline (no scheduling overhead).
 * Use via: withContext(plugin.bukkitDispatcher) { /* Bukkit API here */ }
 */
class BukkitDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (Bukkit.isPrimaryThread()) {
            block.run()
        } else {
            plugin.server.scheduler.runTask(plugin, block)
        }
    }
}

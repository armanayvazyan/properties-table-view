package io.github.armanayvazyan.propsy

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

/**
 * Isolates the platform query for the optional `.env files support` plugin
 * (`ru.adelf.idea.dotenv`). Keeps the plugin-state call in one testable place;
 * references no `ru.adelf.*` classes.
 */
object DotEnvPlugin {
    const val ID = "ru.adelf.idea.dotenv"

    /** True when the dotenv plugin is installed AND enabled (its backend is loaded). */
    fun isActive(): Boolean =
        PluginManagerCore.getPlugin(PluginId.getId(ID))?.isEnabled == true
}

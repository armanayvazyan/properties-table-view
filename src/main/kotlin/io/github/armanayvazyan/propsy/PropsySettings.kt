package io.github.armanayvazyan.propsy

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.Tag

/** A named, serializable reference to a .properties file (path relative to project base dir). */
@Tag("entry")
class PathEntry() {
    var name: String = ""
    var path: String = ""

    constructor(name: String, path: String) : this() {
        this.name = name
        this.path = path
    }

    override fun equals(other: Any?): Boolean =
        other is PathEntry && other.name == name && other.path == path

    override fun hashCode(): Int = 31 * name.hashCode() + path.hashCode()

    override fun toString(): String = "PathEntry(name=$name, path=$path)"
}

/**
 * Per-project storage for the named list of .properties files shown in the
 * Propsy tool window. Legacy bare-path settings are migrated on load.
 */
@Service(Service.Level.PROJECT)
@State(name = "PropsySettings", storages = [Storage("propsy.xml")])
class PropsySettings : PersistentStateComponent<PropsySettings.State> {

    class State {
        var entries: MutableList<PathEntry> = mutableListOf()

        /** Legacy field from pre-name versions; migrated into [entries] on load, then cleared. */
        var paths: MutableList<String> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        if (myState.entries.isEmpty() && myState.paths.isNotEmpty()) {
            myState.entries = myState.paths
                .map { PathEntry(it.substringAfterLast('/'), it) }
                .toMutableList()
            myState.paths = mutableListOf()
        }
    }

    /** Named entries. Always returns/stores defensive copies. */
    var entries: List<PathEntry>
        get() = myState.entries.map { PathEntry(it.name, it.path) }
        set(value) {
            myState.entries = value.map { PathEntry(it.name, it.path) }.toMutableList()
        }

    companion object {
        val CHANGED_TOPIC: Topic<Runnable> =
            Topic.create("Propsy settings changed", Runnable::class.java)

        fun getInstance(project: Project): PropsySettings = project.service()
    }
}

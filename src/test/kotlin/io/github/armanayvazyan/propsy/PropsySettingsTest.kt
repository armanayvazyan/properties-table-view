package io.github.armanayvazyan.propsy

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropsySettingsTest : BasePlatformTestCase() {

    fun testEntriesRoundTripThroughState() {
        val settings = PropsySettings.getInstance(project)
        settings.entries = listOf(
            PathEntry("messages", "src/messages.properties"),
            PathEntry("app", "config/app.properties"),
        )

        val state = settings.state
        val restored = PropsySettings()
        restored.loadState(state)

        assertEquals(
            listOf(
                PathEntry("messages", "src/messages.properties"),
                PathEntry("app", "config/app.properties"),
            ),
            restored.entries,
        )
    }

    fun testLegacyPathsMigrateToEntries() {
        val legacy = PropsySettings.State().apply {
            paths = mutableListOf("src/messages.properties", "config/app.properties")
        }
        val settings = PropsySettings()
        settings.loadState(legacy)

        assertEquals(
            listOf(
                PathEntry("messages.properties", "src/messages.properties"),
                PathEntry("app.properties", "config/app.properties"),
            ),
            settings.entries,
        )
        // legacy field cleared after migration
        assertTrue(settings.state.paths.isEmpty())
    }

    fun testEntriesGetterReturnsDefensiveCopy() {
        val settings = PropsySettings.getInstance(project)
        settings.entries = listOf(PathEntry("a", "a.properties"))
        val first = settings.entries
        settings.entries = listOf(PathEntry("a", "a.properties"), PathEntry("b", "b.properties"))
        assertEquals(1, first.size)
    }
}

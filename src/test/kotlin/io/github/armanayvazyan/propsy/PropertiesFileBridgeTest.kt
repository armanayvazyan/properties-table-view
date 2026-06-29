package io.github.armanayvazyan.propsy

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesFileBridgeTest : BasePlatformTestCase() {

    private fun configure(text: String): PropertiesFile =
        myFixture.configureByText("messages.properties", text) as PropertiesFile

    fun testEntriesAreReadInOrder() {
        val file = configure("# header\nfoo=1\nbar=2\nbaz=3\n")
        val keys = PropertiesFileBridge.entries(file).map { it.key }
        assertEquals(listOf("foo", "bar", "baz"), keys)
    }

    fun testSetValuePreservesCommentsAndOrder() {
        val file = configure("# header comment\n\nfoo=1\nbar=2\n# trailing\nbaz=3\n")
        val bar = PropertiesFileBridge.entries(file).first { it.key == "bar" }
        PropertiesFileBridge.setValue(project, bar.property, "22")
        assertEquals("# header comment\n\nfoo=1\nbar=22\n# trailing\nbaz=3\n", file.containingFile.text)
    }

    fun testAddEntryAppendsAndRejectsDuplicate() {
        val file = configure("foo=1\n")
        assertTrue(PropertiesFileBridge.addEntry(project, file, "bar", "2"))
        assertFalse(PropertiesFileBridge.addEntry(project, file, "foo", "x"))
        assertEquals(listOf("foo", "bar"), PropertiesFileBridge.entries(file).map { it.key })
    }

    fun testDeleteEntryRemovesRow() {
        val file = configure("# keep\nfoo=1\nbar=2\n")
        val foo = PropertiesFileBridge.entries(file).first { it.key == "foo" }
        PropertiesFileBridge.deleteEntry(project, foo.property)
        assertEquals(listOf("bar"), PropertiesFileBridge.entries(file).map { it.key })
        assertTrue(file.containingFile.text.contains("# keep"))
    }
}

package io.github.armanayvazyan.propsy

import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesBackendTest : BasePlatformTestCase() {

    private val backend = PropertiesBackend()

    fun testEntriesMapKeyValueAndHandle() {
        val file = myFixture.configureByText("m.properties", "foo=1\nbar=2\n") as PropertiesFile
        val entries = backend.entries(file)
        assertEquals(listOf("foo", "bar"), entries.map { it.key })
        assertEquals(listOf("1", "2"), entries.map { it.value })
        assertTrue(entries.first().handle is IProperty)
    }

    fun testSetValueWritesThrough() {
        val file = myFixture.configureByText("m.properties", "foo=1\n") as PropertiesFile
        val entry = backend.entries(file).first()
        backend.setValue(project, entry, "99")
        assertEquals("foo=99\n", file.containingFile.text)
    }
}

package io.github.armanayvazyan.propsy

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropsyTableModelTest : BasePlatformTestCase() {

    fun testModelExposesKeyValueColumns() {
        val file = myFixture.configureByText("m.properties", "foo=1\nbar=2\n") as PropertiesFile
        val model = PropsyTableModel(project)
        model.load(ResolvedFile(PropertiesBackend(), file))
        assertEquals(2, model.rowCount)
        assertEquals(2, model.columnCount)
        assertEquals("foo", model.getValueAt(0, 0))
        assertEquals("1", model.getValueAt(0, 1))
    }

    fun testSetValueAtWritesThrough() {
        val file = myFixture.configureByText("m.properties", "foo=1\n") as PropertiesFile
        val model = PropsyTableModel(project)
        model.load(ResolvedFile(PropertiesBackend(), file))
        model.setValueAt("99", 0, 1)
        assertEquals("99", model.getValueAt(0, 1))
        assertEquals("foo=99\n", file.containingFile.text)
    }

    fun testSetKeyAtRenames() {
        val file = myFixture.configureByText("m.properties", "foo=1\n") as PropertiesFile
        val model = PropsyTableModel(project)
        model.load(ResolvedFile(PropertiesBackend(), file))
        model.setValueAt("renamed", 0, 0)
        assertEquals("renamed", model.getValueAt(0, 0))
        assertEquals("renamed=1\n", file.containingFile.text)
    }
}

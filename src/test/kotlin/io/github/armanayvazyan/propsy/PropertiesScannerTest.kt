package io.github.armanayvazyan.propsy

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertiesScannerTest : BasePlatformTestCase() {

    fun testScanFindsPropertiesFileInContent() {
        myFixture.addFileToProject("config.properties", "a=b")

        val found = PropertiesScanner.scan(project)

        assertTrue(
            "expected config.properties in $found",
            found.any { it.path == "config.properties" },
        )
        assertTrue(
            "scanned entries must have a non-blank name",
            found.first { it.path == "config.properties" }.name.isNotBlank(),
        )
    }

    fun testScanIgnoresNonPropertiesFiles() {
        myFixture.addFileToProject("notes.txt", "hello")

        val found = PropertiesScanner.scan(project)

        assertFalse(found.any { it.path.endsWith("notes.txt") })
    }
}

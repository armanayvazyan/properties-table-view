package io.github.armanayvazyan.propsy

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DotEnvPluginTest : BasePlatformTestCase() {

    fun testIdConstantIsStable() {
        assertEquals("ru.adelf.idea.dotenv", DotEnvPlugin.ID)
    }

    fun testIsActiveTrueInTestEnv() {
        // The dotenv plugin is on the test classpath as a build dependency.
        assertTrue(DotEnvPlugin.isActive())
    }
}

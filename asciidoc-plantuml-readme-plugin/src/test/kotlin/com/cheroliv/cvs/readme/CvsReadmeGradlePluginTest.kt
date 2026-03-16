package com.cheroliv.cvs.readme

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * A simple unit test for the 'com.cheroliv.cvs.readme' plugin.
 */
class CvsReadmeGradlePluginTest {
    @Test fun `plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.cvs.readme")

        // Verify the result
        assertNotNull(project.tasks.findByName("readme"))
    }
}

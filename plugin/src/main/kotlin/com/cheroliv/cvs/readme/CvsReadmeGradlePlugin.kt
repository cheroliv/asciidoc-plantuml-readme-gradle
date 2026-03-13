package com.cheroliv.cvs.readme

import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * A simple 'hello world' plugin.
 */
class CvsReadmeGradlePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        // Register a task
        project.tasks.register("readme") { task ->
            task.doLast {
                println("Hello from plugin 'com.cheroliv.cvs.readme'")
            }
        }
    }
}

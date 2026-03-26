@file:Suppress("unused")

package readme.scenarios

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import java.io.File

class ProcessSteps(private val world: ReadMeWorld) {

    // ── Given ─────────────────────────────────────────────────────────────────

    @Given("the file {string} exists with the following content:")
    fun givenFileExistsWithDocString(relativePath: String, content: String) {
        world.writeProjectFile(relativePath, content.trimIndent())
    }

    /**
     * Creates a fake git root as the parent of the Gradle project directory.
     * Simulates a monorepo layout where the Gradle project lives in a subdirectory:
     *
     *   gitRoot/           ← fake .git here
     *     .git/
     *     readme-plugin/   ← projectDir (Gradle rootProject)
     *       build.gradle.kts
     *       README_truth.adoc
     *
     * GitUtils.findGitRoot() will walk up and find the .git at gitRoot level.
     * imgDir will resolve to gitRoot/.github/workflows/readmes/images/
     * The image:: path in README.adoc will be relative: ../.github/...
     */
    @Given("the project is nested under a git root")
    fun givenProjectNestedUnderGitRoot() {
        val currentProjectDir = world.projectDir
            ?: error("Project directory must be initialized before nesting under git root")

        // Create a fake git root one level above the current projectDir
        val gitRoot = currentProjectDir.parentFile
        File(gitRoot, ".git").mkdirs()

        // Store the git root so Then steps can assert on files relative to it
        world.gitRoot = gitRoot
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    @Then("the file {string} should contain {string}")
    fun thenFileContains(relativePath: String, expected: String) {
        assertThat(world.readProjectFile(relativePath))
            .describedAs("Expected $relativePath to contain: $expected")
            .contains(expected)
    }

    @Then("the file {string} should not contain {string}")
    fun thenFileNotContains(relativePath: String, unexpected: String) {
        assertThat(world.readProjectFile(relativePath))
            .describedAs("Expected $relativePath to NOT contain: $unexpected")
            .doesNotContain(unexpected)
    }

    @Then("the file {string} should exist")
    fun thenFileShouldExist(relativePath: String) {
        assertThat(world.projectFileExists(relativePath))
            .describedAs("Expected file to exist: $relativePath")
            .isTrue()
    }

    @Then("the image file should exist at the git root {string}")
    fun thenImageExistsAtGitRoot(relativePath: String) {
        val gitRoot = world.gitRoot
            ?: error(
                "No git root recorded — " +
                        "did you use 'the project is nested under a git root'?"
            )
        val imageFile = File(gitRoot, relativePath)
        assertThat(imageFile)
            .describedAs("Expected image to exist at git root: ${imageFile.absolutePath}")
            .exists()
    }

    @Then("the file {string} should contain the following content:")
    fun thenFileContainsDocString(relativePath: String, expected: String) {
        assertThat(world.readProjectFile(relativePath))
            .describedAs("Expected $relativePath to contain docstring content")
            .contains(expected.trimIndent())
    }
}

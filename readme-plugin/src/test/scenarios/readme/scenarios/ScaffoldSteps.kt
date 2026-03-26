@file:Suppress("unused")

package readme.scenarios

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat

class ScaffoldSteps(private val world: ReadMeWorld) {

    // ── Given ─────────────────────────────────────────────────────────────────

    @Given("the following files already exist:")
    fun givenFilesExist(table: DataTable) {
        table.asMaps().forEach { row ->
            val file    = row["file"]    ?: error("Missing 'file' column")
            val content = row["content"] ?: ""
            world.writeProjectFile(file, content)
        }
    }

    @Given("a {string} with the following yaml values:")
    fun givenConfigWithYamlValues(fileName: String, table: DataTable) {
        val overrides = table.asMaps().associate { row ->
            (row["key"] ?: error("Missing 'key' column")) to (row["value"] ?: "")
        }
        world.writeProjectFile(fileName, buildYamlConfig(overrides))
    }

    @Given("the file {string} exists with content {string}")
    fun givenFileExistsWithContent(relativePath: String, content: String) {
        world.writeProjectFile(relativePath, content)
    }

    @Given("the git remote validator is mocked with result {string}")
    fun givenGitRemoteValidatorMocked(result: String) {
        world.gitValidatorMockResult = result
    }

    // ── When ──────────────────────────────────────────────────────────────────

    @When("I am executing the task {string} expecting failure")
    fun whenExecutingTaskExpectingFailure(taskName: String) = runBlocking {
        world.executeGradle(taskName)
    }

    // ── Then ──────────────────────────────────────────────────────────────────

    @Then("the following files should exist:")
    fun thenFilesExist(table: DataTable) {
        table.asMaps().forEach { row ->
            val file = row["file"] ?: error("Missing 'file' column")
            assertThat(world.projectFileExists(file))
                .describedAs("Expected file to exist: $file")
                .isTrue()
        }
    }

    @Then("the file {string} should contain the following yaml values:")
    fun thenFileContainsYamlValues(fileName: String, table: DataTable) {
        val content = world.readProjectFile(fileName)
        table.asMaps().forEach { row ->
            val key     = row["key"]   ?: error("Missing 'key' column")
            val value   = row["value"] ?: error("Missing 'value' column")
            val leafKey = key.substringAfterLast(".")

            // Accept both quoted and unquoted YAML values:
            //   userName: github-actions[bot]
            //   userName: "github-actions[bot]"
            val matched = content.contains("$leafKey: $value") ||
                    content.contains("$leafKey: \"$value\"")

            assertThat(matched)
                .describedAs(
                    "Expected $fileName to contain $key: $value " +
                            "(quoted or unquoted)"
                )
                .isTrue()
        }
    }

    @Then("the file {string} should contain the following watched branches:")
    fun thenFileContainsWatchedBranches(fileName: String, table: DataTable) {
        val content = world.readProjectFile(fileName)
        table.asMaps().forEach { row ->
            val branch = row["branch"] ?: error("Missing 'branch' column")
            assertThat(content)
                .describedAs("Expected $fileName to contain watched branch: $branch")
                .contains("- $branch")
        }
    }

    @Then("the file {string} should still contain {string}")
    fun thenFileStillContains(relativePath: String, expected: String) {
        assertThat(world.readProjectFile(relativePath))
            .describedAs("Expected $relativePath to still contain: $expected")
            .contains(expected)
    }

    @Then("the following files should still contain their original content:")
    fun thenFilesStillContainOriginalContent(table: DataTable) {
        table.asMaps().forEach { row ->
            val file    = row["file"]    ?: error("Missing 'file' column")
            val content = row["content"] ?: error("Missing 'content' column")
            assertThat(world.readProjectFile(file))
                .describedAs("Expected $file to still contain: $content")
                .contains(content)
        }
    }

    @Then("the build should fail")
    fun thenBuildShouldFail() {
        val output = world.buildResult?.output ?: ""
        assertThat(output)
            .describedAs("Expected build output to contain BUILD FAILED")
            .contains("BUILD FAILED")
    }

    @Then("the build log should contain the following entries:")
    fun thenBuildLogContainsEntries(table: DataTable) {
        val output = world.buildResult?.output
            ?: error("No build result available — did the task run?")
        table.asMaps().forEach { row ->
            val level   = row["level"]   ?: error("Missing 'level' column")
            val keyword = row["keyword"] ?: error("Missing 'keyword' column")
            val value   = row["value"]   ?: error("Missing 'value' column")
            assertThat(output)
                .describedAs(
                    "Expected log to contain [$level] with keyword '$keyword' " +
                            "and value '$value'"
                )
                .containsIgnoringCase(level)
                .containsIgnoringCase(keyword)
                .containsIgnoringCase(value)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal valid readme-truth.yml overriding specific dot-notation keys.
     * All fields default to the plugin's standard conventions.
     * repoUrl is intentionally omitted — resolved programmatically from .git config.
     */
    private fun buildYamlConfig(overrides: Map<String, String>): String {
        fun v(key: String, default: String) = overrides.getOrDefault(key, default)
        return """
            source:
              dir: "${v("source.dir", ".")}"
              defaultLang: "${v("source.defaultLang", "en")}"
            output:
              imgDir: "${v("output.imgDir", ".github/workflows/readmes/images")}"
            git:
              userName: "${v("git.userName", "github-actions[bot]")}"
              userEmail: "${v("git.userEmail", "github-actions[bot]@users.noreply.github.com")}"
              commitMessage: "${v("git.commitMessage", "chore: generate readme [skip ci]")}"
              token: "${v("git.token", "<YOUR_GITHUB_PAT>")}"
              watchedBranches:
                - "main"
                - "master"
        """.trimIndent()
    }
}

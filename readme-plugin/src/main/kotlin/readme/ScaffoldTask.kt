package readme

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Scaffolding task — runs once on first launch, then validates config on every run.
 *
 * Creates if absent:
 *  - readme.yml                          (template with placeholders)
 *  - .github/workflows/readme_action.yml (GitHub Actions workflow)
 *
 * Validates on every run:
 *  - source.dir exists            → ERROR + build failure if not
 *  - source.defaultLang empty     → INFO  + fallback to "en"
 *  - output.imgDir empty          → INFO  + fallback to default
 *  - git.token placeholder        → WARN  + continue
 *  - GitHub remote reachability   → WARN  + continue
 *
 * Internal test property:
 *  -Preadme.git.validator.mock=<r>
 *  Valid values: VALID, TOKEN_PLACEHOLDER, UNREACHABLE,
 *                REPOSITORY_NOT_FOUND, INSUFFICIENT_PUSH_RIGHTS
 */
@UntrackedTask(because = "Scaffolding — always checked, never overwritten")
abstract class ScaffoldTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectDir: DirectoryProperty

    @TaskAction
    fun scaffold() {
        val root   = projectDir.get().asFile
        val config = ReadmePlantUmlConfig.load(root)

        scaffoldConfig(root)
        scaffoldWorkflow(root)
        validateConfig(root, config)
    }

    // ── File creation ─────────────────────────────────────────────────────────

    private fun scaffoldConfig(root: File) {
        val configFile = File(root, ReadmePlantUmlConfig.CONFIG_FILE_NAME)

        if (configFile.exists()) {
            logger.lifecycle("✔ readme.yml already exists — skipped")
            return
        }

        configFile.writeText(CONFIG_TEMPLATE)
        logger.lifecycle("✔ readme.yml created — fill in your token and add to GitHub Secrets")
    }

    private fun scaffoldWorkflow(root: File) {
        val workflowDir  = File(root, ".github/workflows").also { it.mkdirs() }
        val workflowFile = File(workflowDir, "readme_action.yml")

        if (workflowFile.exists()) {
            logger.lifecycle("✔ .github/workflows/readme_action.yml already exists — skipped")
            return
        }

        workflowFile.writeText(WORKFLOW_TEMPLATE)
        logger.lifecycle("✔ .github/workflows/readme_action.yml created")
        logger.lifecycle("  → commit this file to activate the CI workflow")
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateConfig(root: File, config: ReadmePlantUmlConfig) {
        validateSourceDir(root, config)
        validateDefaultLang(config)
        validateImgDir(root, config)
        validateGitConfig(config)
    }

    private fun validateSourceDir(root: File, config: ReadmePlantUmlConfig) {
        val sourceDir = File(root, config.source.dir)
        if (!sourceDir.exists()) {
            logger.error(
                "[ERROR] source.dir does not exist: ${config.source.dir}\n" +
                "→ Update 'source.dir' in readme.yml to a valid path"
            )
            throw IllegalStateException(
                "source.dir does not exist: ${config.source.dir}"
            )
        }
        logger.lifecycle("✔ [INFO]  source.dir         — OK (${config.source.dir})")
    }

    private fun validateDefaultLang(config: ReadmePlantUmlConfig) {
        if (config.source.defaultLang.isBlank()) {
            logger.lifecycle(
                "⚠ [INFO]  defaultLang is empty — falling back to default lang: en"
            )
        } else {
            logger.lifecycle(
                "✔ [INFO]  defaultLang        — OK (${config.source.defaultLang})"
            )
        }
    }

    private fun validateImgDir(root: File, config: ReadmePlantUmlConfig) {
        if (config.output.imgDir.isBlank()) {
            logger.lifecycle(
                "⚠ [INFO]  imgDir is empty — falling back to default imgDir:" +
                " .github/workflows/readmes/images"
            )
            return
        }

        val resolvedImgDir = GitUtils.resolveImgDir(root, config.output.imgDir)

        // Attempt to create the directory if it does not exist yet
        if (!resolvedImgDir.exists() && !resolvedImgDir.mkdirs()) {
            logger.error(
                "[ERROR] output.imgDir cannot be created: ${resolvedImgDir.absolutePath}\n" +
                "→ Check parent directory permissions"
            )
            throw IllegalStateException(
                "output.imgDir cannot be created: ${resolvedImgDir.absolutePath}"
            )
        }

        // Verify the directory is writable
        if (!resolvedImgDir.canWrite()) {
            logger.error(
                "[ERROR] output.imgDir is not writable: ${resolvedImgDir.absolutePath}\n" +
                "→ Check directory permissions"
            )
            throw IllegalStateException(
                "output.imgDir is not writable: ${resolvedImgDir.absolutePath}"
            )
        }

        logger.lifecycle("✔ [INFO]  output.imgDir     — OK (${config.output.imgDir})")
    }

    private fun validateGitConfig(config: ReadmePlantUmlConfig) {
        val validator = resolveMockValidator() ?: JGitRemoteValidator()
        when (validator.validate(config.git)) {
            is GitValidationResult.Valid -> {
                logger.lifecycle("✔ [INFO]  GitHub connection  — OK")
                logger.lifecycle("✔ [INFO]  repository access  — OK")
                logger.lifecycle("✔ [INFO]  push permission    — OK")
            }
            is GitValidationResult.TokenPlaceholder -> {
                logger.warn(
                    "[WARN]  git.token is still a placeholder — " +
                    "replace <YOUR_GITHUB_PAT> with a real token\n" +
                    "→ commitGeneratedReadme will fail until token is set"
                )
            }
            is GitValidationResult.Unreachable -> {
                logger.warn(
                    "[WARN]  GitHub is unreachable — " +
                    "remote validation skipped\n" +
                    "→ processReadme remains fully operational"
                )
            }
            is GitValidationResult.RepositoryNotFound -> {
                logger.warn(
                    "[WARN]  repository not found — " +
                    "check the origin remote in your local .git config\n" +
                    "→ commitGeneratedReadme will fail"
                )
            }
            is GitValidationResult.InsufficientPushRights -> {
                logger.warn(
                    "[WARN]  push — insufficient rights — " +
                    "token does not have contents:write permission\n" +
                    "→ commitGeneratedReadme will fail"
                )
            }
        }
    }

    /**
     * Resolves a mock validator from the internal test property.
     * Returns null in production — JGitRemoteValidator is used instead.
     *
     * Internal test property: -Preadme.git.validator.mock=<r>
     */
    private fun resolveMockValidator(): GitRemoteValidator? {
        val mockValue = project.findProperty("readme.git.validator.mock")
            as? String ?: return null

        logger.lifecycle("[INFO] git remote validator mock active — result: $mockValue")

        return GitRemoteValidator { _ ->
            when (mockValue) {
                "VALID"                    -> GitValidationResult.Valid
                "TOKEN_PLACEHOLDER"        -> GitValidationResult.TokenPlaceholder
                "UNREACHABLE"              -> GitValidationResult.Unreachable
                "REPOSITORY_NOT_FOUND"     -> GitValidationResult.RepositoryNotFound
                "INSUFFICIENT_PUSH_RIGHTS" -> GitValidationResult.InsufficientPushRights
                else -> error("Unknown readme.git.validator.mock value: $mockValue")
            }
        }
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    companion object {
        val CONFIG_TEMPLATE = """
            # ─────────────────────────────────────────────────────────────────
            # readme.yml — Plugin configuration
            #
            # Source of truth files convention :
            #   README_truth.adoc       → default language
            #   README_truth_fr.adoc    → French
            #   README_truth_de.adoc    → German
            #
            # DO NOT commit this file with a real token.
            # Store the full content of this file (token included) in the
            # GitHub secret README_GRADLE_PLUGIN :
            #   GitHub → Settings → Secrets and variables → Actions
            #                     → New repository secret
            # ─────────────────────────────────────────────────────────────────

            source:
              dir: .
              defaultLang: en

            output:
              imgDir: .github/workflows/readmes/images

            git:
              userName: github-actions[bot]
              userEmail: github-actions[bot]@users.noreply.github.com
              commitMessage: "chore: generate readme [skip ci]"
              token: <YOUR_GITHUB_PAT>
              watchedBranches:
                - main
                - master
        """.trimIndent()

        private val WORKFLOW_TEMPLATE = """
            name: Generate README from truth sources

            on:
              push:
                branches:
                  - main
                  - master
                paths:
                  - "README_truth*.adoc"
              workflow_dispatch:

            jobs:
              generate-readme:
                name: Process README_truth → README
                runs-on: ubuntu-latest

                permissions:
                  contents: write

                steps:
                  - name: Checkout repository
                    uses: actions/checkout@v4
                    with:
                      fetch-depth: 0

                  - name: Set up JDK 24
                    uses: actions/setup-java@v4
                    with:
                      java-version: '24'
                      distribution: 'temurin'
                      cache: gradle

                  - name: Grant execute permission for gradlew
                    run: chmod +x gradlew

                  - name: Inject plugin config
                    run: echo "${'$'}{{ secrets.README_GRADLE_PLUGIN }}" > readme.yml

                  - name: Generate README and commit via JGit
                    run: ./gradlew -q -s commitGeneratedReadme --no-daemon

                  - name: Summary
                    if: always()
                    run: |
                      echo "### README — Result" >> ${'$'}GITHUB_STEP_SUMMARY
                      echo "" >> ${'$'}GITHUB_STEP_SUMMARY
                      git diff HEAD~1 --name-only 2>/dev/null | while read f; do
                        echo "- \`${'$'}f\`" >> ${'$'}GITHUB_STEP_SUMMARY
                      done || echo "- *(first run)*" >> ${'$'}GITHUB_STEP_SUMMARY
        """.trimIndent()
    }
}

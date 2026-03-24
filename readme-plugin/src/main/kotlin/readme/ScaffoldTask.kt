package readme

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Tâche de scaffolding — exécutée une seule fois au premier lancement.
 *
 * Crée si absents :
 *  - readme-truth.yml                        (template avec placeholders)
 *  - .github/workflows/readme_truth.yml      (workflow GitHub Actions)
 *
 * Convention de nommage des sources de vérité :
 *   README_truth.adoc      → langue par défaut (ex: en)
 *   README_truth_fr.adoc   → français
 *   README_truth_de.adoc   → allemand
 *
 * Ne touche JAMAIS à un fichier déjà existant.
 */
@UntrackedTask(because = "Scaffolding — toujours vérifié mais jamais écrasé")
abstract class ScaffoldTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectDir: DirectoryProperty

    @TaskAction
    fun scaffold() {
        val root = projectDir.get().asFile
        scaffoldConfig(root)
        scaffoldWorkflow(root)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // readme-truth.yml
    // ─────────────────────────────────────────────────────────────────────────

    private fun scaffoldConfig(root: File) {
        val configFile = File(root, "readme-truth.yml")

        if (configFile.exists()) {
            logger.lifecycle("✔ readme-truth.yml already exists — skipped")
            return
        }

        configFile.writeText("""
            # ─────────────────────────────────────────────────────────────────
            # readme-truth.yml — Plugin configuration
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
              dir: "."
              defaultLang: "en"

            output:
              imgDir: ".github/workflows/readmes/images"

            git:
              userName: "github-actions[bot]"
              userEmail: "github-actions[bot]@users.noreply.github.com"
              commitMessage: "chore: generate readme [skip ci]"
              token: "<YOUR_GITHUB_PAT>"
              watchedBranches:
                - "main"
                - "master"
        """.trimIndent())

        logger.lifecycle("✔ readme-truth.yml created — fill in your token and add to GitHub Secrets")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // .github/workflows/readme_truth.yml
    // ─────────────────────────────────────────────────────────────────────────

    private fun scaffoldWorkflow(root: File) {
        val workflowDir  = File(root, ".github/workflows").also { it.mkdirs() }
        val workflowFile = File(workflowDir, "readme_truth.yml")

        if (workflowFile.exists()) {
            logger.lifecycle("✔ .github/workflows/readme_truth.yml already exists — skipped")
            return
        }

        workflowFile.writeText("""
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

                  - name: Set up JDK 25
                    uses: actions/setup-java@v4
                    with:
                      java-version: '25'
                      distribution: 'temurin'
                      cache: gradle

                  - name: Grant execute permission for gradlew
                    run: chmod +x gradlew

                  - name: Inject plugin config
                    run: echo "${'$'}{{ secrets.README_GRADLE_PLUGIN }}" > readme-truth.yml

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
        """.trimIndent())

        logger.lifecycle("✔ .github/workflows/readme_truth.yml created")
        logger.lifecycle("  → commit this file to activate the CI workflow")
    }
}
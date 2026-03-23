package readme

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Tâche de scaffolding — exécutée une seule fois au premier lancement.
 *
 * Crée si absents :
 *  - readme-plantuml.yml        (template avec placeholders)
 *  - .github/workflows/readme_plantuml.yml  (workflow GitHub Actions)
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
    // readme-plantuml.yml
    // ─────────────────────────────────────────────────────────────────────────

    private fun scaffoldConfig(root: File) {
        val configFile = File(root, "readme-plantuml.yml")

        if (configFile.exists()) {
            logger.lifecycle("✔ readme-plantuml.yml already exists — skipped")
            return
        }

        configFile.writeText("""
            # ─────────────────────────────────────────────────────────────────
            # readme-plantuml.yml — Plugin configuration
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

        logger.lifecycle("✔ readme-plantuml.yml created — fill in your token and add to GitHub Secrets")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // .github/workflows/readme_plantuml.yml
    // ─────────────────────────────────────────────────────────────────────────

    private fun scaffoldWorkflow(root: File) {
        val workflowDir  = File(root, ".github/workflows").also { it.mkdirs() }
        val workflowFile = File(workflowDir, "readme_plantuml.yml")

        if (workflowFile.exists()) {
            logger.lifecycle("✔ .github/workflows/readme_plantuml.yml already exists — skipped")
            return
        }

        workflowFile.writeText("""
            name: Generate README from PlantUML sources

            on:
              push:
                branches:
                  - main
                  - master
                paths:
                  - "README_plantuml*.adoc"
              workflow_dispatch:

            jobs:
              generate-readme:
                name: Process PlantUML → README
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
                    run: echo "${'$'}{{ secrets.README_GRADLE_PLUGIN }}" > readme-plantuml.yml

                  - name: Generate README and commit via JGit
                    run: ./gradlew -q -s commitGeneratedReadme --no-daemon

                  - name: Summary
                    if: always()
                    run: |
                      echo "### README PlantUML — Result" >> ${'$'}GITHUB_STEP_SUMMARY
                      echo "" >> ${'$'}GITHUB_STEP_SUMMARY
                      git diff HEAD~1 --name-only 2>/dev/null | while read f; do
                        echo "- \`${'$'}f\`" >> ${'$'}GITHUB_STEP_SUMMARY
                      done || echo "- *(first run)*" >> ${'$'}GITHUB_STEP_SUMMARY
        """.trimIndent())

        logger.lifecycle("✔ .github/workflows/readme_plantuml.yml created")
        logger.lifecycle("  → commit this file to activate the CI workflow")
    }
}
package readme

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@UntrackedTask(because = "Opération Git — toujours exécutée en CI")
abstract class CommitGeneratedReadmeTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repoDir: DirectoryProperty

    @get:Input
    abstract val gitUserName: Property<String>

    @get:Input
    abstract val gitUserEmail: Property<String>

    @get:Input
    abstract val commitMessage: Property<String>

    // @Internal instead of @Input — token resolution is deferred to task execution time.
    // This prevents Gradle from calling resolvedToken() during property validation,
    // which would fail for placeholder tokens even in mock mode where the token is never used.
    @get:Internal
    abstract val gitToken: Property<String>

    @TaskAction
    fun commitAndPush() {
        val root = repoDir.get().asFile

        // Guard: mock mode — commit locally without push or token resolution.
        // Activated via -Preadme.commit.mock=true in tests.
        if (isCommitMock()) {
            logger.lifecycle("[INFO] commit mock active — local commit only, push skipped")
            commitLocal(root)
            return
        }

        // Token is resolved lazily here — only in non-mock production path.
        val token = gitToken.get()
        val credentials = UsernamePasswordCredentialsProvider("x-access-token", token)

        Git.open(root).use { git ->
            val status = git.status().call()

            if (!hasReadmeChanges(status)) {
                logger.lifecycle("Aucun fichier généré à commiter — dépôt propre.")
                return
            }

            logChangedFiles(status)

            git.add().apply {
                addFilepattern("README*.adoc")
                addFilepattern(".github/workflows/readmes/images/")
            }.call()

            git.commit().apply {
                message = commitMessage.get()
                setAuthor(gitUserName.get(), gitUserEmail.get())
                setCommitter(gitUserName.get(), gitUserEmail.get())
            }.call()

            logger.lifecycle("Commit : \"${commitMessage.get()}\"")

            git.push()
                .setCredentialsProvider(credentials)
                .call()

            logger.lifecycle("Push effectué avec succès.")
        }
    }

    /**
     * Local-only commit — same logic as production but without push.
     * Used in tests via -Preadme.commit.mock=true.
     */
    private fun commitLocal(root: java.io.File) {
        Git.open(root).use { git ->
            val status = git.status().call()

            if (!hasReadmeChanges(status)) {
                logger.lifecycle("Aucun fichier généré à commiter — dépôt propre.")
                return
            }

            logChangedFiles(status)

            git.add().apply {
                addFilepattern("README*.adoc")
                addFilepattern(".github/workflows/readmes/images/")
            }.call()

            git.commit().apply {
                message = commitMessage.get()
                setAuthor(gitUserName.get(), gitUserEmail.get())
                setCommitter(gitUserName.get(), gitUserEmail.get())
            }.call()

            logger.lifecycle("Commit : \"${commitMessage.get()}\"")
            logger.lifecycle("[mock] Push skipped — commit created locally only.")
        }
    }

    /**
     * Returns true if git status contains any README*.adoc or images/ changes
     * that should be committed by this task.
     *
     * Uses targeted pattern matching instead of status.isClean so that
     * unrelated untracked files (e.g. .github/workflows/readme_action.yml
     * created by scaffoldReadme) do not trigger a spurious commit.
     */
    private fun hasReadmeChanges(status: Status): Boolean {
        val allChanged = status.added + status.changed + status.modified +
                status.removed + status.missing + status.untracked

        return allChanged.any { path ->
            path.matches(Regex("README.*\\.adoc")) ||
            path.startsWith(".github/workflows/readmes/images/")
        }
    }

    private fun logChangedFiles(status: Status) {
        val allChanged = status.added + status.changed + status.modified +
                status.removed + status.missing + status.untracked
        val readme = allChanged.filter { path ->
            path.matches(Regex("README.*\\.adoc")) ||
            path.startsWith(".github/workflows/readmes/images/")
        }
        logger.lifecycle("Fichiers à commiter (${readme.size}) :")
        readme.forEach { logger.lifecycle("  + $it") }
    }

    /**
     * Returns true if -Preadme.commit.mock=true is set.
     * Always returns false in production — property is absent.
     */
    private fun isCommitMock(): Boolean =
        project.findProperty("readme.commit.mock")
            ?.toString()
            ?.trim()
            ?.equals("true", ignoreCase = true)
            ?: false
}

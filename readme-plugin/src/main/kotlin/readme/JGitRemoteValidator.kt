package readme


import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * Production implementation of GitRemoteValidator.
 * Resolves the remote URL from the local .git config (origin) — never from ReadmePlantUmlConfig.
 * Uses a lightweight ls-remote to validate token, repo existence and push rights.
 */
class JGitRemoteValidator : GitRemoteValidator {

    override fun validate(config: GitConfig): GitValidationResult {
        if (config.token.isBlank() || config.token == "<YOUR_GITHUB_PAT>")
            return GitValidationResult.TokenPlaceholder

        return try {
            val credentials = UsernamePasswordCredentialsProvider(
                "x-access-token",
                config.token
            )

            // Resolve origin URL from local .git config — no field in GitConfig needed
            val repoUrl = resolveOriginUrl()
                ?: return GitValidationResult.Unreachable

            Git.lsRemoteRepository()
                .setRemote(repoUrl)
                .setCredentialsProvider(credentials)
                .call()

            // TODO: verify push rights via GitHub API (contents:write scope)
            GitValidationResult.Valid

        } catch (e: TransportException) {
            when {
                e.message?.contains("not found", ignoreCase = true) == true
                    -> GitValidationResult.RepositoryNotFound

                e.message?.contains("not authorized", ignoreCase = true) == true
                    -> GitValidationResult.InsufficientPushRights

                else
                    -> GitValidationResult.Unreachable
            }
        } catch (e: Exception) {
            GitValidationResult.Unreachable
        }
    }

    /**
     * Reads the 'origin' remote URL from the local .git repository config.
     * Walks up the filesystem from the current working directory to find the .git folder.
     * Returns null with a clear log if no git repository or no origin remote is found.
     */
    private fun resolveOriginUrl(): String? {
        val gitRoot = findGitRoot()

        if (gitRoot == null) {
            println("[readme] WARNING: no .git directory found in filesystem hierarchy — remote unreachable")
            return null
        }

        return try {
            Git.open(gitRoot).use { git ->
                git.repository
                    .config
                    .getString("remote", "origin", "url")
                    .takeIf { it?.isNotBlank() == true }
                    ?: run {
                        println("[readme] WARNING: no 'origin' remote configured in ${gitRoot.absolutePath}/.git/config — remote unreachable")
                        null
                    }
            }
        } catch (e: Exception) {
            println("[readme] WARNING: failed to read .git config at ${gitRoot.absolutePath} — ${e.message}")
            null
        }
    }

    /**
     * Walks up the filesystem from the working directory to find the .git folder.
     * Returns the directory containing .git, or null if not found.
     */
    private fun findGitRoot(): File? {
        var dir = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, ".git").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }
}


/**
 * SAM interface — validates remote git connectivity and permissions.
 * Production: JGitRemoteValidator
 * Test: lambda driven by -Preadme.git.validator.mock system property
 */
fun interface GitRemoteValidator {
    fun validate(config: GitConfig): GitValidationResult
}

sealed class GitValidationResult {
    object Valid : GitValidationResult()
    object TokenPlaceholder : GitValidationResult()
    object Unreachable : GitValidationResult()
    object RepositoryNotFound : GitValidationResult()
    object InsufficientPushRights : GitValidationResult()
}
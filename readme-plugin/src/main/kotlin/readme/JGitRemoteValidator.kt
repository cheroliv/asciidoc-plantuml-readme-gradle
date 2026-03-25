package readme

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/**
 * Production implementation of GitRemoteValidator.
 * Uses a lightweight ls-remote to validate token, repo existence and push rights.
 * Never instantiated during tests — replaced by the mock property mechanism.
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

            Git.lsRemoteRepository()
                .setRemote(config.repoUrl)
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
    object Valid                  : GitValidationResult()
    object TokenPlaceholder       : GitValidationResult()
    object Unreachable            : GitValidationResult()
    object RepositoryNotFound     : GitValidationResult()
    object InsufficientPushRights : GitValidationResult()
}
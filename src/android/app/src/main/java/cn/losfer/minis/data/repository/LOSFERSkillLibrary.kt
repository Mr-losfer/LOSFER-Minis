package cn.losfer.minis.data.repository

import android.util.Log
import cn.losfer.minis.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * [LOSFER-skill-library] "Download LOSFER Skill Library" on the Skills screen.
 *
 * The list of skill repositories lives in `losfer-skills.json` at the root of
 * our OWN release repo (BuildConfig.RELEASE_REPO), so adding a library never
 * needs a new APK — edit that file and the next tap picks it up. The default
 * entry is https://github.com/NoraStory/reverse-SKILL (48 SKILL.md packs).
 *
 * Every GitHub request goes through gh-proxy.com FIRST and falls back to a
 * direct connection, matching the update checker: users who cannot reach
 * GitHub directly still get the whole library. Both the config file and the
 * repository archive are fetched that way.
 *
 * The archive is a standard GitHub "Download ZIP" (one directory per skill,
 * each with a SKILL.md). SkillRepository.importSkillLibraryZip splits it into
 * per-skill mini-zips, so scripts/, references/ and assets/ come along too.
 */
object LOSFERSkillLibrary {

    private const val TAG = "LOSFERSkillLibrary"
    private const val GH_PROXY = "https://gh-proxy.com/"

    /** Safety guard: a skill library is a few MB; never hold a huge response. */
    private const val MAX_BYTES = 64 * 1024 * 1024

    /** Built-in default so one tap still works if the config file is
     *  temporarily unreachable (e.g. raw CDN cache right after a push). */
    private const val FALLBACK_REPO = "https://github.com/NoraStory/reverse-SKILL"

    private val configUrl =
        "https://raw.githubusercontent.com/${BuildConfig.RELEASE_REPO}/main/losfer-skills.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    data class Library(val repoUrl: String, val branch: String)

    enum class Failure {
        /** No archive could be fetched through the proxy or directly. */
        DOWNLOAD,

        /** An archive was fetched but contained no SKILL.md packs. */
        EMPTY,
    }

    data class LibraryResult(
        val libraries: Int = 0,
        val found: Int = 0,
        val imported: Int = 0,
        val failed: Int = 0,
        val failure: Failure? = null,
        val detail: String? = null,
    ) {
        val isSuccess: Boolean get() = failure == null && imported > 0
    }

    suspend fun downloadAndImport(skillRepository: SkillRepository): LibraryResult =
        withContext(Dispatchers.IO) {
            // Config is optional at runtime: when it cannot be read (or is
            // empty), fall back to the built-in default library so the button
            // is never a dead end.
            val configText = fetch(configUrl, preferProxy = true)
                ?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
            val libraries = configText?.let { parseConfig(it) }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(Library(FALLBACK_REPO, "main"))
            Log.i(TAG, "skill libraries: ${libraries.size} (config ${if (configText != null) "ok" else "fallback"})")

            var fetchedLibraries = 0
            var found = 0
            var imported = 0
            var failed = 0
            val unreachable = mutableListOf<String>()

            for (lib in libraries) {
                val coords = parseRepoCoordinates(lib.repoUrl) ?: continue
                val (user, repository) = coords
                val archive = fetchArchive(user, repository, lib.branch)
                if (archive == null) {
                    unreachable += "$user/$repository"
                    continue
                }
                fetchedLibraries++
                val rawBase = "https://raw.githubusercontent.com/$user/$repository/${lib.branch}"
                val outcome = skillRepository.importSkillLibraryZip(
                    ByteArrayInputStream(archive),
                    rawBase,
                )
                found += outcome.found
                imported += outcome.imported
                failed += outcome.failed
            }

            when {
                imported > 0 -> LibraryResult(fetchedLibraries, found, imported, failed)
                fetchedLibraries == 0 -> LibraryResult(
                    failure = Failure.DOWNLOAD,
                    detail = unreachable.joinToString(", ").ifBlank { null },
                )
                else -> LibraryResult(failure = Failure.EMPTY)
            }
        }

    // ─── Config parsing ────────────────────────────────────────────────────

    /**
     * Accepts the JSON format we ship (`{"repositories":[{"repo":…,
     * "branch":…}]}`), a plain string array, a single `{"repo":…}` object,
     * and — so a .txt file also works — one GitHub URL per line with `#`
     * comments.
     */
    private fun parseConfig(text: String): List<Library> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        val out = mutableListOf<Library>()
        val json = runCatching { JSONObject(trimmed) }.getOrNull()

        if (json != null) {
            val arr = json.optJSONArray("repositories")
                ?: json.optJSONArray("skills")
                ?: JSONArray()
            for (i in 0 until arr.length()) {
                when (val item = arr.opt(i)) {
                    is String -> {
                        val url = item.trim()
                        if (url.startsWith("http")) out += Library(url, "main")
                    }
                    is JSONObject -> {
                        val url = item.optString("repo").ifBlank { item.optString("url") }.trim()
                        val branch = item.optString("branch", "main").ifBlank { "main" }.trim()
                        if (url.startsWith("http")) out += Library(url, branch)
                    }
                }
            }
            if (out.isEmpty()) {
                val url = json.optString("repo").ifBlank { json.optString("url") }.trim()
                if (url.startsWith("http")) {
                    out += Library(url, json.optString("branch", "main").ifBlank { "main" })
                }
            }
        } else {
            trimmed.lines().map { it.trim() }
                .filter { it.startsWith("http") && !it.startsWith("#") }
                .forEach { out += Library(it, "main") }
        }
        return out.distinctBy { it.repoUrl }
    }

    private fun parseRepoCoordinates(url: String): Pair<String, String>? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "github.com" && host != "www.github.com") return null
        val parts = uri.path.trim('/').split('/')
        if (parts.size < 2) return null
        val user = parts[0]
        val repository = parts[1].removeSuffix(".git")
        return if (user.isBlank() || repository.isBlank()) null else user to repository
    }

    // ─── Network ───────────────────────────────────────────────────────────

    /** gh-proxy.com first, direct connection second. */
    private fun fetch(url: String, preferProxy: Boolean = true): ByteArray? {
        val candidates = if (!preferProxy || url.startsWith(GH_PROXY)) {
            listOf(url)
        } else {
            listOf(GH_PROXY + url, url)
        }
        for (candidate in candidates) {
            val bytes = httpGet(candidate)
            if (bytes != null) return bytes
            Log.w(TAG, "fetch failed: $candidate")
        }
        return null
    }

    private fun fetchArchive(user: String, repository: String, configuredBranch: String): ByteArray? {
        val branches = listOf(configuredBranch, "main", "master")
            .filter { it.isNotBlank() }
            .distinct()
        for (branch in branches) {
            val githubZip = "https://github.com/$user/$repository/archive/refs/heads/$branch.zip"
            val codeloadZip = "https://codeload.github.com/$user/$repository/zip/refs/heads/$branch"
            // Proxy variants first, then direct — for each branch.
            val candidates = listOf(
                GH_PROXY + githubZip,
                GH_PROXY + codeloadZip,
                githubZip,
                codeloadZip,
            )
            for (candidate in candidates) {
                val bytes = httpGet(candidate) ?: continue
                if (!isZip(bytes)) continue
                Log.i(TAG, "archive fetched via $candidate (${bytes.size} bytes)")
                return bytes
            }
        }
        return null
    }

    private fun httpGet(url: String): ByteArray? = try {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.bytes()?.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "httpGet $url: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    /** A proxy that answers 200 with an HTML error page must not be mistaken
     *  for a downloaded archive. */
    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())
}

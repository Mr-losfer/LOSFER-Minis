package com.openminis.app.ui.settings

import android.content.Context
import com.openminis.app.data.model.ProviderCredential
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.data.repository.ProviderRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [LOSFER] One-click LOSFER gateway setup.
 *
 * LOSFER exposes an OpenAI-compatible API at https://api.losfer.cn/v1.
 * One-click flow:
 *   1. the user pastes an API key (obtained at api.losfer.cn/pc/api-list/keys/);
 *   2. [validateKey] probes GET /v1/models with the key — a 200 means the key
 *      works, 401/403 means it does not;
 *   3. [addToRepository] creates an OpenAI-protocol provider instance pointed
 *      at the LOSFER base URL, saves the key and refreshes the model list so
 *      the provider is immediately usable.
 */
object LOSFERSetup {

    /** Base host; the app appends /v1 (ProviderInstance.effectiveBaseURL). */
    const val BASE_URL = "https://api.losfer.cn"

    /** Web page where users create an API key. */
    const val KEY_URL = "https://api.losfer.cn/pc/api-list/keys/"

    const val PROVIDER_LABEL = "LOSFER"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .build()
    }

    /**
     * Probe GET {BASE}/v1/models with the key. Returns true only on HTTP 200
     * (the body lists models; a 401/403 key is invalid, anything else is a
     * network/server problem reported separately).
     */
    suspend fun validateKey(apiKey: String): KeyCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/v1/models")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> KeyCheck.Valid
                    401, 403 -> KeyCheck.Invalid
                    else -> KeyCheck.NetworkError("HTTP ${response.code}")
                }
            }
        }.getOrElse { KeyCheck.NetworkError(it.message ?: "network error") }
    }

    /**
     * Adds (or refreshes) the LOSFER provider in [repo]. When an instance
     * already points at the LOSFER base URL its key is replaced and the model
     * list is refreshed; otherwise a new OpenAI-protocol instance is created.
     * Returns the instance the key was stored on.
     */
    suspend fun addToRepository(
        context: Context,
        repo: ProviderRepository,
        apiKey: String,
    ): ProviderInstance {
        val existing = repo.instances.firstOrNull {
            it.providerType == ProviderType.openAI &&
                it.customBaseURL?.trimEnd('/') == BASE_URL
        }
        val instance = existing?.copy() ?: ProviderInstance(
            id = UUID.randomUUID().toString(),
            label = PROVIDER_LABEL,
            providerType = ProviderType.openAI,
            credentialType = ProviderCredential.apiKey,
            customBaseURL = BASE_URL,
            appendV1Suffix = true,
        )
        if (existing == null) {
            repo.addInstance(instance)
        } else {
            repo.updateInstance(instance)
        }
        repo.saveApiKey(instance.id, apiKey.trim())
        repo.refreshModels(instance, forceRefresh = true)
        return instance
    }
}

sealed class KeyCheck {
    data object Valid : KeyCheck()
    data object Invalid : KeyCheck()
    data class NetworkError(val message: String) : KeyCheck()
}

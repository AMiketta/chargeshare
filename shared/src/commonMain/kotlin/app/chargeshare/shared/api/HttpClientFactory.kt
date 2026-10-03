package app.chargeshare.shared.api

import app.chargeshare.shared.model.ApiError
import app.chargeshare.shared.model.TokenPair
import app.chargeshare.shared.model.RefreshRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json

/**
 * Baut den Ktor-[HttpClient] fuer die ChargeShare-API.
 *
 * @param baseUrl Server-Wurzel ohne `/api/v1`, z. B. `https://api.chargeshare.app` oder `http://10.0.2.2:8080`
 * @param engine optional (Tests: `MockEngine`); sonst wird die Engine der Plattform verwendet
 */
fun createHttpClient(
    baseUrl: String,
    tokenStore: TokenStore,
    engine: HttpClientEngine? = null,
    extra: HttpClientConfig<*>.() -> Unit = {},
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false

        install(ContentNegotiation) { json(ChargeShareJson) }

        defaultRequest {
            url(baseUrl.trimEnd('/') + "/")
            accept(ContentType.Application.Json)
        }

        // Nicht-2xx -> ApiException mit Backend-Fehlertext
        install(HttpCallValidator) {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    val err = runCatching { response.body<ApiError>() }.getOrNull()
                    throw ApiException(
                        status = response.status.value,
                        code = err?.code,
                        message = err?.message ?: response.status.description,
                    )
                }
            }
        }

        // Bearer-Auth mit automatischem Refresh bei 401
        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokenStore.accessToken()
                    val refresh = tokenStore.refreshToken()
                    if (access != null && refresh != null) BearerTokens(access, refresh) else null
                }
                refreshTokens {
                    val refresh = tokenStore.refreshToken() ?: return@refreshTokens null
                    val pair = runCatching {
                        client.post("api/v1/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                        }.body<TokenPair>()
                    }.getOrNull()
                    if (pair == null) {
                        tokenStore.clear()
                        null
                    } else {
                        tokenStore.save(pair.accessToken, pair.refreshToken)
                        BearerTokens(pair.accessToken, pair.refreshToken)
                    }
                }
                // Oeffentliche Endpunkte (Login, Suche) brauchen keinen Token vorab
                sendWithoutRequest { request ->
                    val path = request.url.build().encodedPath
                    path.contains("/api/v1/") && AUTH_PUBLIC_SUFFIXES.none { path.endsWith(it) }
                }
            }
        }
        extra()
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}

private val AUTH_PUBLIC_SUFFIXES = listOf("/auth/login", "/auth/register", "/auth/apple", "/auth/refresh")

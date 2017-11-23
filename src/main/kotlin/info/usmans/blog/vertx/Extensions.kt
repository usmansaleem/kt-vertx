package info.usmans.blog.vertx

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import java.net.URI

fun HttpServerRequest.redirectToSecure(publicHttpsPort: Int = 443, redirectCode: Int = 302) {
    val hostName: String = if (this.absoluteURI().isNullOrBlank())
        "usmans.info"
    else
        URI(this.absoluteURI()).host ?: "usmans.info"

    val path: String = if (this.absoluteURI().isNullOrBlank())
        ""
    else
        URI(this.absoluteURI()).path ?: ""

    val url: String = if (publicHttpsPort == 443) "https://${hostName}${path}" else "https://${hostName}:${publicHttpsPort}${path}"
    this.response().putHeader("location", url).setStatusCode(redirectCode).end()
}

fun HttpServerRequest.getOAuthRedirectURI(path: String): String {
    val redirectURI = URI(absoluteURI() ?: "https://usmans.info")
    return "https://" + redirectURI.authority + path
}

/**
 * Extension to the HTTP response to output JSON objects.
 */
fun HttpServerResponse.sendJson(json: String) {
    this.putHeader("Content-Type", "application/json; charset=utf-8").end(json)
}

/**
 * Extension to the HTTP response to output plain text.
 */
fun HttpServerResponse.sendPlain(plain: String) {
    this.putHeader("Content-Type", "text/plain; charset=utf-8").end(plain)
}

fun HttpServerResponse.endWithErrorJson(msg: String) {
    this.setStatusCode(400).sendJson(Json.encode(msg))
}
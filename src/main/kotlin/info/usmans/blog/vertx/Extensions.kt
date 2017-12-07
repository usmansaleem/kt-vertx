package info.usmans.blog.vertx

import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import java.net.URI

fun HttpServerRequest.redirectToSecure(friendlyUrl: String? = null) {
    val hostName: String = if (this.absoluteURI().isNullOrBlank())
        SYS_BLOG_FALLBACK_REDIRECT_HOST
    else
        URI(this.absoluteURI()).host ?: SYS_BLOG_FALLBACK_REDIRECT_HOST

    val path = if(friendlyUrl == null) {
        //determine path from incoming request
        if (this.absoluteURI().isNullOrBlank())
        ""
        else
        URI(this.absoluteURI()).path ?: ""
    } else {
        "/usmansaleem/blog/$friendlyUrl"
    }

//    val path: String = if (this.absoluteURI().isNullOrBlank())
//        ""
//    else
//        URI(this.absoluteURI()).path ?: ""

    val url: String = if (SYS_REDIRECT_SSL_PORT == 443) "https://${hostName}${path}" else "https://${hostName}:${SYS_REDIRECT_SSL_PORT}${path}"
    this.response().putHeader("location", url).setStatusCode(302).end()
}

fun HttpServerRequest.getOAuthRedirectURI(path: String): String {
    val redirectURI = URI(absoluteURI() ?: "https://${SYS_BLOG_FALLBACK_REDIRECT_HOST}")
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
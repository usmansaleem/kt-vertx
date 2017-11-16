package info.usmans.blog.vertx

import io.vertx.core.http.HttpServerRequest
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

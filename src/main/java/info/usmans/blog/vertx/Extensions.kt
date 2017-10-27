package info.usmans.blog.vertx

import io.vertx.core.http.HttpServerRequest
import io.vertx.ext.web.Route
import java.net.URI

fun HttpServerRequest.redirectToSecure(publicHttpsPort: Int = 443, redirectCode: Int = 302) {
     val hostName: String = if(this.absoluteURI().isNullOrBlank())
         "usmans.info"
     else
         URI(this.absoluteURI()).host ?: "usmans.info"

    val path:String = if(publicHttpsPort == 443) "https://${hostName}" else "https://${hostName}:${publicHttpsPort}"
    this.response().putHeader("location", path).setStatusCode(redirectCode).end()
}

fun HttpServerRequest.getOAuthRedirectURI(path: String): String {
    val redirectURI = URI(absoluteURI()?: "https://usmans.info")
    return "https://" + redirectURI.authority + path
}

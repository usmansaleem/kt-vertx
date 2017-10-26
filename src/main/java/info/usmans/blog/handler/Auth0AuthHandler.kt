package info.usmans.blog.handler

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.AuthHandler
import io.vertx.ext.web.handler.impl.AuthHandlerImpl
import io.vertx.ext.web.handler.impl.HttpStatusException
import java.net.URI
import java.util.*



/**
 * Adapted from VertX OAuth2Handler to deal with Auth0 and modified logic of callback uri construction.
 */
class Auth0AuthHandler(private val oAuthProvider: OAuth2Auth, private val callback: Route) : AuthHandlerImpl(oAuthProvider), AuthHandler {
    private val supportJWT: Boolean
    private val scopes = HashSet<String>()
    private val type = Type.BEARER

    private var extraParams: JsonObject? = null

    private val unauthorizedHttpStatus = HttpStatusException(401)
    private val badRequestHttpStatus = HttpStatusException(400)



    init {
        //verify provider
        if (oAuthProvider.flowType != OAuth2FlowType.AUTH_CODE) {
            throw IllegalArgumentException("OAuth2Auth + Bearer Auth requires OAuth2 AUTH_CODE flow")

        }
        supportJWT = oAuthProvider.hasJWTToken()

        //setup callback right here as well
        setupCallback()

    }


    override fun parseCredentials(context: RoutingContext, handler: Handler<AsyncResult<JsonObject>>) {
        if (supportJWT) {
            parseAuthorization(context, true, Handler { parseAuthorization ->
                if (parseAuthorization.failed()) {
                    handler.handle(Future.failedFuture<JsonObject>(parseAuthorization.cause()))
                } else {
                    // if the provider supports JWT we can try to validate the Authorization header
                    val token: String? = parseAuthorization.result()

                    if (!token.isNullOrEmpty()) {
                        oAuthProvider.decodeToken(token) { decodeToken ->
                            if (decodeToken.failed()) {
                                handler.handle(Future.failedFuture(HttpStatusException(401, decodeToken.cause().message)))
                            } else {
                                context.setUser(decodeToken.result())
                                // continue
                                handler.handle(Future.succeededFuture())
                            }
                        }
                    }
                }
            })
        }
        handler.handle(Future.failedFuture(HttpStatusException(302, authURI(context.request().uri(), context.request().absoluteURI()))))
    }

    private fun authURI(redirectURL: String, absoluteURI: String): String {
        val config = JsonObject()
                .put("state", redirectURL)

        //obtain the host name from absolute URI instead of pinning it
        config.put("redirect_uri", getRedirectURI(URI(absoluteURI)))

        if (extraParams != null) {
            config.mergeIn(extraParams)
        }

        if (scopes.size > 0) {
            val scopesJsonArray = JsonArray()
            // scopes are passed as an array because the auth provider has the knowledge on how to encode them
            scopes.forEach { authority -> scopesJsonArray.add(authority) }
            config.put("scopes", scopesJsonArray)
        }

        return oAuthProvider.authorizeURL(config)
    }

    fun extraParams(extraParams: JsonObject?): Auth0AuthHandler {
        this.extraParams = extraParams
        return this
    }

    private fun setupCallback() {
        callback.method(HttpMethod.GET)

        callback.handler { ctx ->
            // Handle the callback of the flow
            val code = ctx.request().getParam("code")

            // code is a require value
            if (code == null) {
                ctx.fail(400)
                return@handler
            }

            val state = ctx.request().getParam("state")

            val config = JsonObject()
                    .put("code", code)


            //use the hostname from request based on how our site is accessed
            config.put("redirect_uri", getRedirectURI(URI(ctx.request().absoluteURI())))


            if (extraParams != null) {
                config.mergeIn(extraParams)
            }

            oAuthProvider.authenticate(config) { res ->
                if (res.failed()) {
                    ctx.fail(res.cause())
                } else {
                    ctx.setUser(res.result())
                    val session = ctx.session()
                    if (session != null) {
                        // the user has upgraded from unauthenticated to authenticated
                        // session should be upgraded as recommended by owasp
                        session.regenerateId()
                        // we should redirect the UA so this link becomes invalid
                        ctx.response()
                                // disable all caching
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                                .putHeader("Pragma", "no-cache")
                                .putHeader(HttpHeaders.EXPIRES, "0")
                                // redirect (when there is no state, redirect to home
                                .putHeader(HttpHeaders.LOCATION, state ?: "/")
                                .setStatusCode(302)
                                .end("Redirecting to " + (state ?: "/") + ".")
                    } else {
                        // there is no session object so we cannot keep state
                        ctx.reroute(state ?: "/")
                    }
                }
            }
        }



    }

    private fun getRedirectURI(redirectURI: URI) =
            redirectURI.scheme + "://" + redirectURI.authority + callback.path

    override fun addAuthority(authority: String): AuthHandler {
        scopes.add(authority)
        return this
    }

    override fun addAuthorities(authorities: Set<String>): AuthHandler {
        this.scopes.addAll(authorities)
        return this
    }


    /**
     * Copied from AuthorizationAuthHandler
     */
    private fun parseAuthorization(ctx: RoutingContext, optional: Boolean, handler: Handler<AsyncResult<String>>) {

        val request = ctx.request()
        val authorization = request.headers().get(HttpHeaders.AUTHORIZATION)

        if (authorization == null) {
            if (optional) {
                // this is allowed
                handler.handle(Future.succeededFuture())
            } else {
                handler.handle(Future.failedFuture(unauthorizedHttpStatus))
            }
            return
        }

        try {
            val idx = authorization.indexOf(' ')

            if (idx <= 0) {
                handler.handle(Future.failedFuture(badRequestHttpStatus))
                return
            }

            if (!type.`is`(authorization.substring(0, idx))) {
                handler.handle(Future.failedFuture(unauthorizedHttpStatus))
                return
            }

            handler.handle(Future.succeededFuture(authorization.substring(idx + 1)))
        } catch (e: RuntimeException) {
            handler.handle(Future.failedFuture(e))
        }

    }

}

// this should match the IANA registry: https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml
enum class Type constructor(private val label: String) {
    BASIC("Basic"),
    DIGEST("Digest"),
    BEARER("Bearer"),
    // these have no known implementation
    HOBA("HOBA"),
    MUTUAL("Mutual"),
    NEGOTIATE("Negotiate"),
    OAUTH("OAuth"),
    SCRAM_SHA_1("SCRAM-SHA-1"),
    SCRAM_SHA_256("SCRAM-SHA-256");

    fun `is`(other: String): Boolean {
        return label.equals(other, ignoreCase = true)
    }
}

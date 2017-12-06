package info.usmans.blog.vertx

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import info.usmans.blog.handler.Auth0AuthHandler
import info.usmans.blog.model.BlogItemUtil
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.Json
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.bridge.BridgeEventType
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.HandlebarsTemplateEngine
import java.io.File
import java.time.LocalDateTime

/**
 * Server Verticle - Launching Https server on 443 (and optionally unsecure server on 80).
 *
 */

class ServerVerticle : AbstractVerticle() {
    init {
        Json.mapper.apply {
            registerKotlinModule()
        }

        Json.prettyMapper.apply {
            registerKotlinModule()
        }
    }

    private val blogItemUtil = BlogItemUtil()
    private val templateEngine = HandlebarsTemplateEngine.create()
    private val checkoutDir = checkoutGist()

    /**
     * Redirect SSL Port where redirection happens. For example the server can be deployed on 8443, but it is behind a nginx or similar
     * reverse proxy, hence any redirection should go to 443 instead of 8443.
     *
     * If redirection to same port is required, set both publisSSLPort and deploySSLPort to same value.
     */
    private val redirectSSLPort = System.getProperty("redirectSSLPort", "443").toIntOrNull() ?: 443
    private val deploySSLPort = System.getProperty("deploySSLPort", "443").toIntOrNull() ?: 443

    /**
     * Controls deployment of unsecured http server (with redirect to secured one)
     */
    private val deployHttp = System.getenv("BLOG_DEPLOY_HTTP")
    private val deployPort = System.getProperty("deployPort", "80").toIntOrNull() ?: 80

    private val sslKeyValue = getSSLKeyValue()
    private val sslCertValue = getSSLCertValue()


    override fun start(startFuture: Future<Void>?) {
        val loadedBlogItemList = blogItemListFromJson(File(checkoutDir, "data.json").readText())
        if (loadedBlogItemList == null) {
            startFuture?.fail("Unable to load data json")
        } else {
            blogItemUtil.initBlogItemMaps(loadedBlogItemList)
            val router = createRouter()

            //create futures for async cordination
            val httpsServerFuture: Future<HttpServer> = Future.future()
            val httpServerVerticleFuture: Future<String> = Future.future()
            val netServerVerticleFuture: Future<String> = Future.future()

            //create http server
            createSecuredHttpServers(router, httpsServerFuture)

            //optionally deploy http verticle
            if(deployHttp != null) {
                vertx.deployVerticle(HttpServerVerticleWithForwardToSecure(deployPort, redirectSSLPort), httpServerVerticleFuture.completer())
            } else {
                httpServerVerticleFuture.complete()
            }

            //deploy the net server verticle
            vertx.deployVerticle(NetServerVerticle(sslCertValue, sslKeyValue), netServerVerticleFuture.completer())

            //wait for all of our services and verticles to start up before declaring this verticle as a success ...
            CompositeFuture.join(httpsServerFuture, httpServerVerticleFuture, netServerVerticleFuture).setHandler({ event ->
                if(event.succeeded()) {
                    println("All services and verticles have been deployed")
                    startFuture?.complete()
                } else {
                    startFuture?.fail(event.cause())
                }
            })
        }
    }

    private fun createRouter() = Router.router(vertx).apply {
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath

        //REST API routes
        get("/rest/blog/highestPage").handler(highestPageHandler(blogItemUtil))
        get("/rest/blog/blogCount").handler(blogCountHandler(blogItemUtil))
        get("/rest/blog/blogItems/:pageNumber").handler(pageNumberHandler(blogItemUtil))
        get("/rest/blog/blogItems").handler(blogItemsHandler(blogItemUtil))
        get("/rest/blog/blogItems/blogItem/:id").handler(blogItemByIdHandler(blogItemUtil))

        //sitemap (for Google)
        get("/sitemap.txt").handler(siteMapHandler(blogItemUtil))

        //forward /blog/id to /usmansaleem/blog/friendlyUrl
        get("/blog/:id").handler(blogByIdHandler(blogItemUtil, redirectSSLPort))
        //Individual Blog Entry from template engine ...
        get("/usmansaleem/blog/:url").handler(blogByFriendlyUrl(blogItemUtil, templateEngine))

        secureRoutes()

        //static pages
        route("/*").handler(StaticHandler.create()) //serve static contents from webroot folder on classpath
    }

    private fun Router.secureRoutes() {
        val oauthClientId = getOAuthClientId()
        val oauthClientSecret = getOAuthClientSecret()
        if (oauthClientId.isNullOrBlank() || oauthClientSecret.isNullOrBlank()) {
            println("OAuth is not setup because OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET is null")
        } else {
            //cookie handler and session handler for OAuth2 authz and authn
            route().handler(CookieHandler.create())
            route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))

            println("Securing routes under /protected ...")
            //1. Create our custom Auth Provider for Auth0
            val authProvider = OAuth2Auth.create(vertx, OAuth2FlowType.AUTH_CODE, OAuth2ClientOptions().apply {
                clientID = oauthClientId
                clientSecret = oauthClientSecret
                site = OAUTH_SITE
                tokenPath = OAUTH_TOKEN_PATH
                authorizationPath = OAUTH_AUTHZ_PATH
                userInfoPath = OAUTH_USERINFO_PATH
                isJwtToken = false
            })

            // We need a user session handler too to make sure
            // the user is stored in the session between requests
            route().handler(UserSessionHandler.create(authProvider))

            // we now protect the resource under the path "/protected"
            route("/protected/*").handler(
                    Auth0AuthHandler(authProvider, get("/callback"))
                            // for this resource we require that users have
                            // the authority to retrieve the user emails
                            .addAuthority("openid profile email")
            )

            get("/protected").handler(protectedPageByTemplateHandler(blogItemUtil, templateEngine))

            get("/protected/blog/edit/:id").handler(blogEditGetHandler(blogItemUtil, templateEngine))

            post("/protected/blog/edit/:blogId").blockingHandler(blogEditPostHandler(blogItemUtil, checkoutDir))

            get("/protected/blog/new").handler(blogNewGetHandler(blogItemUtil, templateEngine))

            post("/protected/blog/new").blockingHandler(blogNewPostHandler(blogItemUtil, checkoutDir))

            get("/protected/sendmessage").handler({ rc ->
                vertx.eventBus().publish("action-feed", "You got message")
                rc.response().sendPlain("Message published")
            })
        }
    }

    private fun createSecuredHttpServers(router: Router, httpServerFuture: Future<HttpServer>) {
        if (sslKeyValue != null && sslCertValue != null) {
            println("Deploying Http Server (SSL) on port $deploySSLPort")
            //deploy this verticle with SSL
            vertx.createHttpServer(getSSLOptions(sslKeyValue, sslCertValue, deploySSLPort)).apply {
                requestHandler(router::accept)
                listen(httpServerFuture.completer())
            }
        } else {
            httpServerFuture.fail("SSL Key or Value cannot be determined.")
        }
    }


    private fun getSSLOptions(blogKeyValue: String, blogCertValue: String, sslPort: Int): HttpServerOptions {
        return HttpServerOptions().apply {
            isSsl = true
            pemKeyCertOptions = PemKeyCertOptions().apply {
                keyValue = Buffer.buffer(blogKeyValue)
                certValue = Buffer.buffer(blogCertValue)
            }
            port = sslPort
            sslEngineOptions = OpenSSLEngineOptions()
        }
    }
}

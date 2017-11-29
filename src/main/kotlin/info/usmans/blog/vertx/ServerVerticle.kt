package info.usmans.blog.vertx

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import info.usmans.blog.handler.Auth0AuthHandler
import info.usmans.blog.model.BlogItemUtil
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.Json
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.HandlebarsTemplateEngine
import java.io.File

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
    
    private val publicSSLPort = System.getProperty("publicSSLPort", "443").toIntOrNull() ?: 443
    private val deploySSLPort = System.getProperty("deploySSLPort", "443").toIntOrNull() ?: 443
    private val deployPort = System.getProperty("deployPort", "80").toIntOrNull() ?: 80
    private val deployUnSecureServer = System.getenv("DEPLOY_UNSECURE_SERVER") != null

    override fun start(startFuture: Future<Void>?) {
        val loadedBlogItemList = blogItemListFromJson(File(checkoutDir, "data.json").readText())
        if (loadedBlogItemList == null) {
            startFuture?.fail("Unable to load data json")
        } else {
            blogItemUtil.initBlogItemMaps(loadedBlogItemList)
            val router = createRouter()
            createHttpServers(router, startFuture)
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
        get("/blog/:id").handler(blogByIdHandler(blogItemUtil, publicSSLPort))
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
        }
    }

    private fun createHttpServers(router: Router, startFuture: Future<Void>?) {
        val (keyValue, certValue) = Pair(getSSLKeyValue(), getSSLCertValue())

        if (keyValue != null && certValue != null) {
            println("Deploying Http Server (SSL) on port $deploySSLPort")
            //deploy this verticle with SSL
            vertx.createHttpServer(getSSLOptions(keyValue, certValue, deploySSLPort)).apply {
                requestHandler(router::accept)
                listen({ httpServerlistenHandler ->
                    if (httpServerlistenHandler.succeeded()) {
                        println("Http Server (SSL) on port $deploySSLPort deployed.")
                        if (deployUnSecureServer) {
                            println("Deploying Forwarding Verticle on 8080 with redirecting to $publicSSLPort...")
                            vertx.deployVerticle(ForwardingServerVerticle(deployPort, publicSSLPort), { verticleHandler ->
                                if (verticleHandler.succeeded())
                                    startFuture?.succeeded()
                                else
                                    startFuture?.fail(verticleHandler.cause())
                            })
                        }
                    } else
                        startFuture?.fail(httpServerlistenHandler.cause())

                })
            }
        } else {
            println("Deploying Http Server on port $deployPort")
            //deploy non secure server
            vertx.createHttpServer().apply {
                requestHandler(router::accept)
                listen(deployPort, { handler ->
                    if (handler.succeeded()) {
                        startFuture?.complete()
                    } else {
                        startFuture?.fail(handler.cause())
                    }
                })
            }
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

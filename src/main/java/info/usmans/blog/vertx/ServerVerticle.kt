package info.usmans.blog.vertx

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import info.usmans.blog.handler.Auth0AuthHandler
import info.usmans.blog.model.BlogItemMaps
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

/**
 * Server Verticle - Launching Http and Https server on port 8080 and 8443.
 *
 * To launch secure server on port 8443, following environment variables are required
 * BLOG_CERT_BASE64 - PEM Certificate further encoded in BASE64 with wrap 0
 * BLOG_KEY_BASE64 - RSA Private Key (non-encryoted) further encoded in BASE64 with wrap 0
 *
 * To configure protected routes, following environment variables are required
 * OAUTH_CLIENT_ID
 * OAUTH_CLIENT_SECRET
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

    private val blogItemsMap = BlogItemMaps()
    private val templateEngine = HandlebarsTemplateEngine.create()

    override fun start(startFuture: Future<Void>?) {
        readJsonData(vertx, DATA_JSON_GIST_TAG).send({ ar ->
            if (ar.succeeded()) {
                val loadedBlogItemList = blogItemListFromJson(ar.result().body())
                if(loadedBlogItemList == null) {
                    startFuture?.fail("Unable to load data json")
                }else {
                    blogItemsMap.initBlogItemMaps(loadedBlogItemList)

                    val router = createRouter()

                    createHttpServers(router, startFuture)
                }
            } else {
                startFuture?.fail(ar.cause())
            }
        })
    }

    private fun createRouter() = Router.router(vertx).apply {
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath

        //REST API routes
        get("/rest/blog/highestPage").handler(highestPageHandler(blogItemsMap))
        get("/rest/blog/blogCount").handler(blogCountHandler(blogItemsMap))
        get("/rest/blog/blogItems/:pageNumber").handler(pageNumberHandler(blogItemsMap))
        get("/rest/blog/blogItems").handler(blogItemsHandler(blogItemsMap))
        get("/rest/blog/blogItems/blogItem/:id").handler(blogItemByIdHandler(blogItemsMap))

        //sitemap (for Google)
        get("/sitemap.txt").handler(siteMapHandler(blogItemsMap))

        //Individual Blog Entry from template engine ...
        get("/blog/:id").handler(blogByTemplateHandler(blogItemsMap, templateEngine))

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

            get("/protected").handler(protectedPageByTemplateHandler(blogItemsMap, templateEngine))

            get("/protected/blog/edit/:id").handler(blogEditByTemplateHandler(blogItemsMap, templateEngine))

            get("/protected/refresh/:tag").handler(refreshBlogJsonHandler(vertx, blogItemsMap))

        }
    }

    private fun createHttpServers(router: Router, startFuture: Future<Void>?) {
        val (keyValue, certValue) = Pair(getSSLKeyValue(), getSSLCertValue())

        if (keyValue != null && certValue != null) {
            println("Deploying Http Server (SSL) on port 8443")
            //deploy this verticle with SSL
            vertx.createHttpServer(getSSLOptions(keyValue, certValue, 8443)).apply {
                requestHandler(router::accept)
                listen({ httpServerlistenHandler ->
                    if (httpServerlistenHandler.succeeded()) {
                        println("Http Server (SSL) on port 8443 deployed.")
                        if(deployUnsecureServer()) {
                            val redirectSSLPort = System.getProperty("redirectSSLPort", "443").toIntOrNull() ?: 443
                            println("Deploying Forwarding Verticle on 8080 with redirecting to ${redirectSSLPort}...")
                            vertx.deployVerticle(ForwardingServerVerticle(redirectSSLPort), { verticleHandler ->
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
            println("Deploying Http Server on port 8080")
            //deploy non secure server
            vertx.createHttpServer().apply {
                requestHandler(router::accept)
                listen(8080, { handler ->
                    if (handler.succeeded()) {
                        startFuture?.complete()
                    } else {
                        startFuture?.fail(handler.cause())
                    }
                })
            }
        }
    }




    private fun getSSLOptions(blogKeyValue: String, blogCertValue: String, sslPort: Int = 8443): HttpServerOptions {
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


package info.usmans.blog.vertx

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import info.usmans.blog.handler.Auth0AuthHandler
import info.usmans.blog.model.BlogItem
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

internal const val ITEMS_PER_PAGE = 10
internal const val DATA_JSON_GIST_TAG = "5b40796dfe53e916486b23afb29f8bcf68ff0f87" //calculated from https://rawgit.com
internal const val OAUTH_SITE = "https://uzi.au.auth0.com"
internal const val OAUTH_TOKEN_PATH = "/oauth/token"
internal const val OAUTH_AUTHZ_PATH = "/authorize"
internal const val OAUTH_USERINFO_PATH = "/userinfo"

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

    private var pagedBlogItems: Map<Int, List<BlogItem>> = mapOf()
    private var blogItemMap = TreeMap<Long, BlogItem>()

    private var highestPage: String = ""
    private var blogCount: String = ""

    private fun dataJsonUrl(tag: String = DATA_JSON_GIST_TAG): String {
        return "https://cdn.rawgit.com/usmansaleem/9bb0e98d05caa0afcc649b6593733edf/raw/$tag/data.json"
    }

    override fun start(startFuture: Future<Void>?) {
        //read data.json
        val jsonUrl = dataJsonUrl()
        val options = WebClientOptions()
        options.isKeepAlive = false
        val client = WebClient.create(vertx, options)
        println("Reading data.json from $jsonUrl")
        client.getAbs(jsonUrl).send({ ar ->
            if (ar.succeeded()) {
                val body = ar.result().body()
                val blogItemMap: Map<Long, BlogItem>? = try {
                    initializeBlogItemMap(body)
                } catch(e: JsonParseException) {
                    startFuture?.fail(e)
                    null
                }

                if(blogItemMap != null) {
                    this.blogItemMap.putAll(blogItemMap)
                    initPagedBlogItems()

                    //construct router and http server
                    val router = createRouter()

                    createHttpServers(router, startFuture)
                }
            } else {
                println("Reading Failed for $jsonUrl")
                startFuture?.fail(ar.cause())
            }
        })
    }

    private fun createHttpServers(router: Router, startFuture: Future<Void>?) {
        val (keyValue, certValue) = Pair(getKeyValue(), getCertValue())
        val redirectSSLPort = System.getProperty("redirectSSLPort", "443").toIntOrNull() ?: 443
        if (keyValue != null && certValue != null) {
            println("Deploying Http Server (SSL) on port 8443")
            //deploy this verticle with SSL
            vertx.createHttpServer(getSSLOptions(keyValue, certValue, 8443)).apply {
                requestHandler(router::accept)
                listen({ httpServerlistenHandler ->
                    if (httpServerlistenHandler.succeeded()) {
                        //deploy nonsecure forwarding verticle on port 8080...
                        println("Http Server (SSL) on port 8443 Deployed. Deploying Forwarding Verticle on 8080...")
                        vertx.deployVerticle(ForwardingServerVerticle(redirectSSLPort), { verticleHandler ->
                            if (verticleHandler.succeeded())
                                startFuture?.succeeded()
                            else
                                startFuture?.fail(verticleHandler.cause())
                        })

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

    private fun getCertValue(): String? {
        val certValueEncoded: String? = System.getenv("BLOG_CERT_BASE64")
        return if (certValueEncoded != null) try {
            Base64.getDecoder().decode(certValueEncoded).toString(UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
        else
            null
    }

    private fun getKeyValue(): String? {
        val keyValueEncoded: String? = System.getenv("BLOG_KEY_BASE64")
        return if (keyValueEncoded != null) try {
            Base64.getDecoder().decode(keyValueEncoded).toString(UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
        else
            null
    }

    private fun getOAuthCred() = Pair(System.getenv("OAUTH_CLIENT_ID"), System.getenv("OAUTH_CLIENT_SECRET"))

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

    private fun initializeBlogItemMap(dataJson: Buffer): Map<Long, BlogItem> {
        val blogItemsOrig: List<BlogItem> = Json.mapper.readValue(dataJson.toString())
        return blogItemsOrig.associateBy({ it.id }) { it }
    }

    private fun initPagedBlogItems() {
        val blogItemCount = blogItemMap.size
        val itemsOnLastPage = blogItemCount % ITEMS_PER_PAGE
        val totalPagesCount = if (itemsOnLastPage == 0) blogItemCount / ITEMS_PER_PAGE else blogItemCount / ITEMS_PER_PAGE + 1

        //index all pages for quick access
        val blogItems: List<BlogItem> = blogItemMap.values.toList()
        val pagedBlogItems = mutableMapOf<Int, List<BlogItem>>()

        for (pageNumber in 1..totalPagesCount) {
            var endIdx = pageNumber * ITEMS_PER_PAGE
            val startIdx = endIdx - ITEMS_PER_PAGE

            if (pageNumber == totalPagesCount && itemsOnLastPage != 0) {
                endIdx = startIdx + itemsOnLastPage
            }
            pagedBlogItems.put(pageNumber, blogItems.subList(startIdx, endIdx).sortedByDescending(BlogItem::id))
        }

        //set class level variables which is read by REST services
        highestPage = totalPagesCount.toString()
        blogCount = blogItemCount.toString()
        this.pagedBlogItems = pagedBlogItems
    }

    private fun createRouter() = Router.router(vertx).apply {
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory

        //our standard routes
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath
        get("/rest/blog/highestPage").handler(handlerHighestPage)
        get("/rest/blog/blogCount").handler(handlerBlogCount)
        get("/rest/blog/blogItems/:pageNumber").handler(handlerMainBlogByPageNumber)
        get("/rest/blog/blogItems").handler(handlerBlogItemsJson)
        get("/rest/blog/blogItems/blogItem/:id").handler(handlerBlogItemById)
        get("/sitemap.txt").handler({ rc ->
            rc.response().putHeader("Content-Type", "text; charset=utf-8").end(blogItemMap.keys.joinToString("\n") { "${rc.request().
                    getOAuthRedirectURI("/#!/blog/")}$it" })
        })

        secureRoutes()

        //static pages
        route("/*").handler(StaticHandler.create()) //serve static contents from webroot folder on classpath
    }

    private fun Router.secureRoutes() {
        val (oauthClientId, oauthClientSecret) = getOAuthCred()
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

            get("/protected").handler({ ctx ->
                val accessToken: AccessToken? = ctx.user() as AccessToken
                //for now simply dump the access token
                val htmlBody = "<html><body><p>${accessToken?.principal()?.encodePrettily()}</p><p>Refresh Data by calling /protected/refresh/tag from rawgit.com</p></body></html>"
                ctx.response().putHeader("Content-Type", "text/html").end(htmlBody)
            })

            get("/protected/refresh/:tag").handler({rc ->
               val jsonUrl = dataJsonUrl(rc.request().getParam("tag") ?: DATA_JSON_GIST_TAG)

                val options = WebClientOptions()
                options.isKeepAlive = false
                val client = WebClient.create(vertx, options)
                client.getAbs(jsonUrl).send({ ar ->
                    if (ar.succeeded()) {
                        val body = ar.result().body()
                        try {
                            val tmpMap = initializeBlogItemMap(body)
                            blogItemMap = TreeMap()
                            blogItemMap.putAll(tmpMap)
                            initPagedBlogItems()
                            rc.response().sendJson(Json.encodePrettily(blogItemMap.values.toList().sortedByDescending { it.id }))
                        } catch(e: JsonParseException) {
                            rc.response().endWithError("Error parsing $jsonUrl")
                        }
                    } else {
                        rc.response().endWithError("Error loading $jsonUrl: " + ar.cause().message)
                    }
                })
            })

        }
    }

    private val handlerBlogCount = Handler<RoutingContext> { req -> req.response().sendPlain(blogCount) }

    private val handlerHighestPage = Handler<RoutingContext> { req -> req.response().sendPlain(highestPage) }

    private val handlerMainBlogByPageNumber = Handler<RoutingContext> { req ->
        val pageNumber = req.request().getParam("pageNumber").toIntOrNull() ?: 0

        if (pageNumber >= 1) {
            val pagedBlogItemsList = pagedBlogItems[pageNumber]
            if (pagedBlogItemsList != null) {
                req.response().sendJson(Json.encode(pagedBlogItemsList))
            } else {
                req.response().endWithInvalidPageNumberError()
            }
        } else {
            req.response().endWithInvalidPageNumberError()
        }
    }

    private val handlerBlogItemsJson = Handler<RoutingContext> { req ->
        req.response().sendJson(Json.encodePrettily(blogItemMap.values.toList().sortedByDescending { it.id }))
    }

    private val handlerBlogItemById = Handler<RoutingContext> { req ->
        val blogItemId = req.request().getParam("id").toLongOrNull() ?: 0
        if (blogItemMap.contains(blogItemId)) {
            req.response().sendJson(Json.encodePrettily(blogItemMap[blogItemId]))
        } else {
            req.response().endWithInvalidIdError()
        }
    }

    /**
     * Extension to the HTTP response to output JSON objects.
     */
    private fun HttpServerResponse.sendJson(json: String) {
        this.putHeader("Content-Type", "application/json; charset=utf-8").end(json)
    }

    /**
     * Extension to the HTTP response to output plain text.
     */
    private fun HttpServerResponse.sendPlain(plain: String) {
        this.putHeader("Content-Type", "text/plain; charset=utf-8").end(plain)
    }

    private fun HttpServerResponse.endWithInvalidPageNumberError() {
        endWithError("Bad Request - Invalid Page Number")
    }

    private fun HttpServerResponse.endWithInvalidIdError() {
        endWithError("Bad Request - Invalid Id")
    }

    private fun HttpServerResponse.endWithError(msg: String) {
        this.setStatusCode(400).end(msg)
    }
}

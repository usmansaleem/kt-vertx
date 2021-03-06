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
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.HandlebarsTemplateEngine
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringReader
import java.util.*

/**
 * Server Verticle - Launching Https server on 443 (and optionally unsecure server on 80).
 *
 */

class ServerVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger("info.usmans.blog.vertx.ServerVerticle")

    private val sslKeyValue = getSSLKeyValue()
    private val sslCertValue = getSSLCertValue()

    private val blogItemUtil: BlogItemUtil
    private val templateEngine: HandlebarsTemplateEngine

    private val gitProperties: Properties = Properties()

    init {
        logger.info("***** Init ServerVerticle ****")
        Json.mapper.apply {
            registerKotlinModule()
        }

        Json.prettyMapper.apply {
            registerKotlinModule()
        }

        blogItemUtil = BlogItemUtil()
        templateEngine = HandlebarsTemplateEngine.create()
    }


    override fun start(startFuture: Future<Void>?) {
        vertx.exceptionHandler { t ->
            logger.error("Unexpected Error handled by VertX: ${t.message}", t)
        }

        //checkout git dir which can take some time ...
        vertx.executeBlocking<File>({ future ->
            val checkoutDir = checkoutGist()
            logger.info("Git directory checked out {}", checkoutDir.path)

            val gitProp = vertx.fileSystem().readFileBlocking("git.properties")
            gitProperties.load(StringReader(gitProp.toString()))
            logger.info("Git Properties loaded.")

            future.complete(checkoutDir)
        }, { res ->
            if (res.succeeded()) {
                val checkoutDir = res.result()
                logger.info("Loading Json...")
                val loadedBlogItemList = blogItemListFromJson(File(checkoutDir, "data.json").readText())
                if (loadedBlogItemList == null) {
                    startFuture?.fail("Unable to load data json")
                } else {
                    logger.info("Init blog items maps ...")
                    blogItemUtil.initBlogItemMaps(loadedBlogItemList)


                    logger.info("Creating VertX Routers ...")
                    val router = createRouter(checkoutDir)

                    //create futures for async cordination
                    val httpsServerFuture: Future<HttpServer> = Future.future()
                    val httpServerVerticleFuture: Future<String> = Future.future()
                    val netServerVerticleFuture: Future<String> = Future.future()

                    //create http server
                    logger.info("Creating Http(s) Server ...")
                    createSecuredHttpServers(router, httpsServerFuture)

                    //optionally deploy http verticle
                    if (ENV_BLOG_DEPLOY_HTTP != null) {
                        vertx.deployVerticle(HttpServerVerticleWithForwardToSecure(), httpServerVerticleFuture.completer())
                    } else {
                        httpServerVerticleFuture.complete()
                    }

                    //deploy the net server verticle
                    logger.info("Deploying NetServer verticle ...")
                    vertx.deployVerticle(NetServerVerticle(sslCertValue, sslKeyValue), netServerVerticleFuture.completer())

                    //wait for all of our services and verticles to start up before declaring this verticle as a success ...
                    CompositeFuture.join(httpsServerFuture, httpServerVerticleFuture, netServerVerticleFuture).setHandler({ event ->
                        if (event.succeeded()) {
                            logger.info("All services and verticles have been deployed")
                            startFuture?.complete()
                        } else {
                            startFuture?.fail(event.cause())
                        }
                    })
                }

            } else {
                startFuture?.fail(res.cause())
            }
        })
    }

    private fun createRouter(checkoutDir: File) = Router.router(vertx).apply {
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath

        //REST API routes
        get("/rest/blog/highestPage").handler(highestPageHandlerGet(blogItemUtil))
        get("/rest/blog/blogCount").handler(blogCountHandlerGet(blogItemUtil))
        get("/rest/blog/blogItems/:pageNumber").handler(pageNumberHandlerGet(blogItemUtil))
        get("/rest/blog/blogItems").handler(blogItemsHandlerGet(blogItemUtil))
        get("/rest/blog/blogItems/blogItem/:id").handler(blogItemByIdHandlerGet(blogItemUtil))

        //sitemap (for Google)
        get("/sitemap.txt").handler(siteMapHandlerGet(blogItemUtil))

        //forward /blog/id to /usmansaleem/blog/friendlyUrl
        get("/blog/:id").handler(redirectToFriendlyUrlHandlerGet(blogItemUtil))
        //Individual Blog Entry from template engine ...
        get("/usmansaleem/blog/:url").handler(blogByFriendlyUrlGet(blogItemUtil, templateEngine))

        secureRoutes(checkoutDir)

        //home automation
        post("/ifttt/webhook").handler(iftttWebHookPostHandler(vertx))

        get("/admin/info").handler {
            it.response().sendJson(Json.encodePrettily(gitProperties))
        }

        //static pages
        route("/*").handler(StaticHandler.create()) //serve static contents from webroot folder on classpath
    }

    private fun Router.secureRoutes(checkoutDir: File) {
        val oauthClientId = ENV_OAUTH_CLIENT_ID
        val oauthClientSecret = ENV_OAUTH_CLIENT_SECRET
        if (oauthClientId.isNullOrBlank() || oauthClientSecret.isNullOrBlank()) {
            logger.info("/protected not available because OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET environment variables are not defined")
        } else {
            logger.info("Securing routes under /protected ...")

            //cookie handler and session handler for OAuth2 authz and authn
            route().handler(CookieHandler.create())
            route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))

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

            get("/protected").handler(protectedPageByTemplateHandlerGet(blogItemUtil, templateEngine))

            get("/protected/blog/edit/:id").handler(blogEditHandlerGet(blogItemUtil, templateEngine))

            post("/protected/blog/edit/:blogId").blockingHandler(blogEditHandlerPost(blogItemUtil, checkoutDir))

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
            logger.info("Deploying Http Server (SSL) on port $SYS_DEPLOY_SSL_PORT")
            //deploy this verticle with SSL
            vertx.createHttpServer(getSSLOptions(sslKeyValue, sslCertValue)).apply {
                exceptionHandler { t ->
                    logger.error("Unexpected error in Http(s) Server Verticle: ${t.message}", t)
                }
                requestHandler(router::accept)
                listen(httpServerFuture.completer())
            }
        } else {
            httpServerFuture.fail("SSL Key or Value cannot be determined.")
        }
    }


    private fun getSSLOptions(blogKeyValue: String, blogCertValue: String): HttpServerOptions {
        return HttpServerOptions().apply {
            isSsl = true
            pemKeyCertOptions = PemKeyCertOptions().apply {
                certValue = Buffer.buffer(blogCertValue)
                keyValue = Buffer.buffer(blogKeyValue)
            }
            addEnabledSecureTransportProtocol("TLSv1.1")
            addEnabledSecureTransportProtocol("TLSv1.2")
            port = SYS_DEPLOY_SSL_PORT
            if (ENV_BLOG_ENABLE_OPENSSL)
                sslEngineOptions = OpenSSLEngineOptions()
        }
    }
}

package info.usmans.blog.vertx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import info.usmans.blog.model.BlogItem
import info.usmans.blog.model.Category
import io.vertx.core.AbstractVerticle
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.handler.StaticHandler

const val ITEMS_PER_PAGE = 10

class ServerVerticle : AbstractVerticle() {
    private val defaultCategories by lazy {
        listOf(Category(1, "Java"),
                Category(2, "PostgreSQL"),
                Category(3, "Linux"),
                Category(4, "IT"),
                Category(5, "General"),
                Category(6, "JBoss")
        )
    }

    private lateinit var blogItems: List<BlogItem>

    private var totalPages = 1
    private var itemsOnLastPage = ITEMS_PER_PAGE
    private var blogItemSize = 0

    override fun start(startFuture: Future<Void>?) {
        val keyValue: String? = System.getenv("keyValue")
        val certValue: String? = System.getenv("certValue")

        if(keyValue == null || certValue == null) {
            startFuture?.fail("keyValue and/or certValue environment variables are null")
            return
        }

        //load our blog from data.json
        initData()

        //construct router and http server
        val router = createRouter()

        val httpServerFuture = Future.future<HttpServer>()
        val httpsServerFuture = Future.future<HttpServer>()

        //start http on 8080
        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(8080, httpServerFuture.completer())

        //start https on 8443
        vertx.createHttpServer(getSSLOptions(keyValue, certValue)).requestHandler { router.accept(it) }
                .listen(httpsServerFuture.completer())

        CompositeFuture.all(httpServerFuture, httpsServerFuture).setHandler({ ar ->
            if (ar.succeeded()) {
                println("Up up and away on port 8080/8443 ...")
                startFuture?.complete()
            } else {
                // At least one server failed
                startFuture?.fail(ar.cause())
            }
        })
    }

    private fun getSSLOptions(keyValue: String?, certValue: String?): HttpServerOptions {
        //ssl certificate options
        val pemKeyCertOptions = PemKeyCertOptions()
        pemKeyCertOptions.keyValue = Buffer.buffer(keyValue)
        pemKeyCertOptions.certValue = Buffer.buffer(certValue)

        val httpServerOptions = HttpServerOptions()
        httpServerOptions.isSsl = true
        httpServerOptions.pemKeyCertOptions = pemKeyCertOptions
        httpServerOptions.port = 8443
        return httpServerOptions
    }

    private fun initData() {
        val dataJson = ClassLoader.getSystemResource("data.json").readText()
        blogItems = ObjectMapper().registerModule(KotlinModule()).readValue(dataJson)
        blogItemSize = blogItems.size
        totalPages = blogItemSize / ITEMS_PER_PAGE
        itemsOnLastPage = blogItemSize % ITEMS_PER_PAGE
        if (itemsOnLastPage != 0) {
            totalPages++
        }
    }

    private fun createRouter() = Router.router(vertx).apply {
        route().redirectToHttpsHandler()
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath
        get("/rest/blog/highestPage").handler(handlerHighestPage)
        get("/rest/blog/listCategories").handler(handlerListCategories)
        get("/rest/blog/blogCount").handler(handlerBlogCount)
        get("/rest/blog/blogItems/:pageNumber").blockingHandler(handlerMainBlogByPageNumber)
        route("/*").handler(StaticHandler.create()) //serve static contents from webroot folder on classpath
    }

    private val handlerListCategories = Handler<RoutingContext> { req ->
        req.response().endWithJson(defaultCategories)
    }

    private val handlerBlogCount = Handler<RoutingContext> { req -> req.response().putHeader("content-type", "text/plain").end(blogItemSize.toString()) }

    private val handlerHighestPage = Handler<RoutingContext> { req -> req.response().putHeader("content-type", "text/plain").end(totalPages.toString()) }

    private val handlerMainBlogByPageNumber = Handler<RoutingContext> { req ->
        val pageNumber = req.request().getParam("pageNumber").toIntOrNull()
        if (pageNumber != null && pageNumber >= 1 && pageNumber <= totalPages) {
            var endIdx = pageNumber * ITEMS_PER_PAGE
            val startIdx = endIdx - ITEMS_PER_PAGE

            if (pageNumber == totalPages && itemsOnLastPage != 0) {
                endIdx = startIdx + itemsOnLastPage
            }
            req.response().endWithJson(blogItems.subList(startIdx, endIdx))

        } else {
            req.response().endWithError()

        }
    }

    /**
     * Extension to the HTTP response to output JSON objects.
     */
    private fun HttpServerResponse.endWithJson(obj: Any) {
        this.putHeader("Content-Type", "application/json; charset=utf-8").end(Json.encodePrettily(obj))
    }

    private fun HttpServerResponse.endWithError() {
        this.setStatusCode(400).end("Bad Request - Invalid Page Number")
    }
}

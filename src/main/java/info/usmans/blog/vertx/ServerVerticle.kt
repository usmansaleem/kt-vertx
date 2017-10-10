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
    private val indexedEntries = HashMap<Int, List<BlogItem>>()

    private var totalPages = 1
    private var itemsOnLastPage = ITEMS_PER_PAGE
    private var blogItemSize = 0

    override fun start(startFuture: Future<Void>?) {
        val keyValue: String? = System.getProperty("keyValue")
        val certValue: String? = System.getProperty("certValue")
        val redirectSSLPort = System.getProperty("redirectSSLPort", "443").toIntOrNull() ?: 443
        val deploySSL = keyValue != null && certValue != null


        //load our blog from data.json
        initData()

        //construct router and http server
        val router = createRouter(redirectSSLPort, deploySSL)

        val httpServerFuture = Future.future<HttpServer>()
        val httpsServerFuture = Future.future<HttpServer>()

        //start http on 8080
        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(8080, httpServerFuture.completer())

        //start https on 8443
        if (deploySSL) {
            vertx.createHttpServer(getSSLOptions(keyValue, certValue, 8443)).requestHandler { router.accept(it) }
                    .listen(httpsServerFuture.completer())
        } else {
            httpsServerFuture.complete()
        }

        CompositeFuture.all(httpServerFuture, httpsServerFuture).setHandler({ ar ->
            if (ar.succeeded()) {
                println("Deployed on 8080: true")
                println("SSL Deployed on 8443: " + deploySSL)
                startFuture?.complete()
            } else {
                println("Deployed on 8080: failed")
                println("SSL Deployed on 8443: failed")
                // At least one server failed
                startFuture?.fail(ar.cause())
            }
        })
    }

    private fun getSSLOptions(keyValue: String?, certValue: String?, sslPort: Int=8443): HttpServerOptions {
        //ssl certificate options
        val pemKeyCertOptions = PemKeyCertOptions()
        pemKeyCertOptions.keyValue = Buffer.buffer(keyValue)
        pemKeyCertOptions.certValue = Buffer.buffer(certValue)

        val httpServerOptions = HttpServerOptions()
        httpServerOptions.isSsl = true
        httpServerOptions.pemKeyCertOptions = pemKeyCertOptions
        httpServerOptions.port = sslPort
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

        //index all pages for quick access
        for (pageNumber in 1..totalPages) {
            var endIdx = pageNumber * ITEMS_PER_PAGE
            val startIdx = endIdx - ITEMS_PER_PAGE

            if (pageNumber == totalPages && itemsOnLastPage != 0) {
                endIdx = startIdx + itemsOnLastPage
            }
            indexedEntries.put(pageNumber, blogItems.subList(startIdx, endIdx))
        }

    }

    private fun createRouter(redirectSSLPort: Int=443, deploySSL: Boolean=false) = Router.router(vertx).apply {
        if (deploySSL) route().redirectToHttpsHandler(redirectSSLPort)
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath
        get("/rest/blog/highestPage").handler(handlerHighestPage)
        get("/rest/blog/listCategories").handler(handlerListCategories)
        get("/rest/blog/blogCount").handler(handlerBlogCount)
        get("/rest/blog/blogItems/:pageNumber").handler(handlerMainBlogByPageNumber)
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
            val pagedBlogItemsList = indexedEntries.get(pageNumber)
            if (pagedBlogItemsList != null) {
                req.response().endWithJson(pagedBlogItemsList)
            } else {
                req.response().endWithError() //this one is not meant to happen ...
            }
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

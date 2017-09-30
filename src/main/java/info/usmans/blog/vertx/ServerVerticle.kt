package info.usmans.blog.vertx

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import info.usmans.blog.model.BlogItem
import info.usmans.blog.model.Category
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
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
        //load data.json
        val dataJson = ClassLoader.getSystemResource("data.json").readText()
        initData(dataJson)

        //construct router and http server
        val router = createRouter()

        vertx.createHttpServer()
                .requestHandler { router.accept(it) }
                .listen(config().getInteger("http.port", 8080)) { result ->
                    if (result.succeeded()) {
                        println("Up up and away ...")
                        startFuture?.complete()
                    } else {
                        startFuture?.fail(result.cause())
                    }
                }
    }

    private fun initData(dataJson: String) {
        blogItems = ObjectMapper().registerModule(KotlinModule()).readValue(dataJson)
        blogItemSize = blogItems.size
        totalPages = blogItemSize / ITEMS_PER_PAGE
        itemsOnLastPage = blogItemSize % ITEMS_PER_PAGE
        if (itemsOnLastPage != 0) {
            totalPages++
        }
    }

    private fun createRouter() = Router.router(vertx).apply {
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

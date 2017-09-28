package info.usmans.blog.vertx;

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
import io.vertx.ext.web.handler.StaticHandler

class ServerVerticle : AbstractVerticle() {
    private val DEFAULT_CATEGORIES by lazy {
        listOf(Category(1, "Java"),
                Category(2, "PostgreSQL"),
                Category(3, "Linux"),
                Category(4, "IT"),
                Category(5, "General"),
                Category(6, "JBoss")
        )
    }

    private val MOCK_BLOG_ENTRIES = listOf(

            BlogItem(1, "Test", "Main Body", "Main", "2017-05-31", "2017-05-31", "31", "May", "2017",
            listOf(Category(1, "Java"),
                    Category(2, "PostgreSQL"))),
            BlogItem(2, "Tes2", "Main Bod2", "Main", "2017-05-31", "2017-05-31", "31", "May", "2017",
                    listOf(Category(1, "Java"),
                            Category(2, "PostgreSQL")))
    )

    override fun start(startFuture: Future<Void>?) {
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

    private fun createRouter() = Router.router(vertx).apply {
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        get("/rest/blog/highestPage").handler(handlerHighestPage)
        get("/rest/blog/listCategories").handler(handlerListCategories)
        get("/rest/blog/blogCount").handler(handlerBlogCount)
        get("/rest/blog/blogItems/:pageNumber").handler(handlerMainBlogByPageNumber)
        route("/*").handler(StaticHandler.create()) //serve static contents from webroot folder on classpath
    }

    val handlerListCategories = Handler<RoutingContext> { req ->
        req.response().endWithJson(DEFAULT_CATEGORIES);
    }

    //TODO: Use actual size
    val handlerBlogCount = Handler<RoutingContext> { req -> req.response().putHeader("content-type", "text/plain").end("1") }

    //TODO: Calculate actual page size
    val handlerHighestPage = Handler<RoutingContext> { req -> req.response().putHeader("content-type", "text/plain").end("1") }

    //TODO: Use data from data.json
    val handlerMainBlogByPageNumber = Handler<RoutingContext> { req ->
        req.response().endWithJson(MOCK_BLOG_ENTRIES)
    }

    /**
     * Extension to the HTTP response to output JSON objects.
     */
    fun HttpServerResponse.endWithJson(obj: Any) {
        this.putHeader("Content-Type", "application/json; charset=utf-8").end(Json.encodePrettily(obj))
    }

    fun HttpServerResponse.endEithError() {
        this.setStatusCode(400).end("Bad Request - Invalid Page Number")
    }
}

package info.usmans.blog.vertx

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import info.usmans.blog.model.BlogItem
import info.usmans.blog.model.Category
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.handler.StaticHandler
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

const val ITEMS_PER_PAGE = 10

class ServerVerticle : AbstractVerticle() {
    init {
        Json.mapper.apply {
            registerKotlinModule()
        }

        Json.prettyMapper.apply {
            registerKotlinModule()
        }
    }

    private val defaultCategories = Json.encode(listOf(
            Category(1, "Java"),
            Category(2, "PostgreSQL"),
            Category(3, "Linux"),
            Category(4, "IT"),
            Category(5, "General"),
            Category(6, "JBoss")
    ))

    private val indexedEntries = HashMap<Int, String>()

    private var highestPage: String = ""
    private var blogCount: String = ""

    override fun start(startFuture: Future<Void>?) {
        val keyValue: String? = getKeyValue()
        val certValue: String? = getCertValue()
        val deploySSL = keyValue != null && certValue != null
        val redirectSSLPort = System.getProperty("redirectSSLPort", "443").toIntOrNull() ?: 443

        //load our blog from data.json
        initData()

        //construct router and http server
        val router = createRouter()

        if (deploySSL) {
            println("Deploying Http Server (SSL) on port 8443")
            //deploy this verticle with SSL
            vertx.createHttpServer(getSSLOptions(keyValue, certValue, 8443)).requestHandler { router.accept(it) }.listen()

            //deploy nonsecure forwarding verticle with 302 logic ...
            vertx.deployVerticle(ForwardingServerVerticle(redirectSSLPort))
        } else {
            println("Deploying Http Server on port 8080")
            //deploy non secure server
            vertx.createHttpServer()
                    .requestHandler { router.accept(it) }
                    .listen(8080)
        }

        startFuture?.complete()
    }

    private fun getCertValue(): String? {
        val certValueEncoded: String? = System.getenv("BLOG_CERT_BASE64")
        val certValue: String? = if (certValueEncoded != null) try {
            Base64.getDecoder().decode(certValueEncoded).toString(UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
        else
            null
        return certValue
    }

    private fun getKeyValue(): String? {
        val keyValueEncoded: String? = System.getenv("BLOG_KEY_BASE64")
        val keyValue: String? = if (keyValueEncoded != null) try {
            Base64.getDecoder().decode(keyValueEncoded).toString(UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
        else
            null
        return keyValue
    }

    private fun getSSLOptions(keyValue: String?, certValue: String?, sslPort: Int = 8443): HttpServerOptions {
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
        println(dataJson)
        val mapper = jacksonObjectMapper()
        val blogItems: List<BlogItem> = mapper.readValue(dataJson)

        val blogItemCount = blogItems.size
        val itemsOnLastPage = blogItemCount % ITEMS_PER_PAGE
        var totalPagesCount = blogItemCount / ITEMS_PER_PAGE
        if (itemsOnLastPage != 0) {
            totalPagesCount++
        }

        highestPage = totalPagesCount.toString()
        blogCount = blogItemCount.toString()

        //index all pages for quick access
        for (pageNumber in 1..totalPagesCount) {
            var endIdx = pageNumber * ITEMS_PER_PAGE
            val startIdx = endIdx - ITEMS_PER_PAGE

            if (pageNumber == totalPagesCount && itemsOnLastPage != 0) {
                endIdx = startIdx + itemsOnLastPage
            }
            indexedEntries.put(pageNumber, Json.encode(blogItems.subList(startIdx, endIdx)))
        }
    }

    private fun createRouter() = Router.router(vertx).apply {
        route().handler(BodyHandler.create()) //BodyHandler aggregate entire incoming request in memory
        //if (deploySSL) route().redirectToHttpsHandler(redirectSSLPort)
        route().handler(FaviconHandler.create()) //serve favicon.ico from classpath
        get("/rest/blog/highestPage").handler(handlerHighestPage)
        get("/rest/blog/listCategories").handler(handlerListCategories)
        get("/rest/blog/blogCount").handler(handlerBlogCount)
        get("/rest/blog/blogItems/:pageNumber").handler(handlerMainBlogByPageNumber)
        route("/*").handler(StaticHandler.create()) //serve static contents from webroot folder on classpath
    }

    private val handlerListCategories = Handler<RoutingContext> { req ->
        req.response().sendJson(defaultCategories)
    }

    private val handlerBlogCount = Handler<RoutingContext> { req -> req.response().sendPlain(blogCount) }

    private val handlerHighestPage = Handler<RoutingContext> { req -> req.response().sendPlain(highestPage) }

    private val handlerMainBlogByPageNumber = Handler<RoutingContext> { req ->
        val pageNumber = req.request().getParam("pageNumber").toIntOrNull() ?: 0

        if (pageNumber >= 1) {
            val pagedBlogItemsList = indexedEntries[pageNumber]
            if (pagedBlogItemsList != null) {
                req.response().sendJson(pagedBlogItemsList)
            } else {
                req.response().endWithError()
            }
        } else {
            req.response().endWithError()
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

    private fun HttpServerResponse.endWithError() {
        this.setStatusCode(400).end("Bad Request - Invalid Page Number")
    }
}

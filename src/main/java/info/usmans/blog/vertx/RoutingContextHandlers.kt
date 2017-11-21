package info.usmans.blog.vertx

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import info.usmans.blog.model.BlogItem
import info.usmans.blog.model.BlogItemMaps
import info.usmans.blog.model.Message
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.templ.TemplateEngine

fun blogItemListFromJson(dataJson: Buffer): List<BlogItem>? {
    try {
        val blogItemsOrig: List<BlogItem> = Json.mapper.readValue(dataJson.toString())
        return blogItemsOrig
    } catch (e: JsonParseException) {
        println(e.message)
        return null
    }
}

fun readJsonData(vertx: Vertx, gistTag: String): HttpRequest<Buffer> {
    val jsonUrl = getDataJsonUrl(gistTag)
    val options = WebClientOptions().apply { isKeepAlive = false }
    val client = WebClient.create(vertx, options)
    println("Reading data.json from $jsonUrl")
    return client.getAbs(jsonUrl)
}

fun highestPageHandler(blogItem: BlogItemMaps) = Handler<RoutingContext> { rc -> rc.response().sendPlain(blogItem.getHighestPage().toString()) }

fun blogCountHandler(blogItem: BlogItemMaps) = Handler<RoutingContext> { rc -> rc.response().sendPlain(blogItem.getBlogCount().toString()) }

fun pageNumberHandler(blogItem: BlogItemMaps) = Handler<RoutingContext> { rc ->
    val pageNumber = rc.request().getParam("pageNumber").toIntOrNull() ?: 0
    if (pageNumber >= 1) {
        if (blogItem.getPagedblogItemMap().containsKey(pageNumber)) {
            val pagedBlogItemsList = blogItem.getPagedblogItemMap().get(pageNumber)
            rc.response().sendJson(Json.encode(pagedBlogItemsList))
        } else {
            rc.response().endWithErrorJson("Bad Request - Invalid Page Number $pageNumber")
        }
    } else {
        rc.response().endWithErrorJson("Bad Request - Invalid Page Number $pageNumber")
    }
}

fun blogItemsHandler(blogItem: BlogItemMaps) = Handler<RoutingContext> { rc ->
    rc.response().sendJson(Json.encodePrettily(blogItem.getblogItemMap().values.toList().sortedBy { it.id }))
}

fun blogItemByIdHandler(blogItem: BlogItemMaps) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("id").toLongOrNull() ?: 0
    if (blogItem.getblogItemMap().contains(blogItemId)) {
        rc.response().sendJson(Json.encodePrettily(blogItem.getblogItemMap().get(blogItemId)))
    } else {
        rc.response().endWithErrorJson("Bad Request - Invalid Id: $blogItemId")
    }
}

fun siteMapHandler(blogItem: BlogItemMaps) = Handler<RoutingContext> { rc ->
    rc.response().putHeader("Content-Type", "text; charset=utf-8").end(blogItem.getblogItemMap().keys.joinToString("\n") {
        "${rc.request().getOAuthRedirectURI("/blog/")}$it"
    })
}

fun blogByTemplateHandler(blogItem: BlogItemMaps, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("id").toLongOrNull() ?: 0
    if (blogItem.getblogItemMap().containsKey(blogItemId)) {
        //pass blogItem to the template
        rc.put("blogItem", blogItem.getblogItemMap().get(blogItemId))
        templateEngine.render(rc, "templates", io.vertx.ext.web.impl.Utils.normalizePath("blog.hbs"), { res ->
            if (res.succeeded()) {
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
            } else {
                rc.fail(res.cause())
            }
        })

    } else {
        rc.response().endWithErrorJson("Invalid Blog Request for id: $blogItemId")
    }
}


fun refreshBlogJsonHandler(vertx: Vertx, blogItemMaps: BlogItemMaps) = Handler<RoutingContext> { rc ->
    val param = rc.request().getParam("tag") ?: DATA_JSON_GIST_TAG
    readJsonData(vertx, param).send({ ar ->
        if (ar.succeeded()) {
            val loadedBlogItemList = blogItemListFromJson(ar.result().body())
            if (loadedBlogItemList == null) {
                rc.response().endWithErrorJson("Error parsing json from tag: $param")
            } else {
                blogItemMaps.initBlogItemMaps(loadedBlogItemList)
                rc.response().sendJson(Json.encodePrettily(Message("data.json updated from tag: $param")))
            }
        } else {
            rc.response().endWithErrorJson("Error loading json from tag: $param " + ar.cause().message)
        }
    })
}

fun protectedPageByTemplateHandler(blogItem: BlogItemMaps, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    if (blogItem.getblogItemMap().isNotEmpty()) {
        //pass blogItem to the template
        rc.put("blogItems", blogItem.getblogItemMap().values)
    }

    val accessToken: AccessToken? = rc.user() as AccessToken
    rc.put("accessToken", accessToken?.principal()?.encodePrettily())

    templateEngine.render(rc, "templates", io.vertx.ext.web.impl.Utils.normalizePath("protected/protected.hbs"), { res ->
        if (res.succeeded()) {
            rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
        } else {
            rc.fail(res.cause())
        }
    })


}

fun blogEditByTemplateHandler(blogItem: BlogItemMaps, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("id").toLongOrNull() ?: 0
    if (blogItem.getblogItemMap().containsKey(blogItemId)) {
        //pass blogItem to the template
        rc.put("blogItem", blogItem.getblogItemMap().get(blogItemId))
        templateEngine.render(rc, "templates", io.vertx.ext.web.impl.Utils.normalizePath("protected/blogedit.hbs"), { res ->
            if (res.succeeded()) {
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
            } else {
                rc.fail(res.cause())
            }
        })

    } else {
        rc.response().endWithErrorJson("Invalid Blog Request for id $blogItemId")
    }
}


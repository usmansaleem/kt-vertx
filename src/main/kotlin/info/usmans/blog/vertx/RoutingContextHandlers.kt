package info.usmans.blog.vertx

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import info.usmans.blog.model.BlogItem
import info.usmans.blog.model.BlogItemMaps
import info.usmans.blog.model.Category
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
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Deprecated("Use String version")
fun blogItemListFromJson(dataJson: Buffer): List<BlogItem>? = blogItemListFromJson(dataJson.toString())

fun blogItemListFromJson(dataJson: String): List<BlogItem>? {
    try {
        return Json.mapper.readValue(dataJson)
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

fun blogEditGetHandler(blogItem: BlogItemMaps, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
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

fun blogEditPostHandler(blogItem: BlogItemMaps, checkoutDir: File) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("blogId").toLongOrNull() ?: 0
    val existingBlogItem = blogItem.getblogItemMap().get(blogItemId)
    if (existingBlogItem != null) {
        val modifiedBlogItem = getModifiedBlogItem(rc, existingBlogItem)

        blogItem.getblogItemMap().put(blogItemId, modifiedBlogItem)
        blogItem.reInitPagedBlogItems()

        //update data.json in local repo
        File(checkoutDir, "data.json").writeText(Json.encodePrettily(blogItem.getblogItemMap().values.toList().sortedBy { it.id }))
        commitGist(checkoutDir, "Updating blog $blogItemId from jgit")
        pushGist(checkoutDir)

        rc.response().sendJson(Json.encode(Message("Blog Successfully updated")))
    } else {
        rc.response().endWithErrorJson("Invalid Blog Request for id $blogItemId")
    }
}

fun blogNewGetHandler(blogItem: BlogItemMaps, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    rc.put("blogItem", BlogItem( blogItem.getblogItemMap().firstKey() + 1,"url_friendly","Title...","Description...","Body...","Main"))
    templateEngine.render(rc, "templates", io.vertx.ext.web.impl.Utils.normalizePath("protected/blogedit.hbs"), { res ->
        if (res.succeeded()) {
            rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
        } else {
            rc.fail(res.cause())
        }
    })
}

fun blogNewPostHandler(blogItem: BlogItemMaps, checkoutDir: File) = Handler<RoutingContext> { rc ->
    val blogItemId = blogItem.getblogItemMap().firstKey() + 1
    val modifiedBlogItem = getNewBlogItem(rc, blogItemId)

    blogItem.getblogItemMap().put(blogItemId, modifiedBlogItem)
    blogItem.reInitPagedBlogItems()

    //update data.json in local repo
    File(checkoutDir, "data.json").writeText(Json.encodePrettily(blogItem.getblogItemMap().values.toList().sortedBy { it.id }))
    commitGist(checkoutDir, "Updating blog $blogItemId from jgit")
    pushGist(checkoutDir)

    rc.response().sendJson(Json.encode(Message("Blog id $blogItemId Successfully created")))
}

private fun getModifiedBlogItem(rc: RoutingContext, existingBlogItem: BlogItem): BlogItem {
    //obtain submitted values ...
    val urlFriendlyId = rc.request().getFormAttribute("urlFriendlyId") ?: existingBlogItem.urlFriendlyId
    val title = rc.request().getFormAttribute("title") ?: existingBlogItem.title
    val description = rc.request().getFormAttribute("description") ?: existingBlogItem.description
    val body = rc.request().getFormAttribute("body") ?: existingBlogItem.body
    val categories = rc.request().getFormAttribute("categories")
    val categoryList = if (categories.isNullOrBlank())
        emptyList<Category>()
    else
        categories.split(",").mapIndexed { i, s ->
            Category(i, s.trim())
        }


    val modifiedOn = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val modifiedBlogItem = existingBlogItem.copy(urlFriendlyId = urlFriendlyId, title = title, description = description, body = body, modifiedOn = modifiedOn, categories = categoryList)
    return modifiedBlogItem
}

private fun getNewBlogItem(rc: RoutingContext, id: Long): BlogItem {
    //obtain submitted values ...
    val urlFriendlyId = rc.request().getFormAttribute("urlFriendlyId")
    val title = rc.request().getFormAttribute("title")
    val description = rc.request().getFormAttribute("description")
    val body = rc.request().getFormAttribute("body")
    val categories = rc.request().getFormAttribute("categories")
    val categoryList = if (categories.isNullOrBlank())
        emptyList<Category>()
    else
        categories.split(",").mapIndexed { i, s ->
            Category(i, s.trim())
        }

    return BlogItem(id=id,urlFriendlyId =  urlFriendlyId, title = title, description = description, body = body, categories = categoryList)
}


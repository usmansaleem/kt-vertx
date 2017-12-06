package info.usmans.blog.vertx

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.readValue
import info.usmans.blog.model.BlogItem
import info.usmans.blog.model.BlogItemUtil
import info.usmans.blog.model.Category
import info.usmans.blog.model.Message
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.impl.Utils
import io.vertx.ext.web.templ.TemplateEngine
import java.io.File

fun blogItemListFromJson(dataJson: String): List<BlogItem>? {
    return try {
        Json.mapper.readValue(dataJson)
    } catch (e: JsonParseException) {
        println(e.message)
        null
    }
}

fun highestPageHandler(blogItemUtil: BlogItemUtil) = Handler<RoutingContext> { rc -> rc.response().sendPlain(blogItemUtil.getHighestPage().toString()) }

fun blogCountHandler(blogItemMapUtil: BlogItemUtil) = Handler<RoutingContext> { rc -> rc.response().sendPlain(blogItemMapUtil.getBlogCount().toString()) }

fun pageNumberHandler(blogItemUtil: BlogItemUtil) = Handler<RoutingContext> { rc ->
    val pageNumber = rc.request().getParam("pageNumber").toLongOrNull() ?: 0
    if (pageNumber >= 1) {
        val pagedBlogItemsList = blogItemUtil.getBlogItemListForPage(pageNumber)

        if (pagedBlogItemsList.isNotEmpty()) {
            rc.response().sendJson(Json.encode(pagedBlogItemsList))
        } else {
            rc.response().endWithErrorJson("Bad Request - Invalid Page Number $pageNumber")
        }
    } else {
        rc.response().endWithErrorJson("Bad Request - Invalid Page Number $pageNumber")
    }
}

fun blogItemsHandler(blogItemUtil: BlogItemUtil) = Handler<RoutingContext> { rc ->
    rc.response().sendJson(Json.encodePrettily(blogItemUtil.getBlogItemList()))
}

fun blogItemByIdHandler(blogItemUtil: BlogItemUtil) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("id").toLongOrNull() ?: 0
    val blogItem = blogItemUtil.getBlogItemForId(blogItemId)
    if (blogItem == null) {
        rc.response().endWithErrorJson("Bad Request - Invalid Id: $blogItemId")
    } else {
        rc.response().sendJson(Json.encode(blogItem))
    }
}

fun siteMapHandler(blogItemUtil: BlogItemUtil) = Handler<RoutingContext> { rc ->
    rc.response().putHeader("Content-Type", "text; charset=utf-8").end(blogItemUtil.getBlogItemUrlList().joinToString("\n") {
        "${rc.request().getOAuthRedirectURI("/usmansaleem/blog/")}$it"
    })
}

fun blogByFriendlyUrl(blogItemUtil: BlogItemUtil, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    val friendlyUrl = rc.request().getParam("url")
    val blogItem = blogItemUtil.getBlogItemForUrl(friendlyUrl)
    if (blogItem != null) {
        //pass blogItem to the template
        rc.put("blogItem", blogItem)
        templateEngine.render(rc, "templates", Utils.normalizePath("blog.hbs"), { res ->
            if (res.succeeded()) {
                rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
            } else {
                rc.fail(res.cause())
            }
        })

    } else {
        rc.response().endWithErrorJson("Invalid Blog Request")
    }
}

fun blogByIdHandler(blogItemUtil: BlogItemUtil, publicHttpsPort:Int = 443 ) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("id").toLongOrNull() ?: 0
    val blogItem = blogItemUtil.getBlogItemForId(blogItemId)
    if(blogItem == null) {
        rc.response().endWithErrorJson("Invalid Request for Blog")
    } else {
        rc.request().redirectToFriendlyUrl(redirectSSLPort = publicHttpsPort, url=blogItem.urlFriendlyId)
    }
}

fun protectedPageByTemplateHandler(blogItemList: BlogItemUtil, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    rc.put("blogItems", blogItemList.getBlogItemList())

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

fun blogEditGetHandler(blogItemUtil: BlogItemUtil, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("id").toLongOrNull() ?: 0
    val blogItem = blogItemUtil.getBlogItemForId(blogItemId)
    if (blogItem != null) {
        //pass blogItem to the template
        rc.put("blogItem", blogItem)
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

fun blogEditPostHandler(blogItemUtil: BlogItemUtil, checkoutDir: File) = Handler<RoutingContext> { rc ->
    val blogItemId = rc.request().getParam("blogId").toLongOrNull() ?: 0
    val existingBlogItem = blogItemUtil.getBlogItemForId(blogItemId)
    if (existingBlogItem != null) {
        val modifiedBlogItem = getNewBlogItemFromSubmittedForm(rc, blogItemId).copy(createdOn = existingBlogItem.createdOn,
                createDay = existingBlogItem.createDay, createMonth = existingBlogItem.createMonth,
                createYear = existingBlogItem.createYear)

        blogItemUtil.putBlogItemForId(blogItemId, modifiedBlogItem)
        blogItemUtil.initPagedBlogItems()

        //update data.json in local repo
        File(checkoutDir, "data.json").writeText(Json.encodePrettily(blogItemUtil.getBlogItemList()))
        commitGist(checkoutDir, "Updating blog $blogItemId from jgit")
        pushGist(checkoutDir)

        rc.response().sendJson(Json.encode(Message("Blog Successfully updated")))
    } else {
        rc.response().endWithErrorJson("Invalid Blog Request for id $blogItemId")
    }
}

fun blogNewGetHandler(blogItemUtil: BlogItemUtil, templateEngine: TemplateEngine) = Handler<RoutingContext> { rc ->
    rc.put("blogItem", BlogItem(blogItemUtil.getNextBlogItemId(), "url_friendly", "Title...", "Description...", "Body...", "Main"))
    templateEngine.render(rc, "templates", io.vertx.ext.web.impl.Utils.normalizePath("protected/blogedit.hbs"), { res ->
        if (res.succeeded()) {
            rc.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
        } else {
            rc.fail(res.cause())
        }
    })
}

fun blogNewPostHandler(blogItemUtil: BlogItemUtil, checkoutDir: File) = Handler<RoutingContext> { rc ->
    val blogItemId = blogItemUtil.getNextBlogItemId()
    val modifiedBlogItem = getNewBlogItemFromSubmittedForm(rc, blogItemId)

    blogItemUtil.putBlogItemForId(blogItemId, modifiedBlogItem)
    blogItemUtil.initPagedBlogItems()

    //update data.json in local repo
    File(checkoutDir, "data.json").writeText(Json.encodePrettily(blogItemUtil.getBlogItemList()))
    commitGist(checkoutDir, "Updating blog $blogItemId from jgit")
    pushGist(checkoutDir)

    rc.response().sendJson(Json.encode(Message("Blog id $blogItemId Successfully created")))
}

private fun getNewBlogItemFromSubmittedForm(rc: RoutingContext, id: Long): BlogItem {
    //obtain submitted values ...
    val urlFriendlyId = rc.request().getFormAttribute("urlFriendlyId")
    val title = rc.request().getFormAttribute("title")
    val description = rc.request().getFormAttribute("description")
    val body = rc.request().getFormAttribute("body")
    val categories = rc.request().getFormAttribute("categories")
    val categoryList = if (categories.isNullOrBlank())
        emptyList()
    else
        categories.split(",").mapIndexed { i, s ->
            Category(i, s.trim())
        }

    return BlogItem(id = id, urlFriendlyId = urlFriendlyId, title = title, description = description, body = body, categories = categoryList)
}


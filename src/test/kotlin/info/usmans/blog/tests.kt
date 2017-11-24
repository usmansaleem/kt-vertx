package info.usmans.blog

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import info.usmans.blog.model.BlogItem
import info.usmans.blog.vertx.checkoutGist
import info.usmans.blog.vertx.commitGist
import info.usmans.blog.vertx.gitPushStatus
import io.vertx.core.json.Json
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class TestSource {
    @Before
    fun setUp() {
        Json.mapper.apply {
            registerKotlinModule()
        }

        Json.prettyMapper.apply {
            registerKotlinModule()
        }
    }

    @Test
    fun testGistCheckout() {
        val checkoutDir = checkoutGist("https://gist.github.com/5fd0a9ee89d72544cf50128ba8e8e012.git")

        //read and change the file and commit ...
        val blogItemList: List<info.usmans.blog.model.BlogItem> = Json.mapper.readValue(File(checkoutDir, "data.json").readText())
        val sortedBlogItemsList =  blogItemList.sortedBy { it.id }

        val blogItem = BlogItem(sortedBlogItemsList.last().id + 1,
                "commit_from_jgit",
                "JGit","Commit from jgit to gist",
                "This will contain fullbody")

        //add to list
        val updatedList = sortedBlogItemsList.toMutableList()
        updatedList.add(blogItem)
        assertEquals(blogItemList.size + 1, updatedList.size)

        //convert to json and write it to file in repository
        val jsonData = Json.encodePrettily(updatedList)
        println(jsonData)

        //write to file in repository
        File(checkoutDir, "data.json").writeText(jsonData)

        val revCommit = commitGist(checkoutDir)
        assertEquals("commit from jgit", revCommit.fullMessage)

        gitPushStatus(checkoutDir)
        //pushGist(checkoutDir, gitCredentialProvider(""))
    }
}
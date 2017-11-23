package info.usmans.blog.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

internal const val BLOG_ITEMS_PER_PAGE = 10

/**
 * BlogItem represents each blog entry.
 */
data class BlogItem(
        val id: Long = 0,
        val urlFriendlyId: String? = "",
        val title: String,
        val description: String? = "",
        val body: String? = "",
        var blogSection: String? = "Main",
        val createdOn: String? = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        val modifiedOn: String? =LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
        val createDay: String? = LocalDate.now().format(DateTimeFormatter.ofPattern("dd")),
        val createMonth: String? = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM")),
        val createYear: String? = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy")),
        val categories: List<Category>? = emptyList()
)


/**
 * Represents a category*
 */
data class Category(val id: Int = 0, val name: String = "")

data class Message(val message: String = "")




class BlogItemMaps {

    //store blog entries per page
    private val pagedBlogItems: TreeMap<Int, List<BlogItem>> = TreeMap()
    //store blog entries based on id
    private val blogItemMap: TreeMap<Long, BlogItem> = TreeMap(Collections.reverseOrder())

    fun initBlogItemMaps(blogItems: List<BlogItem>) {
        this.blogItemMap.clear()
        this.blogItemMap.putAll(blogItems.associateBy({ it.id }) { it })
        reInitPagedBlogItems()
    }

    fun getblogItemMap() = blogItemMap

    fun getPagedblogItemMap() = pagedBlogItems

    fun getHighestPage() = pagedBlogItems.lastKey()

    fun getBlogCount() = blogItemMap.size

    fun reInitPagedBlogItems(){
        val sortedBlogItemsList = blogItemMap.values.toList().sortedByDescending(BlogItem::id)
        val blogItemCount = sortedBlogItemsList.size
        val itemsOnLastPage = blogItemCount % BLOG_ITEMS_PER_PAGE
        val totalPagesCount = if (itemsOnLastPage == 0) blogItemCount / BLOG_ITEMS_PER_PAGE else blogItemCount / BLOG_ITEMS_PER_PAGE + 1

        val pagedBlogItems = mutableMapOf<Int, List<BlogItem>>()


        for (pageNumber in totalPagesCount downTo 1) {
            var endIdx = (pageNumber * BLOG_ITEMS_PER_PAGE)
            val startIdx = endIdx - BLOG_ITEMS_PER_PAGE

            if ((pageNumber == totalPagesCount) && (itemsOnLastPage != 0)) {
                endIdx = startIdx + itemsOnLastPage
            }
            val pagedList = sortedBlogItemsList.subList(startIdx, endIdx) //sort??
            pagedBlogItems.put(pageNumber, pagedList)
        }

        this.pagedBlogItems.clear()
        this.pagedBlogItems.putAll(pagedBlogItems)
    }
}


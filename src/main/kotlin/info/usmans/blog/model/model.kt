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
        val modifiedOn: String? = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
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


class BlogItemUtil {

    //store blog entries per page
    private val pagedBlogItemIdMap: TreeMap<Long, List<Long>> = TreeMap()
    //store blog entries based on id
    private val blogItemMap: TreeMap<Long, BlogItem> = TreeMap(Collections.reverseOrder())

    fun initBlogItemMaps(blogItems: List<BlogItem>) {
        this.blogItemMap.clear()
        this.blogItemMap.putAll(blogItems.associateBy({ it.id }) { it })
        initPagedBlogItems()
    }

    fun getBlogItemIdList() = blogItemMap.keys.toList()
    fun getBlogItemList() = blogItemMap.values.toList()

    fun getBlogItemForId(id: Long) = blogItemMap.get(id)

    fun putBlogItemForId(id: Long, blogItem: BlogItem) = blogItemMap.put(id, blogItem)

    fun getNextBlogItemId() = blogItemMap.firstKey() + 1

    fun getBlogItemListForPage(pageNumber: Long): List<BlogItem> = this.pagedBlogItemIdMap.get(pageNumber)?.map { blogItemMap.get(it) ?: BlogItem(id = 0, title = "Not Found") } ?: emptyList()

    fun getHighestPage() = (if(pagedBlogItemIdMap.size == 0) 0L else pagedBlogItemIdMap.lastKey())!!

    fun getBlogCount() = blogItemMap.size

    fun initPagedBlogItems() {
        val sortedBlogItemsList = blogItemMap.values.toList().sortedByDescending(BlogItem::id)
        val itemsOnLastPage = sortedBlogItemsList.size % BLOG_ITEMS_PER_PAGE
        val totalPagesCount = if (itemsOnLastPage == 0) sortedBlogItemsList.size / BLOG_ITEMS_PER_PAGE else sortedBlogItemsList.size / BLOG_ITEMS_PER_PAGE + 1

        val localBlogItemsIdPerPageList = mutableMapOf<Long, List<Long>>()

        for (pageNumber in totalPagesCount downTo 1) {
            var endIdx = (pageNumber * BLOG_ITEMS_PER_PAGE)
            val startIdx = endIdx - BLOG_ITEMS_PER_PAGE

            if ((pageNumber == totalPagesCount) && (itemsOnLastPage != 0)) {
                endIdx = startIdx + itemsOnLastPage
            }
            val pagedList: List<Long> = sortedBlogItemsList.subList(startIdx, endIdx).map { it.id }
            localBlogItemsIdPerPageList.put(pageNumber.toLong(), pagedList)
        }

        if (localBlogItemsIdPerPageList.size < this.pagedBlogItemIdMap.size) {
            //the pages has been decreased (in case of deletion), clear out global map
            this.pagedBlogItemIdMap.clear()
        }

        this.pagedBlogItemIdMap.putAll(localBlogItemsIdPerPageList)
    }
}


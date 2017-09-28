package info.usmans.blog.model

/**
 * BlogItem represents each blog entry.
 */
data class BlogItem(
        val id: Long = 0,
        val title: String,
        val body: String? = "",
        val blogSection: String? = "Main",
        val createdOn: String? = null,
        val modifiedOn: String? = null,
        val createDay: String? = null,
        val createMonth: String? = null,
        val createYear: String? = null,
        val categories: List<Category>? = null
)

/**
 * Represents a category*
 */
data class Category(val id: Int, val name: String)




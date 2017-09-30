package info.usmans.blog.model

/**
 * BlogItem represents each blog entry.
 */
data class BlogItem(
        val id: Long = 0,
        val title: String,
        val body: String? = "",
        val blogSection: String? = "Main",
        val createdOn: String? = "",
        val modifiedOn: String? ="",
        val createDay: String? = "",
        val createMonth: String? = "",
        val createYear: String? = "",
        val categories: List<Category>? = emptyList()
)

/**
 * Represents a category*
 */
data class Category(val id: Int = 0, val name: String = "")




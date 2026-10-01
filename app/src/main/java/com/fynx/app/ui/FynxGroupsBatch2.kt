package com.fynx.app.ui

/** Group activity foundation: posts, media, reactions, comments, polls, pins and notifications. */
enum class FynxGroupMediaType { IMAGE, VIDEO }

data class FynxGroupMedia(val uri: String, val type: FynxGroupMediaType)

data class FynxGroupPollOption(val id: String, val text: String, val voteCount: Int = 0)

data class FynxGroupPoll(val question: String, val options: List<FynxGroupPollOption>, val multipleChoice: Boolean = false)

data class FynxGroupPost(
    val id: String,
    val groupId: String,
    val authorUsername: String,
    val text: String = "",
    val media: List<FynxGroupMedia> = emptyList(),
    val reactions: Map<String, Int> = emptyMap(),
    val commentIds: List<String> = emptyList(),
    val poll: FynxGroupPoll? = null,
    val pinned: Boolean = false,
    val marketplaceProductId: String? = null
)

data class FynxGroupNotification(val id: String, val groupId: String, val title: String, val message: String, val createdAtMillis: Long)

object FynxGroupsBatch2 {
    fun validatePost(post: FynxGroupPost): List<String> = buildList {
        if (post.id.isBlank()) add("Post ID is required")
        if (post.groupId.isBlank()) add("Group ID is required")
        if (post.authorUsername.isBlank()) add("Post author is required")
        if (post.text.isBlank() && post.media.isEmpty() && post.poll == null && post.marketplaceProductId.isNullOrBlank()) add("Post content is required")
        if (post.media.count { it.type == FynxGroupMediaType.IMAGE } > 12) add("A maximum of 12 images is allowed")
        if (post.media.count { it.type == FynxGroupMediaType.VIDEO } > 1) add("Only one video is allowed")
        post.poll?.let { poll ->
            if (poll.question.isBlank()) add("Poll question is required")
            if (poll.options.size < 2) add("A poll needs at least two options")
            if (poll.options.any { it.text.isBlank() }) add("Poll options cannot be empty")
        }
    }

    fun toggleReaction(post: FynxGroupPost, reaction: String): FynxGroupPost? {
        if (reaction.isBlank()) return null
        val next = post.reactions.toMutableMap()
        next[reaction] = (next[reaction] ?: 0) + 1
        return post.copy(reactions = next)
    }

    fun togglePinned(post: FynxGroupPost): FynxGroupPost = post.copy(pinned = !post.pinned)

    fun createMarketplaceShare(post: FynxGroupPost, productId: String): FynxGroupPost? =
        if (productId.isBlank()) null else post.copy(marketplaceProductId = productId)
}

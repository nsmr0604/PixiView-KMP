package me.matsumo.fanbox.core.repository.download

import me.matsumo.fanbox.core.model.fanbox.PostDetail
import me.matsumo.fanbox.core.model.fanbox.PostDetail.Body
import me.matsumo.fanbox.core.model.fanbox.PostDetail.Body.Article

/** 保存する本文テキストを組み立てる。テキストを持たない本文形式では null を返す。 */
internal fun PostDetail.Body.toPlainText(): String? = when (this) {
    is Article -> blocks.mapNotNull { block ->
        when (block) {
            is Article.Block.Text -> block.text

            is Article.Block.Link -> block.url

            is Article.Block.Embed -> block.url

            else -> null
        }
    }.joinToString("\n\n")

    is Body.Image -> text

    is Body.File -> text

    is Body.Text -> text

    is Body.Video -> text

    is Body.Html -> html

    is Body.Unknown -> null
}.takeIf { !it.isNullOrBlank() }

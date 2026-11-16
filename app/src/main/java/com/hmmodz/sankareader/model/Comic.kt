package com.hmmodz.sankareader.model

data class Comic(
    val id: String,
    val title: String,
    val cover: String = "",
    val slug: String = "",
    val provider: String = "komiku",
    val latestChapter: String = "",
    val url: String = "",
    val synopsis: String = "",
    val genres: List<String> = emptyList(),
    val chapters: List<Chapter> = emptyList()
)
data class Chapter(val id: String, val title: String, val slug: String = "", val url: String = "")
data class ReaderPage(val image: String, val index: Int)

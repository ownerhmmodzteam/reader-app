package com.hmmodz.sankareader.data

import java.net.URLEncoder

/** Routes are based on Sanka's public Comic API directory. Keep provider quirks isolated here. */
data class ProviderAdapter(
    val id: String,
    val label: String,
    val search: (String, Int) -> String,
    val latest: (Int) -> String,
    val detail: (String) -> String,
    val chapter: (String, String) -> String,
    val chapterList: ((String) -> String)? = null,
    val enabled: Boolean = true,
    val note: String = ""
)
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

object ProviderRegistry {
    const val BASE = "https://www.sankavollerei.web.id"
    val all: List<ProviderAdapter> = listOf(
        ProviderAdapter("komiku", "Komiku", { q, _ -> "/comic/search?q=${enc(q)}" }, { "/comic/terbaru?page=$it" }, { "/comic/comic/${enc(it)}" }, { _, ch -> "/comic/chapter/${enc(ch)}" }, note = "Default aggregator routes"),
        ProviderAdapter("bacakomik", "BacaKomik", { q, p -> "/comic/bacakomik/search/${enc(q)}?page=$p" }, { "/comic/bacakomik/latest?page=$it" }, { "/comic/bacakomik/detail/${enc(it)}" }, { _, ch -> "/comic/bacakomik/chapter/${enc(ch)}" }),
        ProviderAdapter("maid", "Maid", { q, p -> "/comic/maid/search/${enc(q)}&page=$p" }, { "/comic/maid/latest?page=$it" }, { "/comic/maid/manga/${enc(it)}" }, { _, ch -> "/comic/maid/chapter/${enc(ch)}" }),
        ProviderAdapter("komikindo", "Komikindo", { q, p -> "/comic/komikindo/search/${enc(q)}/$p" }, { "/comic/komikindo/latest/$it" }, { "/comic/komikindo/detail/${enc(it)}" }, { _, ch -> "/comic/komikindo/chapter/${enc(ch)}" }),
        ProviderAdapter("mangakita", "Mangakita", { q, p -> "/comic/mangakita/search/${enc(q)}/$p" }, { "/comic/mangakita/projects/$it" }, { "/comic/mangakita/detail/${enc(it)}" }, { _, ch -> "/comic/mangakita/chapter/${enc(ch)}" }),
        ProviderAdapter("meganei", "Meganei", { q, _ -> "/comic/meganei/search/${enc(q)}" }, { "/comic/meganei/home/$it" }, { "/comic/meganei/info/${enc(it)}" }, { _, ch -> "/comic/meganei/chapter/${enc(ch)}" }),
        ProviderAdapter("softkomik", "Softkomik", { q, _ -> "/comic/softkomik/search?q=${enc(q)}" }, { "/comic/softkomik/update?page=$it" }, { "/comic/softkomik/detail/${enc(it)}" }, { comic, ch -> "/comic/softkomik/chapter/${enc(comic)}/${enc(ch)}" }),
        ProviderAdapter("westmanga", "Westmanga", { q, p -> "/comic/westmanga/search?q=${enc(q)}&page=$p" }, { "/comic/westmanga/latest?page=$it" }, { "/comic/westmanga/detail/${enc(it)}" }, { _, ch -> "/comic/westmanga/chapter/${enc(ch)}" }),
        ProviderAdapter("kmkindo", "Kmkindo", { q, p -> "/comic/komikindo/search/${enc(q)}/$p" }, { "/comic/komikindo/latest/$it" }, { "/comic/komikindo/detail/${enc(it)}" }, { _, ch -> "/comic/komikindo/chapter/${enc(ch)}" }, note = "Kmkindo directory maps to /comic/komikindo routes"),
        ProviderAdapter("kiryuu", "Kiryuu", { q, p -> "/comic/kiryuu/search/${enc(q)}/$p" }, { "/comic/kiryuu/latest?page=$it" }, { "/comic/kiryuu/manga/${enc(it)}" }, { _, ch -> "/comic/kiryuu/chapter/${enc(ch)}" }),
        ProviderAdapter("shinigami", "Shinigami", { q, _ -> "/comic/shinigami/search/${enc(q)}" }, { "/comic/shinigami/latest?page=$it&page_size=20" }, { "/comic/shinigami/detail/${enc(it)}" }, { _, ch -> "/comic/shinigami/read/${enc(ch)}" }, chapterList = { "/comic/shinigami/chapters/${enc(it)}?page=1" }),
        ProviderAdapter("komikstation", "Komikstation", { q, p -> "/comic/komikstation/search/${enc(q)}/$p" }, { "/comic/komikstation/popular?page=$it" }, { "/comic/komikstation/manga/${enc(it)}" }, { _, ch -> "/comic/komikstation/chapter/${enc(ch)}" }, enabled = false, note = "Directory currently flags this provider as error; disabled by default")
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

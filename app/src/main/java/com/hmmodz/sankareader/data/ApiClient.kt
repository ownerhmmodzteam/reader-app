package com.hmmodz.sankareader.data

import com.hmmodz.sankareader.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private object ApiThrottle {
    val gate = Mutex()
    var lastRequestAt = 0L
}

class ApiClient {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val cache = LinkedHashMap<String, Pair<Long, String>>()

    private suspend fun get(path: String, ttlMs: Long = 180_000): JSONObject {
        val key = ProviderRegistry.BASE + path
        synchronized(cache) { cache[key]?.let { if (System.currentTimeMillis() - it.first < ttlMs) return JSONObject(it.second) } }
        ApiThrottle.gate.withLock {
            val wait = 2_000L - (System.currentTimeMillis() - ApiThrottle.lastRequestAt)
            if (wait > 0) delay(wait)
            ApiThrottle.lastRequestAt = System.currentTimeMillis()
        }
        val req = Request.Builder().url(key).header("Accept", "application/json").header("User-Agent", "SankaReader/1.0 (Android)").get().build()
        return withContext(Dispatchers.IO) { http.newCall(req).execute().use { response ->
            if (response.code == 403) throw ApiException("Akses ditolak oleh server (HTTP 403). Permintaan dihentikan; tidak mencoba melewati pembatasan.", 403)
            if (response.code == 429) throw ApiException("Rate limit tercapai (HTTP 429). Permintaan dihentikan; coba lagi setelah jeda.", 429)
            if (!response.isSuccessful) throw ApiException("API mengembalikan HTTP ${response.code}", response.code)
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw ApiException("Respons API kosong")
            val obj = runCatching { JSONObject(body) }.getOrElse { JSONObject().put("data", JSONArray().put(body)) }
            synchronized(cache) { cache[key] = System.currentTimeMillis() to obj.toString(); while (cache.size > 120) cache.remove(cache.keys.first()) }
            obj
        } }
    }

    suspend fun home(): List<Comic> {
        val result = mutableListOf<Comic>()
        listOf("/comic/homepage" to "komiku", "/comic/terbaru?page=1" to "komiku", "/comic/populer?page=1" to "komiku").forEach { (path, provider) ->
            try { result += normalize(get(path), provider) } catch (e: ApiException) { if (e.httpCode == 403 || e.httpCode == 429) throw e }
            catch (_: Exception) { /* keep other documented home sections available */ }
        }
        return result.distinctBy { it.provider + ":" + it.id }.take(80)
    }
    suspend fun latest(providerId: String, page: Int = 1): List<Comic> {
        val p = ProviderRegistry.byId(providerId); return normalize(get(p.latest(page)), p.id)
    }
    suspend fun latestAll(page: Int = 1): List<Comic> {
        val result = mutableListOf<Comic>()
        ProviderRegistry.all.filter { it.enabled }.forEach { p ->
            try { result += normalize(get(p.latest(page)), p.id) } catch (e: ApiException) { if (e.httpCode == 403 || e.httpCode == 429) throw e }
            catch (_: Exception) { /* skip only this provider's ordinary parse/network failure */ }
        }
        return result.distinctBy { it.provider + ":" + it.id }
    }
    suspend fun search(providerId: String, q: String, page: Int = 1): List<Comic> {
        if (providerId == "all") {
            val result = mutableListOf<Comic>()
            ProviderRegistry.all.filter { it.enabled }.forEach { p ->
                try { result += normalize(get(p.search(q, page)), p.id) } catch (e: ApiException) { if (e.httpCode == 403 || e.httpCode == 429) throw e }
                catch (_: Exception) { /* continue only for ordinary provider failures */ }
            }
            return result.distinctBy { it.provider + ":" + it.id }
        }
        val p = ProviderRegistry.byId(providerId); return normalize(get(p.search(q, page)), p.id)
    }
    suspend fun detail(providerId: String, slug: String): Comic {
        val p = ProviderRegistry.byId(providerId)
        val obj = get(p.detail(slug), 300_000)
        val dataObj = obj.optJSONObject("data") ?: obj
        val title = firstString(dataObj, listOf("title", "name", "judul", "manga_title", "comic_title")) ?: slug
        val base = Comic(
            id = firstString(dataObj, listOf("slug", "manga_id", "comic_id", "id")) ?: slug,
            title = title,
            cover = firstString(dataObj, listOf("cover", "cover_portrait", "thumbnail", "image", "poster", "thumb", "cover_url")).orEmpty(),
            slug = slug,
            provider = p.id,
            latestChapter = firstString(dataObj, listOf("latest_chapter", "chapter", "last_chapter")).orEmpty(),
            url = firstString(dataObj, listOf("url", "link")).orEmpty()
        )
        val chapterJson = p.chapterList?.let { get(it(slug), 300_000) } ?: obj
        val chapters = extractObjects(chapterJson).mapNotNull { o ->
            val number = firstString(o, listOf("chapter_title", "chapter", "chapter_number"))
                ?: firstString(o, listOf("name", "title"))?.takeIf { it.contains("chapter", ignoreCase = true) }
                ?: return@mapNotNull null
            val chapterId = firstString(o, listOf("slug", "chapter_slug", "chapter_id", "id", "url")) ?: number
            Chapter(chapterId, if (number.startsWith("Chapter", true)) number else "Chapter $number", chapterId, firstString(o, listOf("url", "link")).orEmpty())
        }.distinctBy { it.id }
        val genres = strings(dataObj.opt("genres")) + extractObjects(JSONObject().put("genres", dataObj.opt("genres"))).mapNotNull { firstString(it, listOf("name")) }
        return base.copy(synopsis = firstString(dataObj, listOf("synopsis", "description", "summary")).orEmpty(), genres = genres.distinct(), chapters = chapters)
    }
    suspend fun read(providerId: String, comicSlug: String, chapterSlug: String): List<String> {
        val p = ProviderRegistry.byId(providerId); val obj = get(p.chapter(comicSlug, chapterSlug), 600_000)
        val urls = mutableListOf<String>()
        fun scan(v: Any?) {
            when (v) {
                is JSONObject -> {
                    val keys = v.keys(); while (keys.hasNext()) { val k = keys.next(); val value = v.opt(k); if (k.lowercase() in listOf("image", "images", "src", "url", "image_url", "content") && value is String && value.startsWith("https://")) urls += value else scan(value) }
                }
                is JSONArray -> for (i in 0 until v.length()) scan(v.opt(i))
                is String -> if (v.startsWith("https://") && (v.contains(".jpg") || v.contains(".jpeg") || v.contains(".png") || v.contains(".webp") || v.contains("image"))) urls += v
            }
        }
        scan(obj); return urls.distinct()
    }

    private fun normalize(root: JSONObject, provider: String): List<Comic> = extractObjects(root).mapNotNull { o ->
        val title = firstString(o, listOf("title", "name", "judul", "manga_title", "comic_title")) ?: return@mapNotNull null
        val slug = firstString(o, listOf("slug", "manga_id", "comic_id", "id", "url")) ?: title
        val safetyText = (title + " " + firstString(o, listOf("type", "rating", "content_rating", "category")).orEmpty() + " " + o.opt("genres").toString()).lowercase()
        if (listOf("18+", "adult", "hentai", "nsfw", "pornographic", "erotica", "smut", "mature").any { safetyText.contains(it) }) return@mapNotNull null
        Comic(id = slug, title = title, cover = firstString(o, listOf("cover", "thumbnail", "image", "poster", "thumb", "cover_url", "cover_portrait")).orEmpty(), slug = slug, provider = provider,
            latestChapter = firstString(o, listOf("latest_chapter", "chapter", "last_chapter", "chapter_name")).orEmpty(), url = firstString(o, listOf("url", "link")).orEmpty())
    }.distinctBy { it.id }

    private fun extractObjects(root: JSONObject): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        fun walk(v: Any?, depth: Int) {
            if (depth > 5) return
            when (v) {
                is JSONObject -> {
                    if (firstString(v, listOf("title", "name", "judul", "manga_title", "comic_title", "chapter_number")) != null) found += v
                    val keys = v.keys(); while (keys.hasNext()) walk(v.opt(keys.next()), depth + 1)
                }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), depth + 1)
            }
        }
        walk(root, 0); return found.distinctBy { firstString(it, listOf("slug", "chapter_id", "manga_id", "id", "chapter_number", "title", "name")).orEmpty() }
    }
    private fun firstString(o: JSONObject, keys: List<String>): String? = keys.firstNotNullOfOrNull { k -> o.opt(k)?.takeIf { it !is JSONObject && it !is JSONArray && it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() && it != "null" } }
    private fun strings(v: Any?): List<String> = when (v) { is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it).takeIf(String::isNotBlank) }; is String -> v.split(",").map(String::trim); else -> emptyList() }
}
class ApiException(message: String, val httpCode: Int = 0) : Exception(message)

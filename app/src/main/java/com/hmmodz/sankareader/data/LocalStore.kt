package com.hmmodz.sankareader.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hmmodz.sankareader.model.Comic

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("sanka_reader", Context.MODE_PRIVATE)
    private val gson = Gson()
    private fun read(key: String): MutableList<Comic> = runCatching {
        gson.fromJson<List<Comic>>(prefs.getString(key, "[]"), object : TypeToken<List<Comic>>() {}.type).toMutableList()
    }.getOrDefault(mutableListOf())
    private fun write(key: String, data: List<Comic>) { prefs.edit().putString(key, gson.toJson(data)).apply() }
    fun favorites() = read("favorites")
    fun history() = read("history")
    fun isFavorite(id: String) = favorites().any { it.provider + it.id == id }
    fun toggleFavorite(comic: Comic) { val list = favorites(); if (list.any { it.provider + it.id == comic.provider + comic.id }) list.removeAll { it.provider + it.id == comic.provider + comic.id } else list.add(0, comic); write("favorites", list) }
    fun addHistory(comic: Comic) { val list = history(); list.removeAll { it.provider + it.id == comic.provider + comic.id }; list.add(0, comic); write("history", list.take(100)) }
    fun cachedHome(): List<Comic> {
        if (System.currentTimeMillis() - prefs.getLong("home_cache_at", 0L) > 24L * 60L * 60L * 1000L) return emptyList()
        return runCatching { gson.fromJson<List<Comic>>(prefs.getString("home_cache", "[]"), object : TypeToken<List<Comic>>() {}.type) ?: emptyList() }.getOrDefault(emptyList())
    }
    fun cacheHome(data: List<Comic>) {
        prefs.edit().putString("home_cache", gson.toJson(data))
            .putLong("home_cache_at", System.currentTimeMillis()).apply()
    }
    fun uiTheme() = prefs.getString("ui_theme", "Material 3 + Dynamic Island") ?: "Material 3 + Dynamic Island"
    fun setUiTheme(value: String) { prefs.edit().putString("ui_theme", value).apply() }
    fun speed() = prefs.getInt("speed", 1)
    fun setSpeed(v: Int) { prefs.edit().putInt("speed", v.coerceIn(1, 6)).apply() }
    fun autoNext() = prefs.getBoolean("auto_next", true)
    fun setAutoNext(v: Boolean) { prefs.edit().putBoolean("auto_next", v).apply() }
}

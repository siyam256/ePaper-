package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LookupItem(
    val id: String,
    val word: String,
    val contextSentence: String,
    val timestamp: Long,
    val meaningBengali: String,
    val contextualMeaning: String,
    val grammarRules: String,
    val engExample: String,
    val banglaExample: String
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("epaper_reader_prefs", Context.MODE_PRIVATE)
    private val moshi: Moshi = Moshi.Builder().build()
    private val listType = Types.newParameterizedType(List::class.java, LookupItem::class.java)
    private val listAdapter = moshi.adapter<List<LookupItem>>(listType)

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("gemini_api_key", apiKey.trim()).apply()
    }

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveTheme(theme: String) {
        prefs.edit().putString("reader_theme", theme).apply()
    }

    fun getTheme(): String {
        return prefs.getString("reader_theme", "Sepia") ?: "Sepia"
    }

    fun saveFontSize(size: Float) {
        prefs.edit().putFloat("reader_font_size", size).apply()
    }

    fun getFontSize(): Float {
        return prefs.getFloat("reader_font_size", 18f)
    }

    fun getLookupHistory(): List<LookupItem> {
        val json = prefs.getString("lookup_history", "[]") ?: "[]"
        return try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addLookupItem(item: LookupItem) {
        val currentList = getLookupHistory().toMutableList()
        // Avoid duplicate searches of the exact same word
        currentList.removeAll { it.word.equals(item.word, ignoreCase = true) }
        currentList.add(0, item) // Add to the top
        
        // Limit history to 50 items
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        val json = listAdapter.toJson(currentList)
        prefs.edit().putString("lookup_history", json).apply()
    }

    fun removeLookupItem(id: String) {
        val currentList = getLookupHistory().toMutableList()
        currentList.removeAll { it.id == id }
        val json = listAdapter.toJson(currentList)
        prefs.edit().putString("lookup_history", json).apply()
    }

    fun clearHistory() {
        prefs.edit().putString("lookup_history", "[]").apply()
    }
}

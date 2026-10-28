package com.alaa.universe

import android.content.SharedPreferences
import org.json.JSONArray
import java.security.MessageDigest

/** Only opaque IDs and hashed scopes are stored, never provider logins or URLs. */
internal class MediaOrder(private val prefs: SharedPreferences) {
    private fun key(scope: List<String>) = MessageDigest.getInstance("SHA-256")
        .digest(JSONArray(scope).toString().toByteArray()).joinToString("") { "%02x".format(it) }
    private fun saved(scope: List<String>): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key(scope), "[]"))
        (0 until array.length()).map { array.getString(it) }.distinct()
    }.getOrDefault(emptyList())
    fun ids(scope: List<String>, available: List<String>) = merge(saved(scope), available)
    fun save(scope: List<String>, ids: List<String>): Boolean {
        // Retain temporarily absent IDs at the tail, so returning channels are not forgotten.
        val present = ids.toHashSet()
        val all = ids.distinct() + saved(scope).filterNot { it in present }
        return prefs.edit().putString(key(scope), JSONArray(all).toString()).commit()
    }
    companion object {
        fun merge(order: List<String>, available: List<String>): List<String> {
            val remaining = available.toCollection(LinkedHashSet())
            val result = ArrayList<String>(remaining.size)
            order.forEach { if (remaining.remove(it)) result.add(it) }
            result.addAll(remaining)
            return result
        }
    }
}

/** An uncommitted move is isolated from both cache refreshes and persistent order. */
internal class MediaMove(val scope: List<String>, val id: String, ids: List<String>) {
    var draft = ids.toList(); private set
    fun ids(available: List<String>) = MediaOrder.merge(draft, available)
    fun move(delta: Int, available: List<String>) {
        val next = ids(available).toMutableList()
        val from = next.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, next.lastIndex)
        java.util.Collections.swap(next, from, to)
        draft = next
    }
}

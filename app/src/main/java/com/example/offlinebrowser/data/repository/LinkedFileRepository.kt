package com.example.offlinebrowser.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class LinkedZimFile(val name: String, val uriString: String)

class LinkedFileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("linked_files_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "linked_files_list"

    fun getLinkedFiles(): List<LinkedZimFile> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<LinkedZimFile>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addLinkedFile(file: LinkedZimFile) {
        val list = getLinkedFiles().toMutableList()
        // Avoid duplicates by uri
        if (list.none { it.uriString == file.uriString }) {
            list.add(file)
            saveList(list)
        }
    }

    fun removeLinkedFile(file: LinkedZimFile) {
        val list = getLinkedFiles().toMutableList()
        val removed = list.removeIf { it.uriString == file.uriString }
        if (removed) {
            saveList(list)
        }
    }

    private fun saveList(list: List<LinkedZimFile>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}

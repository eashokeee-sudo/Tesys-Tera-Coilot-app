package com.tesys.lab.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Holds the editable parameter catalog.
 * First launch: copies assets/default_catalog.json to internal storage.
 * After that the user's file is the source of truth, so app updates never overwrite your edits.
 */
class CatalogStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "catalog.json")
    private val _params = MutableStateFlow<List<ParamDef>>(emptyList())
    val params: StateFlow<List<ParamDef>> = _params

    init { load() }

    private fun defaultText(): String =
        ctx.assets.open("default_catalog.json").bufferedReader().use { it.readText() }

    private fun load() {
        val list = try {
            if (!file.exists()) file.writeText(defaultText())
            CatalogJson.parse(file.readText())
        } catch (e: Exception) {
            CatalogJson.parse(defaultText())
        }
        _params.value = list
    }

    private fun persist() { file.writeText(CatalogJson.toJson(_params.value)) }

    /** Add a new parameter, or replace the one that had [originalId]. */
    fun upsert(p: ParamDef, originalId: String?) {
        val cur = _params.value.toMutableList()
        val idx = cur.indexOfFirst { it.id == (originalId ?: p.id) }
        if (idx >= 0) cur[idx] = p else cur.add(p)
        _params.value = cur
        persist()
    }

    fun delete(id: String) {
        _params.value = _params.value.filterNot { it.id == id }
        persist()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        _params.value = _params.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persist()
    }

    fun idExists(id: String) = _params.value.any { it.id == id }

    fun exportText(): String = CatalogJson.toJson(_params.value)

    /** Returns a human-readable result message. */
    fun importText(text: String, merge: Boolean): String = try {
        val incoming = CatalogJson.parse(text)
        if (incoming.isEmpty()) "Nothing imported: no valid parameters found."
        else {
            if (merge) {
                val cur = _params.value.toMutableList()
                incoming.forEach { n ->
                    val i = cur.indexOfFirst { it.id == n.id }
                    if (i >= 0) cur[i] = n else cur.add(n)
                }
                _params.value = cur
            } else _params.value = incoming
            persist()
            "Imported ${incoming.size} parameters (${if (merge) "merged" else "replaced"})."
        }
    } catch (e: Exception) {
        "Import failed: ${e.message}"
    }

    fun resetToDefault() {
        file.writeText(defaultText())
        load()
    }
}

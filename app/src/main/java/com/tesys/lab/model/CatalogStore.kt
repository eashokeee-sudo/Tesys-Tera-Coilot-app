package com.tesys.lab.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Holds the editable parameter catalog.
 * First launch: copies assets/default_catalog.json to internal storage.
 * When the shipped catalog version is newer, the new defaults are installed and the parameters you
 * created yourself (ids starting with "u") are kept.
 */
class CatalogStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "catalog.json")
    private val _params = MutableStateFlow<List<ParamDef>>(emptyList())
    val params: StateFlow<List<ParamDef>> = _params

    init { load() }

    private fun defaultText(): String =
        ctx.assets.open("default_catalog.json").bufferedReader().use { it.readText() }

    private fun load() {
        val def = defaultText()
        val list = try {
            if (!file.exists()) {
                file.writeText(def)
                CatalogJson.parse(def)
            } else {
                val txt = file.readText()
                if (CatalogJson.version(txt) < CatalogJson.version(def)) {
                    val defaults = CatalogJson.parse(def)
                    val ids = defaults.map { it.id }.toSet()
                    val mine = CatalogJson.parse(txt).filter { it.id.startsWith("u") && it.id !in ids }
                    val merged = defaults + mine
                    file.writeText(CatalogJson.toJson(merged))
                    merged
                } else CatalogJson.parse(txt)
            }
        } catch (e: Exception) {
            CatalogJson.parse(def)
        }
        _params.value = list
    }

    private fun persist() { file.writeText(CatalogJson.toJson(_params.value)) }

    /** Add a new parameter, or replace the one that had [originalId]. */
    fun upsert(p: ParamDef, originalId: String?) {
        val cur = _params.value.toMutableList()
        val idx = cur.indexOfFirst { it.id == (originalId ?: p.id) }
        val old = cur.getOrNull(idx)
        // the editor does not know the special display fields: keep them when editing a shipped parameter
        val np = if (old != null && p.fmt.isEmpty() && p.length == 0) p.copy(fmt = old.fmt, length = old.length) else p
        if (idx >= 0) cur[idx] = np else cur.add(np)
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

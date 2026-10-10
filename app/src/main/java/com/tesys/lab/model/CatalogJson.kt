package com.tesys.lab.model

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of the parameter catalog. Tolerant: unknown / missing fields get defaults. */
object CatalogJson {

    fun toJson(params: List<ParamDef>): String {
        val arr = JSONArray()
        params.forEach { arr.put(toObj(it)) }
        return JSONObject().put("version", 1).put("params", arr).toString(2)
    }

    fun toObj(p: ParamDef): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("register", p.register)
        put("type", p.type.name)
        put("function", p.function)
        put("scale", p.scale)
        put("unit", p.unit)
        put("decimals", p.decimals)
        put("screen", p.screen)
        put("group", p.group)
        put("role", p.role)
        put("tile", p.tile)
        put("bitStyle", p.bitStyle.name)
        put("everyN", p.everyN)
        put("enabled", p.enabled)
        put("verified", p.verified)
        put("source", p.source)
        put("notes", p.notes)
        val e = JSONObject(); p.enumMap.forEach { (k, v) -> e.put(k.toString(), v) }
        put("enum", e)
        val b = JSONArray(); p.bits.forEach { b.put(JSONObject().put("bit", it.bit).put("name", it.name)) }
        put("bits", b)
    }

    fun parse(text: String): List<ParamDef> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("params") ?: JSONArray()
        val out = ArrayList<ParamDef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val reg = o.optInt("register", -1)
            if (reg < 1) continue
            val enumMap = LinkedHashMap<Long, String>()
            o.optJSONObject("enum")?.let { e ->
                e.keys().forEach { k -> k.toLongOrNull()?.let { enumMap[it] = e.optString(k) } }
            }
            val bits = ArrayList<BitDef>()
            o.optJSONArray("bits")?.let { b ->
                for (j in 0 until b.length()) b.optJSONObject(j)?.let {
                    bits.add(BitDef(it.optInt("bit"), it.optString("name")))
                }
            }
            out.add(
                ParamDef(
                    id = o.optString("id").ifBlank { "p$reg" },
                    name = o.optString("name").ifBlank { "Register $reg" },
                    register = reg,
                    type = runCatching { DType.valueOf(o.optString("type", "UINT16")) }.getOrDefault(DType.UINT16),
                    function = o.optInt("function", 3),
                    scale = o.optDouble("scale", 1.0),
                    unit = o.optString("unit", ""),
                    decimals = o.optInt("decimals", -1),
                    screen = o.optString("screen", "registers"),
                    group = o.optString("group", ""),
                    role = o.optString("role", ""),
                    tile = o.optBoolean("tile", false),
                    bitStyle = runCatching { BitStyle.valueOf(o.optString("bitStyle", "STATE")) }.getOrDefault(BitStyle.STATE),
                    enumMap = enumMap,
                    bits = bits,
                    everyN = o.optInt("everyN", 1).coerceAtLeast(1),
                    enabled = o.optBoolean("enabled", true),
                    verified = o.optBoolean("verified", false),
                    source = o.optString("source", ""),
                    notes = o.optString("notes", "")
                )
            )
        }
        return out
    }
}

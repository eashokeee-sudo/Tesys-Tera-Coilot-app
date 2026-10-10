package com.tesys.lab.engine

import com.tesys.lab.model.ParamDef

/** One Modbus request that covers one or more parameters with contiguous addresses. */
data class Block(val fc: Int, val start: Int, val qty: Int, val params: List<ParamDef>)

object Blocks {
    const val MAX_WORDS = 125   // Modbus limit for FC03/04 (also the TeSys Tera limit)
    const val MAX_BITS = 1900

    /**
     * Groups the parameters into as few requests as possible. Only directly adjacent / overlapping
     * parameters are merged (no gap-filling), so reserved registers are never read by accident.
     */
    fun build(params: List<ParamDef>): List<Block> {
        val out = ArrayList<Block>()
        params.groupBy { it.function }.forEach { (fc, list) ->
            val limit = if (fc == 1 || fc == 2) MAX_BITS else MAX_WORDS
            val sorted = list.sortedBy { it.address }
            var start = -1
            var end = -1
            var cur = ArrayList<ParamDef>()
            for (p in sorted) {
                val pEnd = p.address + p.span
                if (cur.isNotEmpty() && p.address <= end && maxOf(end, pEnd) - start <= limit) {
                    cur.add(p); end = maxOf(end, pEnd)
                } else {
                    if (cur.isNotEmpty()) out.add(Block(fc, start, end - start, cur))
                    cur = arrayListOf(p); start = p.address; end = pEnd
                }
            }
            if (cur.isNotEmpty()) out.add(Block(fc, start, end - start, cur))
        }
        return out
    }
}

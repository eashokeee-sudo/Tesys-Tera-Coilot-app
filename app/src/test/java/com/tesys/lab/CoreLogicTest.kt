package com.tesys.lab

import com.tesys.lab.engine.Blocks
import com.tesys.lab.engine.Decoder
import com.tesys.lab.model.DType
import com.tesys.lab.model.ParamDef
import com.tesys.lab.modbus.Modbus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreLogicTest {

    @Test fun crc16_standardVector() {
        // 01 03 00 00 00 0A -> CRC C5 CD (low byte first)
        val b = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A)
        assertEquals(0xCDC5, Modbus.crc16(b))
    }

    @Test fun registerToAddress_matchesGuide() {
        // Guide: L1 RMS current register 5501 = address 0x157C; IAVG 5511 = 0x1586
        assertEquals(0x157C, ParamDef("l1", "L1", 5501).address)
        assertEquals(0x1586, ParamDef("iavg", "IAVG", 5511).address)
    }

    @Test fun guideExample_L1current_87485mA() {
        // Guide example: words 0x0001 0x55BD = 87 485 mA
        val p = ParamDef("l1", "L1", 5501, DType.UINT32, scale = 0.001, unit = "A")
        val r = Decoder.decode(p, intArrayOf(0x0001, 0x55BD), swapWords = false, swapBytes = false)
        assertEquals(87.485, r.value!!, 1e-9)
        assertEquals("87.485", r.text)
    }

    @Test fun flc1_register4467_isUint16_tenthsOfAmp() {
        // DOCA0355EN p.105: 0x1172 / register 4467, UINT16, 0.1 A  (4466 is reserved)
        val p = ParamDef("flc1", "FLC1", 4467, DType.UINT16, scale = 0.1, unit = "A")
        assertEquals(0x1172, p.address)
        val r = Decoder.decode(p, intArrayOf(450), swapWords = false, swapBytes = false)
        assertEquals(45.0, r.value!!, 1e-9)
        assertEquals("45.0", r.text)
    }

    @Test fun swapWords_and_swapBytes() {
        val p = ParamDef("x", "x", 1, DType.UINT32)
        assertEquals(0x000155BDL, Decoder.decode(p, intArrayOf(0x55BD, 0x0001), true, false).raw)
        val q = ParamDef("y", "y", 1, DType.UINT16)
        assertEquals(0x03E8L, Decoder.decode(q, intArrayOf(0xE803), false, true).raw)   // guide endianness example 1000
    }

    @Test fun enumLabel_and_bitmap() {
        val st = ParamDef("st", "Status", 5558, enumMap = mapOf(1L to "STOP", 2L to "START", 4L to "RUN"))
        assertEquals("RUN", Decoder.decode(st, intArrayOf(4), false, false).text)
        val bm = ParamDef("bm", "Trip", 5646, DType.BITMAP32)
        val r = Decoder.decode(bm, intArrayOf(0x0000, 0x0009), false, false)
        assertEquals(9L, r.raw)
        assertNull(r.value)
    }

    @Test fun blocks_mergeContiguousOnly() {
        val ps = listOf(
            ParamDef("a", "a", 5501, DType.UINT32),
            ParamDef("b", "b", 5503, DType.UINT32),
            ParamDef("c", "c", 5505, DType.UINT32),
            ParamDef("d", "d", 5511, DType.UINT32),      // gap -> separate block
            ParamDef("e", "e", 5559, DType.UINT16)
        )
        val bl = Blocks.build(ps)
        assertEquals(3, bl.size)
        assertEquals(5500, bl[0].start); assertEquals(6, bl[0].qty)
        assertEquals(5510, bl[1].start); assertEquals(2, bl[1].qty)
        assertEquals(5558, bl[2].start); assertEquals(1, bl[2].qty)
    }

    @Test fun blocks_respect125WordLimit() {
        val ps = (0 until 70).map { ParamDef("p$it", "p$it", 1000 + it * 2, DType.UINT32) }   // 140 words contiguous
        val bl = Blocks.build(ps)
        assertEquals(2, bl.size)
        bl.forEach { assert(it.qty <= 125) }
    }

    @Test fun parseWordsAndBits() {
        val pdu = byteArrayOf(0x03, 0x04, 0x00, 0x01, 0x55, 0xBD.toByte())
        assertArrayEquals(intArrayOf(0x0001, 0x55BD), Modbus.parseWords(pdu, 2))
        val bits = byteArrayOf(0x02, 0x01, 0b0000_0101)
        assertArrayEquals(intArrayOf(1, 0, 1, 0), Modbus.parseBits(bits, 4))
    }
}

package com.tesys.lab

import com.tesys.lab.engine.Blocks
import com.tesys.lab.engine.Decoder
import com.tesys.lab.engine.FrameExplain
import com.tesys.lab.engine.LogParser
import com.tesys.lab.model.Codes
import com.tesys.lab.model.Commands
import com.tesys.lab.model.DType
import com.tesys.lab.model.ParamDef
import com.tesys.lab.modbus.Modbus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    @Test fun crc16_standardVector() {
        // 01 03 00 00 00 0A -> CRC C5 CD (low byte first)
        val b = byteArrayOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A)
        assertEquals(0xCDC5, Modbus.crc16(b))
    }

    @Test fun registerToAddress_matchesGuide() {
        assertEquals(0x157C, ParamDef("l1", "L1", 5501).address)
        assertEquals(0x1586, ParamDef("iavg", "IAVG", 5511).address)
    }

    @Test fun guideExample_L1current_87485mA() {
        val p = ParamDef("l1", "L1", 5501, DType.UINT32, scale = 0.001, unit = "A")
        val r = Decoder.decode(p, intArrayOf(0x0001, 0x55BD), swapWords = false, swapBytes = false)
        assertEquals(87.485, r.value!!, 1e-9)
        assertEquals("87.485", r.text)
    }

    @Test fun flc1_register4467_isUint16_tenthsOfAmp() {
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
        assertEquals(0x03E8L, Decoder.decode(q, intArrayOf(0xE803), false, true).raw)
    }

    @Test fun enumLabel_and_bitmap() {
        val st = ParamDef("st", "Status", 5558, enumMap = mapOf(1L to "STOP", 2L to "START", 4L to "RUN"))
        assertEquals("RUN", Decoder.decode(st, intArrayOf(4), false, false).text)
        val bm = ParamDef("bm", "Trip", 5646, DType.BITMAP32)
        val r = Decoder.decode(bm, intArrayOf(0x0000, 0x0009), false, false)
        assertEquals(9L, r.raw)
        assertNull(r.value)
    }

    @Test fun uint64_energy() {
        val p = ParamDef("e", "E", 5533, DType.UINT64, scale = 0.001, unit = "kWh")
        val r = Decoder.decode(p, intArrayOf(0, 0, 0x0001, 0x86A0), false, false)   // 100000 -> 100.000 kWh
        assertEquals(100.0, r.value!!, 1e-9)
    }

    @Test fun ascii_and_firmwareVersion() {
        val a = ParamDef("ref", "Ref", 9153, DType.UINT16, fmt = "ASCII", length = 2)
        assertEquals(2, a.span)
        assertEquals("LTMT", Decoder.decode(a, intArrayOf(0x4C54, 0x4D54), false, false).text)
        val v = ParamDef("fw", "FW", 9165, DType.UINT32, fmt = "FWVER")
        assertEquals("001.000.000", Decoder.decode(v, intArrayOf(0x0000, 0x0100), false, false).text)
    }

    @Test fun blocks_mergeContiguousOnly() {
        val ps = listOf(
            ParamDef("a", "a", 5501, DType.UINT32),
            ParamDef("b", "b", 5503, DType.UINT32),
            ParamDef("c", "c", 5505, DType.UINT32),
            ParamDef("d", "d", 5511, DType.UINT32),
            ParamDef("e", "e", 5559, DType.UINT16)
        )
        val bl = Blocks.build(ps)
        assertEquals(3, bl.size)
        assertEquals(5500, bl[0].start); assertEquals(6, bl[0].qty)
        assertEquals(5510, bl[1].start); assertEquals(2, bl[1].qty)
        assertEquals(5558, bl[2].start); assertEquals(1, bl[2].qty)
    }

    @Test fun blocks_respect125WordLimit() {
        val ps = (0 until 70).map { ParamDef("p$it", "p$it", 1000 + it * 2, DType.UINT32) }
        val bl = Blocks.build(ps)
        assertEquals(2, bl.size)
        bl.forEach { assertTrue(it.qty <= 125) }
    }

    @Test fun parseWordsAndBits() {
        val pdu = byteArrayOf(0x03, 0x04, 0x00, 0x01, 0x55, 0xBD.toByte())
        assertArrayEquals(intArrayOf(0x0001, 0x55BD), Modbus.parseWords(pdu, 2))
        val bits = byteArrayOf(0x02, 0x01, 0b0000_0101)
        assertArrayEquals(intArrayOf(1, 0, 1, 0), Modbus.parseBits(bits, 4))
    }

    @Test fun commandRegister_fromDivaTable() {
        // DIVA table: register address 49876 = 40001 + 9875 = 0x2693
        assertEquals(9875, 49876 - 40001)
        assertEquals(0x2693, Commands.HMI.address)
        assertEquals(9876, Commands.HMI.register)
        assertEquals(704, Commands.COMM.address)
        assertEquals(0x0001, Commands.START.value)       // bit 0 Motor run forward
        assertEquals(0x0008, Commands.TRIP_RESET.value)  // bit 3 Trip reset
        assertEquals(0x1000, Commands.STOP.value)        // bit 12 Motor stop
    }

    @Test fun writePdu_fc06() {
        assertArrayEquals(byteArrayOf(6, 0x26, 0x93.toByte(), 0x10, 0x00), Modbus.writePdu(0x2693, 0x1000))
    }

    @Test fun logParser_dateTimeBcd() {
        assertEquals("2026-10-07 14:30:30", LogParser.dateTime(intArrayOf(0x3000, 0x1430, 0x1007, 0x2026)))
        assertEquals("-", LogParser.dateTime(intArrayOf(0, 0, 0, 0)))
        assertTrue(LogParser.dateTime(intArrayOf(0xABCD, 1, 2, 3)).startsWith("raw "))
    }

    @Test fun logParser_tripRecord() {
        val w = IntArray(32)
        w[0] = 0x3000; w[1] = 0x1430; w[2] = 0x1007; w[3] = 0x2026
        w[4] = 1; w[5] = 65; w[7] = 12345; w[18] = 4001; w[23] = 5000; w[16] = 120
        val t = LogParser.trip(1, w)!!
        assertEquals(1, t.code); assertEquals(65, t.thermal)
        assertEquals(12.345, t.l1, 1e-9)
        assertEquals(400.1, t.v12, 1e-6)
        assertEquals(50.0, t.freq, 1e-9)
        assertEquals(1.2, t.imbalance, 1e-9)
        assertNull(LogParser.trip(2, IntArray(32)))
    }

    @Test fun codes_trip_and_event() {
        assertEquals("Thermal overload trip", Codes.trip(1))
        assertEquals("Interlock 1 trip", Codes.trip(33))
        assertEquals("Thermal overload alarm", Codes.event(1))
        assertEquals("Thermal overload alarm reset", Codes.event(2))
        assertEquals("No voltage inhibit", Codes.event(641))
        assertEquals("No voltage inhibit reset", Codes.event(642))
        assertEquals("DI 1 ON", Codes.event(385))
        assertEquals("DI 1 OFF", Codes.event(386))
        assertEquals("Thermal overload trip reset", Codes.event(897))
    }

    @Test fun frameExplain_rtuRequest() {
        val b = byteArrayOf(1, 3, 0x15, 0x7C, 0, 2)
        val c = Modbus.crc16(b)
        val full = b + byteArrayOf((c and 0xFF).toByte(), (c shr 8).toByte())
        val rows = FrameExplain.rows(full, true, false, full, emptyList())
        assertTrue(rows.any { it.field == "Function" && it.meaning.startsWith("3 - Read holding registers") })
        assertTrue(rows.any { it.field == "CRC" && it.meaning.startsWith("OK") })
    }
}

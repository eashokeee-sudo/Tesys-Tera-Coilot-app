package com.tesys.lab.engine

import com.tesys.lab.model.ConnState
import com.tesys.lab.model.ParamDef
import com.tesys.lab.model.Reading
import com.tesys.lab.modbus.CommError
import com.tesys.lab.modbus.ModbusClient
import com.tesys.lab.modbus.ModbusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls whatever [params] returns, every cycle. The list is re-read at the start of each cycle, so added /
 * removed / edited parameters and tab changes take effect immediately without reconnecting.
 * A parameter the device refuses (Modbus exception) is retried only every [RETRY_CYCLES] cycles.
 */
class PollEngine(private val scope: CoroutineScope) {
    private val _readings = MutableStateFlow<Map<String, Reading>>(emptyMap())
    val readings: StateFlow<Map<String, Reading>> = _readings
    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private var job: Job? = null
    @Volatile private var fullNext = false

    fun setState(s: ConnState) { _state.value = s }
    fun clearReadings() { _readings.value = emptyMap() }

    /** Next cycle reads every parameter once, ignoring the per-parameter "poll every N" setting. */
    fun requestFull() { fullNext = true }

    fun start(
        client: ModbusClient,
        params: () -> List<ParamDef>,
        intervalMs: Long,
        swapWords: Boolean,
        swapBytes: Boolean,
        onCycle: () -> Unit
    ) {
        stop()
        _state.value = ConnState.CONNECTING
        _lastError.value = null
        fullNext = true
        job = scope.launch(Dispatchers.IO) {
            var cycle = 0L
            var ioFailures = 0
            val badUntil = HashMap<String, Long>()
            while (isActive) {
                val t0 = System.nanoTime()
                val full = fullNext
                if (full) fullNext = false
                val due = params().filter {
                    it.enabled &&
                        (full || cycle % it.everyN.coerceAtLeast(1) == 0L) &&
                        (full || (badUntil[it.id] ?: 0L) <= cycle)
                }
                val updates = HashMap<String, Reading>()
                var gotResponse = false
                var ioFailed = false

                loop@ for (b in Blocks.build(due)) {
                    try {
                        val data = fetch(client, b.fc, b.start, b.qty)
                        gotResponse = true
                        for (p in b.params) {
                            updates[p.id] = decodeSlice(p, b.start, data, swapWords, swapBytes)
                            badUntil.remove(p.id)
                        }
                    } catch (e: ModbusException) {
                        gotResponse = true
                        // One bad parameter must not blank the whole block: retry each one alone.
                        for (p in b.params) {
                            if (b.params.size == 1) {
                                updates[p.id] = errReading(e.code)
                                badUntil[p.id] = cycle + RETRY_CYCLES
                                continue
                            }
                            try {
                                val d = fetch(client, p.function, p.address, p.span)
                                updates[p.id] = decodeSlice(p, p.address, d, swapWords, swapBytes)
                                badUntil.remove(p.id)
                            } catch (e2: ModbusException) {
                                updates[p.id] = errReading(e2.code)
                                badUntil[p.id] = cycle + RETRY_CYCLES
                            } catch (e2: CancellationException) {
                                throw e2
                            } catch (e2: Exception) {
                                ioFailed = true; _lastError.value = e2.message; break@loop
                            }
                        }
                    } catch (e: CommError) {
                        ioFailed = true; _lastError.value = e.message; break@loop
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ioFailed = true; _lastError.value = e.message; break@loop
                    }
                }

                if (updates.isNotEmpty()) _readings.value = _readings.value + updates
                if (gotResponse && !ioFailed) {
                    ioFailures = 0
                    _state.value = ConnState.CONNECTED
                    _lastError.value = null
                } else if (ioFailed) {
                    ioFailures++
                    if (ioFailures >= 3) _state.value = ConnState.COMM_LOST
                }
                onCycle()
                cycle++
                val elapsed = (System.nanoTime() - t0) / 1_000_000
                delay((intervalMs - elapsed).coerceAtLeast(20))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun errReading(code: Int) =
        Reading(error = "EXC $code", text = "EXC $code", timeMs = System.currentTimeMillis())

    private suspend fun fetch(c: ModbusClient, fc: Int, addr: Int, qty: Int): IntArray =
        if (fc == 1 || fc == 2) c.readBits(fc, addr, qty) else c.readWords(fc, addr, qty)

    private fun decodeSlice(p: ParamDef, blockStart: Int, data: IntArray, sw: Boolean, sb: Boolean): Reading {
        val off = p.address - blockStart
        return Decoder.decode(p, data.copyOfRange(off, off + p.span), sw, sb)
    }

    companion object { const val RETRY_CYCLES = 30L }
}

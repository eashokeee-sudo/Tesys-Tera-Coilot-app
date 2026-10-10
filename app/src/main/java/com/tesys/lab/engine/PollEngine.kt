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
 * Polls whatever the catalog currently says, every cycle. The catalog is re-read at the start of
 * each cycle, so added / removed / edited parameters take effect immediately without reconnecting.
 */
class PollEngine(private val scope: CoroutineScope) {
    private val _readings = MutableStateFlow<Map<String, Reading>>(emptyMap())
    val readings: StateFlow<Map<String, Reading>> = _readings
    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private var job: Job? = null

    fun setState(s: ConnState) { _state.value = s }
    fun clearReadings() { _readings.value = emptyMap() }

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
        job = scope.launch(Dispatchers.IO) {
            var cycle = 0L
            var ioFailures = 0
            while (isActive) {
                val t0 = System.nanoTime()
                val due = params().filter { it.enabled && cycle % it.everyN.coerceAtLeast(1) == 0L }
                val updates = HashMap<String, Reading>()
                var gotResponse = false
                var ioFailed = false

                loop@ for (b in Blocks.build(due)) {
                    try {
                        val data = fetch(client, b.fc, b.start, b.qty)
                        gotResponse = true
                        for (p in b.params) updates[p.id] = decodeSlice(p, b.start, data, swapWords, swapBytes)
                    } catch (e: ModbusException) {
                        gotResponse = true
                        // One bad parameter must not blank the whole block: retry each one alone.
                        for (p in b.params) {
                            try {
                                val d = fetch(client, p.function, p.address, p.span)
                                updates[p.id] = decodeSlice(p, p.address, d, swapWords, swapBytes)
                            } catch (e2: ModbusException) {
                                updates[p.id] = Reading(error = "EXC ${e2.code}", text = "EXC ${e2.code}", timeMs = System.currentTimeMillis())
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

    private suspend fun fetch(c: ModbusClient, fc: Int, addr: Int, qty: Int): IntArray =
        if (fc == 1 || fc == 2) c.readBits(fc, addr, qty) else c.readWords(fc, addr, qty)

    private fun decodeSlice(p: ParamDef, blockStart: Int, data: IntArray, sw: Boolean, sb: Boolean): Reading {
        val off = p.address - blockStart
        return Decoder.decode(p, data.copyOfRange(off, off + p.span), sw, sb)
    }
}

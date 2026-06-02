package dev.uavonmap.app.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * Transport-layer abstraction: delivers raw bytes and accepts raw bytes to send.
 * Protocol-agnostic — any telemetry protocol (MAVLink, CRSF, MSP, …) sits on top.
 */
interface RawConnection {
    /** Start the connection; launches an internal reader coroutine in [readerScope]. */
    suspend fun connect(readerScope: CoroutineScope)

    /** Stream of raw byte chunks received from the transport. */
    val incomingBytes: SharedFlow<ByteArray>

    /** Send raw bytes over the transport. */
    suspend fun send(data: ByteArray)

    suspend fun close()

    fun isConnected(): Boolean
}

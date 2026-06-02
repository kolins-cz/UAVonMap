package dev.uavonmap.app.connection

import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * Abstraction for MAVLINK connections over different transports
 */
interface MavConnection {
    /**
     * Connect to the MAVLINK source
     * @param readerScope Coroutine scope for the connection reader
     */
    suspend fun connect(readerScope: CoroutineScope)

    /**
     * Get the flow of MAVLINK frames
     */
    val mavFrame: SharedFlow<MavFrame<out MavMessage<*>>>

    /**
     * Send a MAVLINK v1 message
     */
    suspend fun <T : MavMessage<T>> sendV1(systemId: UByte, componentId: UByte, payload: T)

    /**
     * Close the connection
     */
    suspend fun close()

    /**
     * Check if the connection is active
     */
    fun isConnected(): Boolean
}

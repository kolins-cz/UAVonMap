package dev.uavonmap.app.connection

import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.connection.tcp.TcpClientMavConnection
import com.divpundir.mavlink.definitions.ardupilotmega.ArdupilotmegaDialect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * Adapter for TCP client MAVLINK connections
 */
class TcpMavConnectionAdapter(
    private val host: String,
    private val port: Int
) : MavConnection {

    private lateinit var connection: CoroutinesMavConnection

    override suspend fun connect(readerScope: CoroutineScope) {
        connection = TcpClientMavConnection(host, port, ArdupilotmegaDialect).asCoroutine()
        connection.connect(readerScope)
    }

    override val mavFrame: SharedFlow<MavFrame<out MavMessage<*>>>
        get() = connection.mavFrame

    override suspend fun <T : MavMessage<T>> sendV1(systemId: UByte, componentId: UByte, payload: T) {
        connection.sendV1(systemId, componentId, payload)
    }

    override suspend fun close() {
        connection.close()
    }

    override fun isConnected(): Boolean {
        return ::connection.isInitialized
    }
}

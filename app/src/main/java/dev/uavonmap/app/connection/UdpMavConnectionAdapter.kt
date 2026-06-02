package dev.uavonmap.app.connection

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.connection.udp.UdpServerMavConnection
import com.divpundir.mavlink.definitions.ardupilotmega.ArdupilotmegaDialect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * UDP Server MAVLink connection adapter for ExpressLRS Backpack communication
 * 
 * ExpressLRS TX Backpack behavior:
 * - Broadcasts MAVLINK packets on UDP port 14550 (default)
 * - After receiving a response from client, switches to targeted transmission
 * 
 * This adapter listens for broadcasts and receives bidirectional MAVLINK data
 *  
 * TODO: Implement HEARTBEAT auto-response to trigger ExpressLRS targeted mode
 * The HEARTBEAT message construction requires enum types that may have different
 * names in the mavlink-kotlin library. Need to research correct enum names.
 */
class UdpMavConnectionAdapter(
    private val port: Int,
    dialect: ArdupilotmegaDialect = ArdupilotmegaDialect
) : MavConnection {

    private val connection: CoroutinesMavConnection = UdpServerMavConnection(
        port = port,
        dialect = dialect
    ).asCoroutine()

    override val mavFrame: SharedFlow<MavFrame<out MavMessage<*>>>
        get() = connection.mavFrame

    override suspend fun connect(readerScope: CoroutineScope) {
        connection.connect(readerScope)
        Log.d("UdpMavConnection", "UDP server listening on port $port")
    }

    override suspend fun <T : MavMessage<T>> sendV1(systemId: UByte, componentId: UByte, payload: T) {
        connection.sendV1(systemId, componentId, payload)
    }

    override suspend fun close() {
        connection.close()
        Log.d("UdpMavConnection", "UDP server closed")
    }

    override fun isConnected(): Boolean {
        // UDP server is always "connected" once started
        return true
    }
}

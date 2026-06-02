package dev.uavonmap.app.connection

import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * SKELETON: Bluetooth SPP (Serial Port Profile) MAVLINK connection
 * 
 * TODO: Implementation steps:
 * 1. Add Bluetooth permissions to AndroidManifest.xml:
 *    - BLUETOOTH, BLUETOOTH_ADMIN (API < 31)
 *    - BLUETOOTH_CONNECT, BLUETOOTH_SCAN (API >= 31)
 * 2. Implement device discovery UI to list paired Bluetooth devices
 * 3. Use BluetoothSocket with SPP UUID: 00001101-0000-1000-8000-00805F9B34FB
 * 4. Connect to device and get input/output streams
 * 5. Parse MAVLINK frames from InputStream in background coroutine
 * 6. Write MAVLINK frames to OutputStream
 * 7. Handle connection errors and disconnections gracefully
 * 
 * Reference: https://developer.android.com/guide/topics/connectivity/bluetooth
 */
class BluetoothSppMavConnection(
    private val deviceAddress: String
) : MavConnection {

    private val _mavFrame = MutableSharedFlow<MavFrame<out MavMessage<*>>>(replay = 0, extraBufferCapacity = 64)
    override val mavFrame: SharedFlow<MavFrame<out MavMessage<*>>> = _mavFrame

    override suspend fun connect(readerScope: CoroutineScope) {
        throw NotImplementedError(
            "Bluetooth SPP connection not yet implemented. " +
            "Requires BluetoothSocket, device pairing UI, and stream-based MAVLINK parsing."
        )
    }

    override suspend fun <T : MavMessage<T>> sendV1(systemId: UByte, componentId: UByte, payload: T) {
        throw NotImplementedError("Bluetooth SPP connection not yet implemented")
    }

    override suspend fun close() {
        // No-op for unimplemented
    }

    override fun isConnected(): Boolean = false
}

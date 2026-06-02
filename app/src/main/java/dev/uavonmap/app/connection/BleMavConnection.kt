package dev.uavonmap.app.connection

import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * SKELETON: Bluetooth Low Energy (BLE) MAVLINK connection
 * 
 * TODO: Implementation steps:
 * 1. Add Bluetooth LE permissions to AndroidManifest.xml:
 *    - BLUETOOTH, BLUETOOTH_ADMIN (API < 31)
 *    - BLUETOOTH_CONNECT, BLUETOOTH_SCAN (API >= 31)
 *    - ACCESS_FINE_LOCATION (required for BLE scanning)
 * 2. Implement BLE device scanning UI to discover MAVLINK-capable devices
 * 3. Define MAVLINK service UUID and characteristic UUIDs (TX/RX)
 *    - Common approach: Use Nordic UART Service (NUS) UUID
 *    - Or define custom service for MAVLINK
 * 4. Use BluetoothGatt to connect and discover services
 * 5. Subscribe to notifications on RX characteristic
 * 6. Handle BLE MTU limitations (20-512 bytes per packet)
 * 7. Implement frame fragmentation/reassembly for MAVLINK messages
 * 8. Write to TX characteristic to send MAVLINK frames
 * 
 * Reference: https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview
 * 
 * Note: BLE has lower throughput than SPP, may require rate limiting for high-frequency streams
 */
class BleMavConnection(
    private val deviceAddress: String
) : MavConnection {

    private val _mavFrame = MutableSharedFlow<MavFrame<out MavMessage<*>>>(replay = 0, extraBufferCapacity = 64)
    override val mavFrame: SharedFlow<MavFrame<out MavMessage<*>>> = _mavFrame

    override suspend fun connect(readerScope: CoroutineScope) {
        throw NotImplementedError(
            "BLE connection not yet implemented. " +
            "Requires BluetoothGatt, service/characteristic discovery, MTU negotiation, and frame fragmentation."
        )
    }

    override suspend fun <T : MavMessage<T>> sendV1(systemId: UByte, componentId: UByte, payload: T) {
        throw NotImplementedError("BLE connection not yet implemented")
    }

    override suspend fun close() {
        // No-op for unimplemented
    }

    override fun isConnected(): Boolean = false
}

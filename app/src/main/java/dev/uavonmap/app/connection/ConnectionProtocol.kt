package dev.uavonmap.app.connection

/**
 * Supported MAVLINK connection protocols
 */
enum class ConnectionProtocol(val displayName: String, val requiresNetwork: Boolean, val isImplemented: Boolean) {
    TCP("TCP", requiresNetwork = true, isImplemented = true),
    UDP("UDP", requiresNetwork = true, isImplemented = true),
    BLUETOOTH_SPP("Bluetooth SPP", requiresNetwork = false, isImplemented = false),
    BLE("Bluetooth LE", requiresNetwork = false, isImplemented = false),
    USB_SERIAL("USB Serial", requiresNetwork = false, isImplemented = false);

    companion object {
        fun fromOrdinal(ordinal: Int): ConnectionProtocol {
            return entries.getOrNull(ordinal) ?: TCP
        }
    }
}

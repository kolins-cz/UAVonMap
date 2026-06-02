package dev.uavonmap.app.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * SKELETON: USB Serial MAVLINK connection
 * 
 * TODO: Implementation steps:
 * 1. Add usb-serial-for-android library to build.gradle:
 *    implementation 'com.github.mik3y:usb-serial-for-android:3.7.3'
 * 2. Add USB Host feature to AndroidManifest.xml:
 *    <uses-feature android:name="android.hardware.usb.host" />
 * 3. Request USB device permissions using UsbManager
 * 4. Detect common USB-Serial chips:
 *    - FTDI (FT232, FT2232, etc.)
 *    - CH340/CH341
 *    - CP210x
 *    - PL2303
 * 5. Open UsbSerialPort with appropriate baud rate (typically 57600 or 115200 for MAVLINK)
 * 6. Read data in background coroutine and parse MAVLINK frames
 * 7. Write MAVLINK frames to serial port
 * 8. Handle device disconnection events
 * 
 * Reference: https://github.com/mik3y/usb-serial-for-android
 * 
 * Note: UI should show device selector instead of host/port inputs for USB Serial
 */
class UsbSerialMavConnection(
    private val vendorId: Int = 0,
    private val productId: Int = 0,
    private val baudRate: Int = 57600
) : RawConnection {

    private val _incomingBytes = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    override val incomingBytes: SharedFlow<ByteArray> = _incomingBytes

    override suspend fun connect(readerScope: CoroutineScope) {
        throw NotImplementedError(
            "USB Serial connection not yet implemented. " +
            "Requires usb-serial-for-android library, USB permissions, and device detection."
        )
    }

    override suspend fun send(data: ByteArray) {
        throw NotImplementedError("USB Serial connection not yet implemented")
    }

    override suspend fun close() {
        // No-op for unimplemented
    }

    override fun isConnected(): Boolean = false
}

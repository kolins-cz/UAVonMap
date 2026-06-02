package dev.uavonmap.app.connection

/**
 * Wire-level telemetry protocol — what format the bytes are in.
 * Orthogonal to ConnectionProtocol (which transport is used).
 */
enum class TelemetryProtocol(val displayName: String, val isImplemented: Boolean) {
    AUTO("Auto-detect",  isImplemented = false),
    MAVLINK2("MAVLink 2", isImplemented = true),
    CRSF("CRSF",         isImplemented = true),
    MSP("MSP",           isImplemented = false),
    LTM("LTM",           isImplemented = false),
    NMEA("NMEA 0183",    isImplemented = false),
    UBLOX("u-blox UBX",  isImplemented = false);

    companion object {
        fun fromOrdinal(ordinal: Int): TelemetryProtocol =
            entries.getOrNull(ordinal) ?: MAVLINK2
    }
}

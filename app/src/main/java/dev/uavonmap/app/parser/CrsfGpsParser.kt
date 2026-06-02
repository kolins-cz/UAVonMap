package dev.uavonmap.app.parser

import dev.uavonmap.app.connection.RawConnection
import dev.uavonmap.app.model.GpsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Parses CRSF GPS frames (type 0x02) from a raw byte stream.
 *
 * Strictly follows the TBS CRSF specification:
 * https://github.com/tbs-fpv/tbs-crsf-spec/blob/main/crsf.md
 *
 * Frame structure (broadcast frame):
 *   [sync 1B][frameLen 1B][type 1B][payload...][crc8 1B]
 *   frameLen = type(1) + payload + crc(1), valid range [2..62]
 *   CRC-8 with polynomial 0xD5, computed over type + payload only.
 *
 * GPS payload (15 bytes, big-endian):
 *   int32  latitude     degree / 10,000,000
 *   int32  longitude    degree / 10,000,000
 *   uint16 groundspeed  km/h / 100
 *   uint16 heading      degree / 100
 *   uint16 altitude     meter − 1000m offset
 *   uint8  satellites   count
 */
class CrsfGpsParser {

    private val buffer = ArrayDeque<Byte>()

    fun parseGps(transport: RawConnection): Flow<GpsData> = flow {
        buffer.clear()
        transport.incomingBytes.collect { chunk ->
            chunk.forEach { buffer.addLast(it) }
            var gps = nextGpsFrame()
            while (gps != null) {
                emit(gps)
                gps = nextGpsFrame()
            }
        }
    }

    /**
     * Tries to extract the next valid GPS frame from [buffer].
     * Advances past invalid bytes (bad sync, out-of-range length, CRC mismatch, wrong type).
     * Returns null when more data is needed.
     */
    private fun nextGpsFrame(): GpsData? {
        while (buffer.size >= 2) {
            val sync = buffer[0].toInt() and 0xFF
            if (!isValidSync(sync)) {
                buffer.removeFirst()
                continue
            }

            val frameLen = buffer[1].toInt() and 0xFF
            // Spec: valid range is [2..62]; discard sync byte if out of range
            if (frameLen < 2 || frameLen > 62) {
                buffer.removeFirst()
                continue
            }

            val totalLen = 2 + frameLen  // sync(1) + frameLen(1) + frameLen bytes
            if (buffer.size < totalLen) break  // need more data

            // Extract full frame and advance buffer regardless of CRC result
            val frame = ByteArray(totalLen) { buffer[it] }
            repeat(totalLen) { buffer.removeFirst() }

            // CRC-8 over type + payload (frame[2] .. frame[totalLen-2])
            val crcInput = frame.sliceArray(2 until totalLen - 1)
            if (crc8(crcInput) != (frame[totalLen - 1].toInt() and 0xFF)) continue

            val type = frame[2].toInt() and 0xFF
            if (type != FRAME_TYPE_GPS) continue

            // frameLen = type(1) + payload + crc(1)  →  payloadLen = frameLen - 2
            val payloadLen = frameLen - 2
            if (payloadLen < GPS_PAYLOAD_SIZE) continue

            // Spec allows extra fields beyond known ones — safe to ignore them.
            // Payload starts at frame[3].
            return parseGpsPayload(frame, offset = 3)
        }
        return null
    }

    /**
     * Decodes the 15-byte GPS payload starting at [offset] in [frame].
     * All fields are big-endian per spec.
     */
    private fun parseGpsPayload(frame: ByteArray, offset: Int): GpsData {
        val lat   = readInt32BE(frame, offset)       // degree / 10,000,000
        val lon   = readInt32BE(frame, offset + 4)   // degree / 10,000,000
        val speed = readUInt16BE(frame, offset + 8)  // km/h / 100
        val hdg   = readUInt16BE(frame, offset + 10) // degree / 100
        val alt   = readUInt16BE(frame, offset + 12) // meter − 1000m offset
        // offset + 14: satellites (uint8) — no matching field in GpsData

        return GpsData(
            latitude  = lat / 1e7,
            longitude = lon / 1e7,
            altitude  = (alt - 1000).toDouble(),
            speed     = speed / 360f,   // km/h/100 → m/s  (÷100 for scale, ÷3.6 for unit)
            bearing   = hdg / 100f,     // cdeg → degrees
            hdop      = 1.0f            // not present in CRSF GPS frame
        )
    }

    companion object {
        private const val FRAME_TYPE_GPS   = 0x02
        private const val GPS_PAYLOAD_SIZE = 15  // 4+4+2+2+2+1

        /**
         * Returns true if [b] is a valid CRSF sync byte per spec:
         * the serial sync byte (0xC8), broadcast address (0x00), or any device address.
         */
        private fun isValidSync(b: Int): Boolean = when {
            b == 0x00 || b == 0xC8                         -> true  // broadcast / serial sync
            b == 0x0E || b in 0x10..0x14                   -> true  // Cloud, USB, BT, WiFi, VRx
            b in 0x20..0x7F                                -> true  // dynamic address space
            b == 0x80 || b == 0x8A                         -> true  // OSD, reserved
            b in 0x90..0x97                                -> true  // ESC 1–8
            b == 0xB0 || b == 0xB2                         -> true  // Crossfire reserved
            b == 0xC0 || b == 0xC2 || b == 0xC4            -> true  // sensors
            b == 0xC8 || b == 0xCA || b == 0xCC || b == 0xCE -> true  // FC, race tag, VTX
            b in 0xEA..0xEE                                -> true  // RC, repeaters, TX/RX module
            b == 0xF0 || b == 0xF2                         -> true  // reserved
            else                                           -> false
        }

        /**
         * CRC-8 with polynomial 0xD5 (DVB-S2), computed over [data].
         * Table is generated algorithmically — matches the spec reference table exactly.
         */
        private val CRC8_TABLE = IntArray(256).also { t ->
            for (i in 0..255) {
                var crc = i
                repeat(8) {
                    crc = if (crc and 0x80 != 0) (crc shl 1) xor 0xD5 else crc shl 1
                    crc = crc and 0xFF
                }
                t[i] = crc
            }
        }

        private fun crc8(data: ByteArray): Int {
            var crc = 0
            for (b in data) crc = CRC8_TABLE[crc xor (b.toInt() and 0xFF)]
            return crc
        }

        // --- Big-endian readers ---

        private fun readInt32BE(buf: ByteArray, off: Int): Int =
            ((buf[off    ].toInt() and 0xFF) shl 24) or
            ((buf[off + 1].toInt() and 0xFF) shl 16) or
            ((buf[off + 2].toInt() and 0xFF) shl  8) or
             (buf[off + 3].toInt() and 0xFF)

        private fun readUInt16BE(buf: ByteArray, off: Int): Int =
            ((buf[off    ].toInt() and 0xFF) shl 8) or
             (buf[off + 1].toInt() and 0xFF)
    }
}

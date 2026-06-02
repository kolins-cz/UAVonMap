package dev.uavonmap.app.parser

import android.util.Log
import com.divpundir.mavlink.api.MavDialect
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.connection.MavRawFrame
import com.divpundir.mavlink.definitions.ardupilotmega.ArdupilotmegaDialect
import com.divpundir.mavlink.definitions.common.GpsRawInt
import com.divpundir.mavlink.definitions.standard.GlobalPositionInt
import dev.uavonmap.app.connection.RawConnection
import dev.uavonmap.app.model.GpsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

private const val TAG = "MavlinkGpsParser"
private val STX_V1 = 0xFE.toByte()
private val STX_V2 = 0xFD.toByte()

/**
 * Parses MAVLink v1/v2 frames from a raw byte stream and extracts GPS positions.
 *
 * One instance per connection — the [buffer] accumulates bytes between packets.
 * For sending (e.g. data-stream requests), use the companion [sendV1].
 */
class MavlinkGpsParser(private val dialect: MavDialect = ArdupilotmegaDialect) {

    private val buffer = ArrayDeque<Byte>()

    fun parseGps(transport: RawConnection): Flow<GpsData> = flow {
        buffer.clear()
        transport.incomingBytes.collect { chunk ->
            chunk.forEach { buffer.addLast(it) }
            var raw = nextFrame()
            while (raw != null) {
                toGpsData(raw)?.let { emit(it) }
                raw = nextFrame()
            }
        }
    }

    private fun toGpsData(raw: MavRawFrame): GpsData? {
        val companion = dialect.resolveCompanionOrNull(raw.messageId) ?: return null
        if (!raw.validateCrc(companion.crcExtra)) return null
        val msg = runCatching { companion.deserialize(raw.payload) }.getOrNull() ?: return null
        return when (msg) {
            is GpsRawInt -> {
                Log.d(TAG, "GPS_RAW_INT lat=${msg.lat / 1e7} lon=${msg.lon / 1e7}")
                GpsData(
                    latitude  = msg.lat / 1e7,
                    longitude = msg.lon / 1e7,
                    altitude  = msg.alt / 1000.0,
                    speed     = msg.vel.toInt() / 100f,
                    bearing   = msg.cog.toInt() / 100f,
                    hdop      = msg.eph.toInt() / 100f
                )
            }
            is GlobalPositionInt -> {
                Log.d(TAG, "GLOBAL_POSITION_INT lat=${msg.lat / 1e7} lon=${msg.lon / 1e7}")
                val vx = msg.vx / 100.0
                val vy = msg.vy / 100.0
                GpsData(
                    latitude  = msg.lat / 1e7,
                    longitude = msg.lon / 1e7,
                    altitude  = msg.alt / 1000.0,
                    speed     = sqrt(vx * vx + vy * vy).toFloat(),
                    bearing   = msg.hdg.toInt() / 100f
                )
            }
            else -> {
                Log.v(TAG, "Ignored msg id=${raw.messageId}")
                null
            }
        }
    }

    /**
     * Tries to read the next complete MAVLink frame from [buffer].
     * Returns null if more bytes are needed.
     * On a bad sync byte, discards it and retries.
     */
    private fun nextFrame(): MavRawFrame? {
        // Discard leading garbage until a sync byte
        while (buffer.isNotEmpty() && buffer.first() != STX_V1 && buffer.first() != STX_V2) {
            buffer.removeFirst()
        }
        if (buffer.size < 2) return null

        val isV2 = buffer.first() == STX_V2
        val payloadLen = buffer[1].toInt() and 0xFF

        val frameLen: Int = if (isV2) {
            if (buffer.size < 3) return null
            val incompatFlags = buffer[2].toInt() and 0xFF
            12 + payloadLen + if ((incompatFlags and 0x01) != 0) 13 else 0
        } else {
            8 + payloadLen
        }

        if (buffer.size < frameLen) return null

        val bytes = ByteArray(frameLen) { buffer[it] }
        repeat(frameLen) { buffer.removeFirst() }

        return runCatching {
            if (isV2) MavRawFrame.fromV2Bytes(bytes) else MavRawFrame.fromV1Bytes(bytes)
        }.onFailure {
            Log.w(TAG, "Bad frame (id might still decode): ${it.message}")
        }.getOrNull()
    }

    companion object {
        private val txSeq = AtomicInteger(0)

        /**
         * Serialise [payload] as a MAVLink v1 frame and write it to [transport].
         */
        suspend fun <T : MavMessage<T>> sendV1(
            transport: RawConnection,
            systemId: UByte,
            componentId: UByte,
            payload: T
        ) {
            val companion = payload.instanceCompanion
            val frame = MavRawFrame.createV1(
                seq         = (txSeq.getAndIncrement() and 0xFF).toUByte(),
                systemId    = systemId,
                componentId = componentId,
                messageId   = companion.id,
                payload     = payload.serializeV1(),
                crcExtra    = companion.crcExtra
            )
            transport.send(frame.rawBytes)
        }
    }
}

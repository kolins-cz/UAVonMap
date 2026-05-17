package dev.uavonmap.app.parser
import android.util.Log
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import com.divpundir.mavlink.definitions.common.GpsRawInt
import com.divpundir.mavlink.definitions.standard.GlobalPositionInt
import dev.uavonmap.app.model.GpsData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlin.math.sqrt
private const val TAG = "MavlinkParser"
object MavlinkParser {
    fun fromFrameFlow(frames: SharedFlow<MavFrame<out MavMessage<*>>>): Flow<GpsData> =
        frames.mapNotNull { frame ->
            when (val p = frame.message) {
                is GpsRawInt -> {
                    Log.d(TAG, "GPS_RAW_INT: lat=${p.lat/1e7} lon=${p.lon/1e7} alt=${p.alt/1000.0}m")
                    GpsData(p.lat/1e7, p.lon/1e7, p.alt/1000.0, p.vel.toInt()/100f, p.cog.toInt()/100f, p.eph.toInt()/100f)
                }
                is GlobalPositionInt -> {
                    Log.d(TAG, "GLOBAL_POSITION_INT: lat=${p.lat/1e7} lon=${p.lon/1e7} alt=${p.alt/1000.0}m")
                    val vx = p.vx/100.0; val vy = p.vy/100.0
                    GpsData(p.lat/1e7, p.lon/1e7, p.alt/1000.0, sqrt(vx*vx+vy*vy).toFloat(), p.hdg.toInt()/100f)
                }
                else -> { Log.v(TAG, "Frame: ${p?.javaClass?.name}"); null }
            }
        }
}
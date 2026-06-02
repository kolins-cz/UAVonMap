package dev.uavonmap.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.definitions.common.MavDataStream
import com.divpundir.mavlink.definitions.common.RequestDataStream
import dev.uavonmap.app.MainActivity
import dev.uavonmap.app.connection.ConnectionProtocol
import dev.uavonmap.app.connection.RawConnection
import dev.uavonmap.app.connection.TcpRawConnection
import dev.uavonmap.app.connection.TelemetryProtocol
import dev.uavonmap.app.connection.UdpRawConnection
import dev.uavonmap.app.parser.MavlinkGpsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    companion object {
        const val EXTRA_HOST           = "host"
        const val EXTRA_PORT           = "port"
        const val EXTRA_PROTOCOL       = "protocol"
        const val EXTRA_TELEM_PROTOCOL = "telem_protocol"
        private const val CHANNEL_ID      = "mock_location_channel"
        private const val NOTIFICATION_ID = 1
        private const val PROVIDER        = LocationManager.GPS_PROVIDER
        private const val TAG             = "MockLocService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    private val binder = LocalBinder()
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null

    private lateinit var locationManager: LocationManager

    var statusMessage: String = "Idle"
        private set
    var onStatusChanged: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
        val port = intent.getIntExtra(EXTRA_PORT, 14550)
        val protocolOrdinal = intent.getIntExtra(EXTRA_PROTOCOL, ConnectionProtocol.TCP.ordinal)
        val protocol = ConnectionProtocol.fromOrdinal(protocolOrdinal)
        val telemOrdinal = intent.getIntExtra(EXTRA_TELEM_PROTOCOL, TelemetryProtocol.MAVLINK2.ordinal)
        val telemetry = TelemetryProtocol.fromOrdinal(telemOrdinal)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
        addMockProvider()
        startStreaming(host, port, protocol, telemetry)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        connectionJob?.cancel()
        removeMockProvider()
    }

    private fun startStreaming(host: String, port: Int, transport: ConnectionProtocol, telemetry: TelemetryProtocol) {
        connectionJob?.cancel()

        val conn: RawConnection = when (transport) {
            ConnectionProtocol.TCP -> TcpRawConnection(host, port)
            ConnectionProtocol.UDP -> UdpRawConnection(port)
            ConnectionProtocol.BLUETOOTH_SPP,
            ConnectionProtocol.BLE,
            ConnectionProtocol.USB_SERIAL -> {
                updateStatus("Error: ${transport.displayName} transport not implemented")
                return
            }
        }

        connectionJob = scope.launch {
            val endpoint = when (transport) {
                ConnectionProtocol.UDP -> "UDP :$port"
                else -> "$host:$port"
            }
            updateStatus("Connecting to $endpoint via ${telemetry.displayName}…")
            try {
                conn.connect(readerScope = this)
                Log.d(TAG, "Transport connected: ${transport.displayName}")

                when (telemetry) {
                    TelemetryProtocol.MAVLINK2 -> {
                        updateStatus("Connected — requesting MAVLink streams…")
                        launch { requestMavlinkDataStreams(conn) }
                        updateStatus("Connected — waiting for GPS fix…")
                        MavlinkGpsParser().parseGps(conn).collect { gps ->
                            pushLocation(gps.latitude, gps.longitude, gps.altitude,
                                gps.speed, gps.bearing, gps.hdop)
                            updateStatus("Fix: %.6f, %.6f  alt=%.1fm".format(
                                gps.latitude, gps.longitude, gps.altitude))
                        }
                    }
                    else -> updateStatus("Error: ${telemetry.displayName} parser not yet implemented")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}", e)
                updateStatus("Error: ${e.message}")
            } finally {
                runCatching { conn.close() }
                updateStatus("Disconnected")
            }
        }
    }

    private suspend fun requestMavlinkDataStreams(conn: RawConnection) {
        val streams = listOf(
            MavDataStream.ALL,
            MavDataStream.POSITION,
            MavDataStream.RAW_SENSORS,
            MavDataStream.EXTENDED_STATUS
        )
        for (stream in streams) {
            runCatching {
                MavlinkGpsParser.sendV1(
                    transport   = conn,
                    systemId    = 255u,
                    componentId = 190u,
                    payload = RequestDataStream(
                        targetSystem    = 1u,
                        targetComponent = 1u,
                        reqStreamId     = MavEnumValue.of(stream),
                        reqMessageRate  = 4u,
                        startStop       = 1u
                    )
                )
                Log.d(TAG, "Requested stream: $stream")
            }.onFailure { Log.w(TAG, "Stream request failed: ${it.message}") }
        }
    }

    fun stopStreaming() {
        connectionJob?.cancel()
        updateStatus("Idle")
        removeMockProvider()
        stopSelf()
    }

    private fun addMockProvider() {
        runCatching {
            locationManager.addTestProvider(
                PROVIDER, false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(PROVIDER, true)
        }
    }

    private fun removeMockProvider() {
        runCatching {
            locationManager.setTestProviderEnabled(PROVIDER, false)
            locationManager.removeTestProvider(PROVIDER)
        }
    }

    private fun pushLocation(lat: Double, lon: Double, alt: Double,
                             speed: Float, bearing: Float, hdop: Float) {
        val loc = Location(PROVIDER).apply {
            latitude             = lat
            longitude            = lon
            altitude             = alt
            this.speed           = speed
            this.bearing         = bearing
            accuracy             = maxOf(hdop * 5f, 1f)
            time                 = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        runCatching { locationManager.setTestProviderLocation(PROVIDER, loc) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "UAVonMap GPS",
                NotificationManager.IMPORTANCE_LOW)
                .apply { description = "UAV mock location service" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UAVonMap")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateStatus(msg: String) {
        statusMessage = msg
        Handler(Looper.getMainLooper()).post {
            onStatusChanged?.invoke(msg)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(msg))
        }
    }
}


package dev.uavonmap.app.connection

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

private const val TAG = "UdpRawConnection"

/**
 * UDP server that listens on [port] for incoming datagrams.
 * Records the sender address of the first received packet and uses it for outgoing sends
 * (enables bidirectional comms with ExpressLRS TX Backpack etc.).
 */
class UdpRawConnection(private val port: Int) : RawConnection {

    private var socket: DatagramSocket? = null
    private var remoteAddr: InetSocketAddress? = null
    private val _incomingBytes = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 256)
    override val incomingBytes: SharedFlow<ByteArray> = _incomingBytes.asSharedFlow()

    override suspend fun connect(readerScope: CoroutineScope) {
        val s = withContext(Dispatchers.IO) { DatagramSocket(port) }
        socket = s
        Log.d(TAG, "UDP listening on port $port")
        readerScope.launch(Dispatchers.IO) {
            val buf = ByteArray(65535)
            val packet = DatagramPacket(buf, buf.size)
            try {
                while (!s.isClosed) {
                    s.receive(packet)
                    if (remoteAddr == null)
                        remoteAddr = InetSocketAddress(packet.address, packet.port)
                    _incomingBytes.emit(packet.data.copyOf(packet.length))
                }
            } catch (e: IOException) {
                Log.d(TAG, "Reader ended: ${e.message}")
            }
        }
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val addr = remoteAddr ?: return@withContext
        try {
            socket?.send(DatagramPacket(data, data.size, addr.address, addr.port))
        } catch (e: IOException) {
            Log.w(TAG, "Send failed: ${e.message}")
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        Log.d(TAG, "Closed")
        Unit
    }

    override fun isConnected(): Boolean = socket?.isClosed == false
}

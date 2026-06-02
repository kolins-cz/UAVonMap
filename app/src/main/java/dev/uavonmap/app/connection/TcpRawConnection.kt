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
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "TcpRawConnection"

class TcpRawConnection(
    private val host: String,
    private val port: Int
) : RawConnection {

    private var socket: Socket? = null
    private val _incomingBytes = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 256)
    override val incomingBytes: SharedFlow<ByteArray> = _incomingBytes.asSharedFlow()

    override suspend fun connect(readerScope: CoroutineScope) {
        val s = withContext(Dispatchers.IO) {
            Socket().also { it.connect(InetSocketAddress(host, port), 10_000) }
        }
        socket = s
        Log.d(TAG, "Connected to $host:$port")
        readerScope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            try {
                while (!s.isClosed) {
                    val n = s.inputStream.read(buf)
                    if (n < 0) break
                    _incomingBytes.emit(buf.copyOf(n))
                }
            } catch (e: IOException) {
                Log.d(TAG, "Reader ended: ${e.message}")
            }
        }
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.outputStream ?: return@withContext
        try {
            out.write(data)
            out.flush()
        } catch (e: IOException) {
            Log.w(TAG, "Send failed: ${e.message}")
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        socket?.close()
        Log.d(TAG, "Closed")
        Unit
    }

    override fun isConnected(): Boolean =
        socket?.let { it.isConnected && !it.isClosed } ?: false
}

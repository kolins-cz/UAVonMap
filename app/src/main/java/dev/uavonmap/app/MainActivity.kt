package dev.uavonmap.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.uavonmap.app.databinding.ActivityMainBinding
import dev.uavonmap.app.service.MockLocationService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var service: MockLocationService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MockLocationService.LocalBinder).getService()
            bound = true
            service!!.onStatusChanged = { msg -> runOnUiThread { updateStatus(msg) } }
            updateStatus(service!!.statusMessage)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
            service = null
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) doConnect()
        else updateStatus("Location permission denied — cannot provide mock location")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = getSharedPreferences("conn", MODE_PRIVATE)
        binding.editHost.setText(prefs.getString("host", "192.168.10.100"))
        binding.editPort.setText(prefs.getInt("port", 14550).toString())

        binding.btnConnect.setOnClickListener { requestPermissionsAndConnect() }
        binding.btnDisconnect.setOnClickListener { doDisconnect() }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, MockLocationService::class.java).also {
            bindService(it, connection, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) { unbindService(connection); bound = false }
    }

    private fun requestPermissionsAndConnect() {
        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) doConnect() else permLauncher.launch(needed.toTypedArray())
    }

    private fun doConnect() {
        val host = binding.editHost.text?.toString()?.trim() ?: ""
        val port = binding.editPort.text?.toString()?.toIntOrNull() ?: 14550
        if (host.isEmpty()) { updateStatus("Enter a host/IP address"); return }

        prefs.edit().putString("host", host).putInt("port", port).apply()

        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_HOST, host)
            putExtra(MockLocationService.EXTRA_PORT, port)
        }
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        binding.btnConnect.isEnabled    = false
        binding.btnDisconnect.isEnabled = true
        binding.editHost.isEnabled      = false
        binding.editPort.isEnabled      = false
        updateStatus("Connecting…")
    }

    private fun doDisconnect() {
        service?.stopStreaming()
        binding.btnConnect.isEnabled    = true
        binding.btnDisconnect.isEnabled = false
        binding.editHost.isEnabled      = true
        binding.editPort.isEnabled      = true
        updateStatus("Idle")
    }

    private fun updateStatus(msg: String) {
        val isfix = msg.startsWith("Fix:")
        binding.tvStatus.text = if (isfix) "● Connected" else "Status: $msg"
        if (isfix) binding.tvGpsFix.text = msg
        else if (msg == "Idle" || msg == "Disconnected") binding.tvGpsFix.text = ""
    }
}

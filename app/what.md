# MAVLINK GPS Mock Location Provider - Project Context

## Project Goal
Build an Android app that connects to a MAVLINK telemetry stream via multiple connection types (TCP, UDP, USB-UART, Bluetooth), decodes GPS data, and provides it as a Mock Location for Android system.

## Key Requirements

### Must Have
- ✅ **Zero Google dependencies** (no Google Play Services, no Google account required)
- ✅ Works on degoogled Android devices (LineageOS, GrapheneOS, etc.)
- ✅ Android Mock Location Provider implementation
- ✅ Multiple connection types: TCP, UDP, USB-Serial, Bluetooth (Classic & BLE)
- ✅ MAVLINK protocol support (v1 and v2)
- ✅ GPS data extraction (latitude, longitude, altitude, speed, heading)
- ✅ Background service (foreground service for Android O+)
- ✅ Simple, clean UI for connection management

### Must Avoid
- ❌ Google Play Services
- ❌ Firebase
- ❌ Any com.google.android.gms dependencies
- ❌ Technology debt from existing projects

## Technical Stack

### Core
- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **IDE**: Android Studio
- **Min SDK**: API 23 (Android 6.0)
- **Target SDK**: API 34+

### Key Libraries
```gradle
dependencies {
    // MAVLINK protocol
    implementation("io.dronefleet.mavlink:mavlink:2.x.x")
    
    // USB Serial communication
    implementation("com.github.mik3y:usb-serial-for-android:3.x.x")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.x.x")
    
    // AndroidX - Pure Android, no Google
    implementation("androidx.core:core-ktx:1.x.x")
    implementation("androidx.appcompat:appcompat:1.x.x")
}
```

### Permissions Required
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-feature android:name="android.hardware.usb.host" />
```

## Architecture

### App as MAVLINK Endpoint (Not Sniffer)
The app acts as the **destination** for MAVLINK data, not a passive packet interceptor:
```
MAVLINK Source → [TCP/UDP/USB/BT] → Android App → Mock Location Provider
```

### Component Structure
```
app/
├── ConnectionManager (abstract)
│   ├── TcpConnection
│   ├── UdpConnection
│   ├── UsbSerialConnection
│   └── BluetoothConnection
├── MAVLinkParser
│   └── Extracts GPS messages (24, 33, 113)
├── MockLocationService (Foreground Service)
│   └── Implements LocationManager.addTestProvider()
└── MainActivity
    └── Connection UI + Controls
```

## MAVLINK GPS Details

### Relevant Message IDs
- **GPS_RAW_INT (24)**: Raw GPS data
- **GLOBAL_POSITION_INT (33)**: Global position estimate
- **GPS2_RAW (124)**: Secondary GPS (if needed)

### Coordinate Conversion
```kotlin
// MAVLINK stores lat/lon as int32 (degrees * 1e7)
val latitudeDegrees = mavlinkLatInt / 1e7
val longitudeDegrees = mavlinkLonInt / 1e7
val altitudeMeters = mavlinkAltMm / 1000.0
```

## Mock Location Provider Implementation

### Setup
```kotlin
// In Service or Activity
val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

// Add test provider
locationManager.addTestProvider(
    LocationManager.GPS_PROVIDER,
    false, false, false, false,
    true, true, true,
    Criteria.POWER_LOW, Criteria.ACCURACY_FINE
)

locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
```

### Updating Location
```kotlin
val location = Location(LocationManager.GPS_PROVIDER).apply {
    latitude = parsedLatitude
    longitude = parsedLongitude
    altitude = parsedAltitude
    accuracy = 5.0f // meters
    bearing = parsedHeading
    speed = parsedSpeed
    time = System.currentTimeMillis()
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
}

locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
```

### Cleanup
```kotlin
override fun onDestroy() {
    locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
}
```

## Connection Types

### TCP Client
```kotlin
val socket = Socket(ipAddress, port)
val inputStream = socket.getInputStream()
// Read MAVLINK bytes
```

### UDP Server
```kotlin
val socket = DatagramSocket(port)
val packet = DatagramPacket(buffer, buffer.size)
socket.receive(packet)
// Parse MAVLINK from packet.data
```

### USB-Serial
```kotlin
// Using usb-serial-for-android library
val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
val port = driver.ports[0]
port.open(connection)
port.setParameters(57600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
// Read bytes
```

### Bluetooth Classic
```kotlin
val socket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
socket.connect()
val inputStream = socket.inputStream
// Read MAVLINK bytes
```

## Background Service Requirements

### Foreground Service (Android O+)
```kotlin
class MockLocationService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS Mock Active")
            .setContentText("Receiving MAVLINK telemetry")
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }
}
```

## Device Setup

### Developer Options
1. Settings → About → Tap "Build number" 7 times
2. Developer Options → Enable USB Debugging
3. Developer Options → Select mock location app (your app)

### MAVLINK Source Configuration
Configure telemetry output to send to phone's IP:
```
# Example for ArduPilot
MAV_1_MODE = UDP
MAV_1_REMOTE_IP = 192.168.x.x  # Phone's IP
MAV_1_PORT = 14550
```

## Reference Implementation
Similar app (for reference only, don't copy): android-taranis-smartport-telemetry
- Has MAVLINK support
- Has USB-Serial implementation
- Has Bluetooth handling
- But includes unnecessary features (video, complex UI)

## Distribution
- Direct APK installation (sideload)
- Optional: F-Droid (open-source app store)
- Optional: GitHub Releases
- No Google Play Store needed

## Testing
- Physical Android device required (mock location doesn't work well in emulator)
- MAVLINK simulator: MAVProxy, QGroundControl
- Enable mock location in Developer Options
- Select this app as mock location provider

## Important Notes
- App acts as MAVLINK **endpoint**, not passive sniffer
- Cannot intercept packets passing through hotspot without root
- All standard Android APIs - no root required
- Works completely offline after dependencies downloaded
- No accounts or cloud services required

## Next Steps
1. Create project structure
2. Implement connection abstraction layer
3. Integrate MAVLINK library
4. Parse GPS messages
5. Implement Mock Location Provider
6. Build UI for connection management
7. Test with real MAVLINK source

---
*Last updated: 2026-05-17*
*Development environment: Windows 11, Android Studio with Copilot*

# UAVonMap

Android app that receives your UAV's GPS position via MAVLINK (TCP) and injects it as a **Mock Location** — so any map app on your phone (OsmAnd, Google Maps, etc.) shows your aircraft's position instead of your own.

```
Autopilot → MAVLINK/TCP → UAVonMap → Android Mock Location → Your map app
```

**No Google Play Services required.** Works on degoogled devices (LineageOS, GrapheneOS).

---

## Requirements

- Android 7.0+ (API 24)
- Developer Options enabled on the device
- MAVLINK source accessible over TCP (ArduPilot, PX4, MAVProxy, SITL)

## One-time device setup

1. Settings → About → tap **Build number** 7× to unlock Developer Options
2. Developer Options → enable **USB Debugging**
3. Developer Options → **Mock location app** → select **UAVonMap**
4. Grant **Location** permission when the app asks

## MAVLINK source setup

The app is a TCP **client** — your autopilot or MAVProxy must expose a TCP server:

```bash
# MAVProxy example
mavproxy.py --master /dev/ttyS0 --out tcpin:0.0.0.0:5763

# ArduPilot SITL
sim_vehicle.py -v ArduCopter --out tcpin:0.0.0.0:5763
```

Default connection in app: `192.168.10.100 : 14550`

---

## Build

```bash
# Clone
git clone git@github.com:kolins-cz/UAVonMap.git
cd UAVonMap

# Build debug APK (requires Android SDK)
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in **Android Studio** and press ▶ Run.

---

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| MAVLINK | [mavlink-kotlin](https://github.com/divyanshupundir/mavlink-kotlin) v1.2.15 |
| Connection | TCP client (`TcpClientMavConnection`) |
| Messages decoded | `GPS_RAW_INT` (24), `GLOBAL_POSITION_INT` (33) |
| Android API | `LocationManager.setTestProviderLocation()` |

## Known limitations

- TCP client only (no UDP, no USB-Serial, no Bluetooth yet)
- No auto-reconnect
- PX4 needs `SET_MESSAGE_INTERVAL` instead of `REQUEST_DATA_STREAM` (ArduPilot-style used currently)

## License

MIT


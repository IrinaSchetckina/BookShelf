package ua.readshelf.data.remote

/**
 * The device's own loopback, forwarded to the developer machine with
 * `adb reverse tcp:8080 tcp:8080`. Works the same on the emulator and on a USB or
 * wireless-debugging phone; 10.0.2.2 would only reach the host from the emulator.
 * The forward is dropped when the device disconnects, so run it again after reconnecting.
 */
actual val apiBaseUrl: String = "http://127.0.0.1:8080"

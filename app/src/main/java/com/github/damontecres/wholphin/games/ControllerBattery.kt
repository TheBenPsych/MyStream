package com.github.damontecres.wholphin.games

import android.os.Build
import android.view.InputDevice
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * Benflix (Idea Box 25): which game controllers are low on battery. Only Android 12 and newer tell apps a
 * controller's battery level, so older TVs and phones always get an empty list (and nothing shows).
 */
object ControllerBattery {
    data class Low(
        val name: String,
        val percent: Int,
    )

    /** Under this much counts as low */
    private const val LOW = 0.15f

    fun low(): List<Low> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Newer.read(LOW)
            } catch (ex: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

    /** Plain words, e.g. "Xbox controller battery: 12%" (null when nothing is low) */
    fun words(): String? =
        low().takeIf { it.isNotEmpty() }?.joinToString(" · ") { "${it.name.ifBlank { "Controller" }} battery: ${it.percent}%" }

    @RequiresApi(Build.VERSION_CODES.S)
    private object Newer {
        fun read(low: Float): List<Low> =
            InputDevice
                .getDeviceIds()
                .toList()
                .mapNotNull { id -> InputDevice.getDevice(id) }
                .filter { device ->
                    !device.isVirtual &&
                        (
                            device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                                device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                        )
                }.mapNotNull { device ->
                    val battery = device.batteryState
                    val level = battery.capacity
                    if (!battery.isPresent || level.isNaN() || level < 0f || level > low) {
                        null
                    } else {
                        Low(device.name.orEmpty(), (level * 100).roundToInt())
                    }
                }.distinctBy { it.name }
    }
}

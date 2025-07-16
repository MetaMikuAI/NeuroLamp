package com.metamiku.neurolamp

import android.graphics.Color


enum class DeviceMode {
    SPECIFIED, RANDOM, API, SYNC
}


data class LampDeviceConfig(
    val address: String,
    var enabled: Boolean = false,
    var alias: String = "NeuroLamp",
    var mode: DeviceMode = DeviceMode.SPECIFIED,
    var color: Int = Color.WHITE,
    var colorFormat: String = "RGBA"
) {
    fun serialize(): String {
        return listOf(
            address,
            enabled.toString(),
            alias.replace("|", ""),
            mode.name,
            color.toString(),
            colorFormat
        ).joinToString("|")
    }

    companion object {
        fun deserialize(data: String): LampDeviceConfig? {
            val parts = data.split("|")
            if (parts.size < 5) return null
            
            val colorFormat = if (parts.size >= 6) parts[5] else "RGBA"
            
            return LampDeviceConfig(
                address = parts[0],
                enabled = parts[1].toBooleanStrictOrNull() == true,
                alias = parts[2],
                mode = DeviceMode.valueOf(parts[3]),
                color = parts[4].toIntOrNull() ?: Color.WHITE,
                colorFormat = colorFormat
            )
        }
    }
}
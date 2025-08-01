package com.metamiku.neurolamp

import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log


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
        var apiColor: Int = Color.WHITE
        var apiUrl: String = "http://127.0.0.1:1219/example"

        suspend fun fetchApiColor() {
            withContext(Dispatchers.IO) {
                val TAG = "LampDeviceConfig"
                try {
                    Log.i(TAG, "Fetching color from API: $apiUrl")
                    val url = URL(apiUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val code = conn.responseCode
                    Log.i(TAG, "HTTP response code: $code")
                    if (code != 200) {
                        Log.w(TAG, "Non-200 response from API: $code")
                        conn.disconnect()
                        return@withContext
                    }

                    val colorStr = conn.inputStream.bufferedReader().readText().trim()
                    Log.i(TAG, "Received color string: $colorStr")
                    if (!colorStr.matches(Regex("^#[0-9A-Fa-f]{8}$"))) {
                        Log.w(TAG, "Invalid color format: $colorStr")
                        conn.disconnect()
                        return@withContext
                    }
                    
                    apiColor = android.graphics.Color.parseColor(colorStr)
                    Log.i(TAG, "apiColor updated: $apiColor")
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching color from API: ${e.message}", e)
                }
            }
        }

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
package com.metamiku.neurolamp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.graphics.Color
import kotlin.random.Random

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.safeRegisterReceiver(
    receiver: BroadcastReceiver,
    filter: IntentFilter
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("DEPRECATION")
        registerReceiver(receiver, filter)
    }
}

internal fun logBroadcastSent(tag:String, intent: Intent) {
    Log.i(tag, "Broadcast SENT: action=${intent.action}, extras=${intent.extras}")
}

internal fun logBroadcastReceived(tag:String, intent: Intent) {
    Log.i(tag, "Broadcast RECEIVED: action=${intent.action}, extras=${intent.extras}")
}


fun getSendDataByColor(color: Int, colorFormat: String = "RGBA"): ByteArray {
    val argb = color.toUInt()
    val a = argb shr 24
    val r = (argb shr 16) and 0xFFu
    val g = (argb shr 8) and 0xFFu
    val b = argb and 0xFFu

    val colorBytes = mutableListOf<Byte>()
    for (char in colorFormat.uppercase()) {
        val byteValue = when (char) {
            'A' -> a.toByte()
            'R' -> r.toByte()
            'G' -> g.toByte()
            'B' -> b.toByte()
            else -> 0x00.toByte()
        }
        colorBytes.add(byteValue)
    }

    return byteArrayOf(0xA0.toByte(), 0x69, 0x04, *colorBytes.toByteArray())
}

fun generateRandomColor(alphaByte: Byte = 0xFF.toByte()): Int {
    val hue = Random.nextFloat() * 360f
    val saturation = Random.nextFloat()
    val value = 1.0f

    val hsv = floatArrayOf(hue, saturation, value)
    val rgb = Color.HSVToColor(hsv)

    val alphaInt = alphaByte.toInt() and 0xFF
    return (alphaInt shl 24) or (rgb and 0x00FFFFFF)
}

fun extractAlphaByte(color: Int): Byte {
    return ((color shr 24) and 0xFF).toByte()
}

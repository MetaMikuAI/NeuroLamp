package com.metamiku.neurolamp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SearchActivity : AppCompatActivity() {
    private val TAG = "SearchActivity"
    private lateinit var deviceListText: TextView
    private lateinit var deviceCountText: TextView
    private var deviceCount: Int = 0

    private val deviceFoundReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context, intent: Intent) {
            deviceCount++
            val name = intent.getStringExtra("name") ?: "(Unknown)"
            val address = intent.getStringExtra("address") ?: "?"
            deviceListText.text = "Found Device $name ($address)"
            deviceCountText.text= "Found $deviceCount device(s) total"
            Log.i(TAG, "Device found: name=$name, address=$address, total=$deviceCount")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate called")
        
        enableEdgeToEdge()
        
        setContentView(R.layout.activity_search)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        findViewById<Button>(R.id.search_cancel_button).setOnClickListener {
            Log.i(TAG, "Cancel button clicked, stopping scan and finishing activity")
            stopScan()
            finish()
        }

        deviceListText = findViewById(R.id.search_found_device)
        deviceCountText = findViewById(R.id.search_device_count)

        startScan()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(BleIntents.ACTION_NEW_LAMP_CONFIG)
        safeRegisterReceiver(deviceFoundReceiver, filter)
        Log.i(TAG, "Broadcast receiver registered for device found events")
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(deviceFoundReceiver)
        Log.i(TAG, "Broadcast receiver unregistered")
    }

    private fun startScan() {
        val intent = Intent(BleIntents.ACTION_START_SCAN)
        intent.setPackage(this.packageName)
        sendBroadcast(intent)
        Toast.makeText(this, "Started scanning for nearby Ble devices", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "startScan called, broadcast sent")
    }

    private fun stopScan() {
        val intent = Intent(BleIntents.ACTION_STOP_SCAN)
        intent.setPackage(this.packageName)
        sendBroadcast(intent)
        Toast.makeText(this, "Stopped scanning for nearby Ble devices", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "stopScan called, broadcast sent")
    }
} 
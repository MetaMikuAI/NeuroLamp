package com.metamiku.neurolamp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat

class BleService : Service() {

    private val TAG = "BleService"
    private val TARGET_DEVICE_NAME = "SP621E"

    private lateinit var gattManager: GattConnectionManager
    private lateinit var manager: BluetoothManager
    private lateinit var adapter: BluetoothAdapter
    private lateinit var handler: Handler
    private lateinit var scanner: BluetoothLeScanner

    private var isScanning = false
    private val currentlyScannedDevices = mutableListOf<BluetoothDevice>()
    private val allDiscoveredBleDevices = mutableListOf<BluetoothDevice>()
    private val allLampDevicesConfig = mutableMapOf<String, LampDeviceConfig>()
    private val SCAN_PERIOD: Long = 10000

    private val bleServiceReceiver = object : BroadcastReceiver() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        @SuppressLint("NotifyDataSetChanged")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            logBroadcastReceived(TAG, intent)
            when (intent.action) {
                BleIntents.ACTION_UPDATE_CONFIG -> updateConfig(intent)
                BleIntents.ACTION_UPDATE_COLOR -> updateColor(intent)
                BleIntents.ACTION_TOGGLE_LAMP -> toggleLamp(intent)
                BleIntents.ACTION_START_SCAN -> startScan()
                BleIntents.ACTION_STOP_SCAN -> stopScan()
                BleIntents.ACTION_GET_CONFIG -> handleGetConfig(intent)
                BleIntents.ACTION_GET_ALL_CONFIGS -> broadcastAllLampConfigs()
                else -> Log.w(TAG, "onReceive: unknown action=${intent.action}")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "scanCallback.onScanResult called")
            if (!isScanning) return
            val device = result.device
            if (device == null || device.name != TARGET_DEVICE_NAME || currentlyScannedDevices.contains(device)) return
            val address = device.address
            Log.i(TAG, "New BLE device found: address=$address")
            currentlyScannedDevices.add(device)
            broadcastNewLampConfig(device.address, device.name)

            if (allDiscoveredBleDevices.contains(device)) return
            allDiscoveredBleDevices.add(device)
            allLampDevicesConfig[address] = LampDeviceConfig(address = device.address)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scanCallback.onScanFailed: errorCode=$errorCode")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        Log.i(TAG, "onCreate called")
        super.onCreate()
        manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        handler = Handler(Looper.getMainLooper())
        scanner = adapter.bluetoothLeScanner

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        gattManager = GattConnectionManager(
            context = this,
            bluetoothManager = bluetoothManager
        ) { address ->
            Log.i(TAG, "Device disconnected callback: address=$address")
            val intent = Intent(BleIntents.ACTION_LAMP_DISCONNECTED)
            intent.setPackage(this.packageName)
            intent.putExtra("address", address)
            sendBroadcast(intent)
            logBroadcastSent(TAG, intent)

            if (!allLampDevicesConfig.containsKey(address)) {
                Log.e(TAG, "Device disconnected: unknown address=$address")
                return@GattConnectionManager
            }
            allLampDevicesConfig[address]!!.enabled = false
        }

        val filter = IntentFilter().apply {
            addAction(BleIntents.ACTION_UPDATE_CONFIG)
            addAction(BleIntents.ACTION_UPDATE_COLOR)
            addAction(BleIntents.ACTION_TOGGLE_LAMP)
            addAction(BleIntents.ACTION_START_SCAN)
            addAction(BleIntents.ACTION_STOP_SCAN)
            addAction(BleIntents.ACTION_GET_CONFIG)
            addAction(BleIntents.ACTION_GET_ALL_CONFIGS)
        }
        safeRegisterReceiver(bleServiceReceiver, filter)
        Log.i(TAG, "Broadcast receiver registered")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan() {
        Log.i(TAG, "startScan called")
        if (isScanning) {
            Log.w(TAG, "startScan: already scanning")
            return
        }

        currentlyScannedDevices.clear()
        isScanning = true
        Log.i(TAG, "BLE scan started")
        scanner.startScan(scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            scanner.stopScan(scanCallback)
            if (!isScanning) return@postDelayed
            Log.i(TAG, "BLE scan stopped (timeout)")
            broadcastAllLampConfigs()
            isScanning = false
        }, SCAN_PERIOD)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        Log.i(TAG, "stopScan called")
        if (!isScanning) {
            Log.w(TAG, "stopScan: not currently scanning")
        }

        scanner.stopScan(scanCallback)
        isScanning = false
        Log.i(TAG, "BLE scan stopped manually")
        broadcastAllLampConfigs()
    }

    private val isLiveHandler = Handler(Looper.getMainLooper())

    private val logRunnable = object : Runnable {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun run() {
            allLampDevicesConfig.forEach {
                Log.d(TAG, "logRunnable: device=${it.value.serialize()}")
                flushLampStatus(it.value)
            }
            isLiveHandler.postDelayed(this, 3000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand called: action=${intent?.action}")
        when (intent?.action) {
            "START" -> startBleService()
        }
        return START_STICKY
    }

    private fun startBleService() {
        Log.i(TAG, "startBleService called")
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, "NeuroLamp")
            .setContentTitle("NeuroLamp")
            .setContentText("NeuroLamp is running")
            .setSmallIcon(R.drawable.lava_lamp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        startForeground(1, notification)
        handler.post(logRunnable)
    }

    private fun createNotificationChannel() {
        Log.i(TAG, "createNotificationChannel called")
        val channel = NotificationChannel(
            "NeuroLamp",
            "NeuroLamp",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(logRunnable)
        unregisterReceiver(bleServiceReceiver)
        Log.i(TAG, "onDestroy called")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun broadcastNewLampConfig(address: String, name: String) {
        Log.i(TAG, "broadcastNewLampConfig: address=$address, name=$name")
        val intent = Intent(BleIntents.ACTION_NEW_LAMP_CONFIG)
        intent.setPackage(this.packageName)
        intent.putExtra("name", name)
        intent.putExtra("address", address)
        sendBroadcast(intent)
        logBroadcastSent(TAG, intent)
    }

    private fun broadcastAllLampConfigs() {
        Log.i(TAG, "broadcastAllLampConfigs called")
        val intent = Intent(BleIntents.ACTION_ALL_LAMP_CONFIGS)
        intent.setPackage(this.packageName)
        val serializedList = ArrayList<String>()
        allLampDevicesConfig.forEach { serializedList.add(it.value.serialize()) }
        intent.putExtra("all_lamp_configs", serializedList)
        sendBroadcast(intent)
        logBroadcastSent(TAG, intent)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updateConfig(intent: Intent) {
        val configJson = intent.getStringExtra("updated_config")
        if (configJson == null) {
            Log.w(TAG, "updateConfig: configJson is null")
            return
        }

        val config = LampDeviceConfig.deserialize(configJson)
        if (config == null) {
            Log.w(TAG, "updateConfig: deserialization failed")
            return
        }

        allLampDevicesConfig[config.address] = config
        flushLampStatus(config)
        Log.i(TAG, "updateConfig: updated config for address=${config.address}")
    }

    @OptIn(ExperimentalStdlibApi::class)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updateColor(intent: Intent) {
        val address = intent.getStringExtra("address")
        val color = intent.getIntExtra("color", Color.WHITE)

        if (address == null) {
            Log.e(TAG, "updateColor: address is null")
            return
        }

        if (!allLampDevicesConfig.containsKey(address)) {
            Log.e(TAG, "updateColor: unknown address=$address")
            return
        }

        val config = allLampDevicesConfig[address]!!
        config.color = color

        flushLampStatus(config)
        Log.i(TAG, "updateColor: updated color for address=$address, color=${color.toHexString()}")
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun toggleLamp(intent: Intent) {
        val address = intent.getStringExtra("address")
        val enabled = intent.getBooleanExtra("enabled", false)

        if (address == null) {
            Log.e(TAG, "toggleLamp: address is null")
            return
        }
        if (!allLampDevicesConfig.containsKey(address)) {
            Log.e(TAG, "toggleLamp: unknown address=$address")
            return
        }

        val config = allLampDevicesConfig[address]!!
        if (config.enabled == enabled) return

        config.enabled = enabled
        if (enabled) {
            gattManager.connect(address)
        } else {
            gattManager.disconnect(address)
        }
        Log.i(TAG, "toggleLamp: address=$address, enabled=$enabled")
    }

    private fun handleGetConfig(intent: Intent) {
        val address = intent.getStringExtra("address") ?: return
        val config = allLampDevicesConfig[address] ?: return
        val updateIntent = Intent(BleIntents.ACTION_UPDATE_CONFIG)
        updateIntent.putExtra("updated_config", config.serialize())
        updateIntent.setPackage(this.packageName)
        sendBroadcast(updateIntent)
        logBroadcastSent(TAG, updateIntent)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun flushLampStatus(config: LampDeviceConfig) {
        val address = config.address
        val alias = config.alias
        val enabled = config.enabled
        var color = config.color
        val mode = config.mode
        val colorFormat = config.colorFormat

        if (enabled && mode == DeviceMode.RANDOM) {
            color = generateRandomColor(extractAlphaByte(color))
        }
        if (!enabled) {
            color = 0x00000000
        }
        gattManager.sendData(address, getSendDataByColor(color, colorFormat))
    }
}

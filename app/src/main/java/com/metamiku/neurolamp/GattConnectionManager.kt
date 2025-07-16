package com.metamiku.neurolamp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission


class GattConnectionManager(
    private val context: Context,
    bluetoothManager: BluetoothManager,
    private val onDisconnected: (address: String) -> Unit = {}
) {
    private val TAG = "GattConnectionManager"
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val gattMap = mutableMapOf<String, BluetoothGatt>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        val address = device.address
        if (gattMap.containsKey(address)) {
            Log.i(TAG, "connect: device $address is already connected")
            return
        }
        Log.i(TAG, "connect: connecting to device $address (no data send)")
        val gatt = device.connectGatt(context, false, createSimpleCallback(address))
        gattMap[address] = gatt
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
            ?: throw IllegalArgumentException("Invalid Bluetooth address: $address")
        connect(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(address: String) {
        gattMap.remove(address)?.let {
            Log.i(TAG, "disconnect: disconnecting device $address")
            try {
                it.disconnect()
                it.close()
            } catch (e: Exception) {
                Log.e(TAG, "disconnect: close error: ${e.message}", e)
            }
        } ?: Log.w(TAG, "disconnect: device $address not found in connection map, cannot disconnect")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendData(address: String, data: ByteArray) {
        val gatt = gattMap[address]
        if (gatt == null) {
            Log.i(TAG, "sendData: device $address not connected, connecting and sending data")
            connectAndSend(address, data)
            return
        }
        writeToCharacteristic(gatt, data, address)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectAndSend(address: String, data: ByteArray) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
            ?: throw IllegalArgumentException("Invalid Bluetooth address: $address")
        if (gattMap.containsKey(address)) {
            sendData(address, data)
            return
        }
        Log.i(TAG, "connectAndSend: connecting to device $address and sending data")
        val gatt = device.connectGatt(context, false, createSendCallback(address, data))
        gattMap[address] = gatt
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun clearAll() {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "clearAll: missing BLUETOOTH_CONNECT permission")
            return
        }

        Log.i(TAG, "clearAll: clearing all GATT connections")
        val iterator = gattMap.entries.iterator()
        while (iterator.hasNext()) {
            val (address, gatt) = iterator.next()
            try {
                Log.i(TAG, "clearAll: disconnecting device $address")
                gatt.disconnect()
                gatt.close()
            } catch (e: SecurityException) {
                Log.w(TAG, "clearAll: disconnect failed for $address: ${e.message}", e)
            }
            iterator.remove()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun createSimpleCallback(address: String) = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "createSimpleCallback: [$address] connected (no data send)")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "createSimpleCallback: [$address] disconnected")
                    gatt.close()
                    gattMap.remove(address)
                    onDisconnected(address)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun createSendCallback(address: String, data: ByteArray) = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "createSendCallback: [$address] connected, discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "createSendCallback: [$address] disconnected")
                    gatt.close()
                    gattMap.remove(address)
                    onDisconnected(address)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "createSendCallback: [$address] service discovery failed: $status")
                return
            }
            Log.i(TAG, "createSendCallback: [$address] services discovered, sending data...")
            writeToCharacteristic(gatt, data, address)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "createSendCallback: [$address] write successful")
            } else {
                Log.w(TAG, "createSendCallback: [$address] write failed: $status")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeToCharacteristic(gatt: BluetoothGatt, data: ByteArray, address: String) {
        val service = gatt.services.firstOrNull()
        val characteristic = service?.characteristics?.firstOrNull {
            it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0
        }

        if (characteristic == null) {
            Log.w(TAG, "writeToCharacteristic: [$address] no writable characteristic found")
            return
        }
        characteristic.value = data
        val result = gatt.writeCharacteristic(characteristic)
        Log.i(TAG, "writeToCharacteristic: [$address] data send result: $result")
    }
}

package com.metamiku.neurolamp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.metamiku.neurolamp.databinding.ActivityMainBinding
import androidx.core.net.toUri
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatDelegate


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate called")

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

        if (!areAllRequiredPermissionsGranted()) {
            Log.w(TAG, "Not all required permissions are granted. Requesting permissions.")
            requestMissingPermissions()
        } else {
            Log.i(TAG, "All required permissions granted. Starting BLE service if permitted.")
            startBleServiceIfPermitted()
        }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission.")
        }
    }

    private fun startBleServiceIfPermitted() {
        Log.i(TAG, "Attempting to start BLE foreground service.")
        val intent = Intent(this, BleService::class.java).apply {
            action = "START"
        }
        startService(intent)
        Toast.makeText(this, "Foreground service started", Toast.LENGTH_SHORT).show()
    }

    private fun areAllRequiredPermissionsGranted(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = requiredPermissions.all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Permission check: allGranted=$allGranted, requiredPermissions=$requiredPermissions")
        return allGranted
    }

    private fun requestMissingPermissions() {
        val missingPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)

            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        Log.d(TAG, "Requesting missing permissions: $missingPermissions")

        if (missingPermissions.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Permission Request")
                .setMessage("Some permissions are not granted, and some features may not work properly. Please restart the app after granting permissions to take effect.")
                .setPositiveButton("OK") { _, _ ->
                    requestPermissions(missingPermissions.toTypedArray(), 1001)
                }
                .setCancelable(false)
                .show()
        } else {
            requestPermissions(missingPermissions.toTypedArray(), 1001)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (missingPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT) ||
                    missingPermissions.contains(Manifest.permission.BLUETOOTH_SCAN))) {

            Log.w(TAG, "Bluetooth permissions missing. Prompting user to grant in settings.")
            AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("To support Bluetooth connections while the app runs in background and discover nearby devices, please grant location and nearby device permissions manually.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy called")
    }
}

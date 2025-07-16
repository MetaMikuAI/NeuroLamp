package com.metamiku.neurolamp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import android.widget.RadioGroup
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import android.graphics.Color
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import android.util.Log


class LampDeviceConfigActivity : AppCompatActivity() {

    private val TAG = "LampDeviceConfigActivity"
    private var lampConfig: LampDeviceConfig? = null
    private lateinit var colorPickerView: ColorPickerView
    private lateinit var alphaSlideBar: AlphaSlideBar

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            logBroadcastReceived(TAG, intent)
            if (intent.action != BleIntents.ACTION_UPDATE_CONFIG) return
            val configStr = intent.getStringExtra("updated_config") ?: return
            lampConfig = LampDeviceConfig.deserialize(configStr)
            lampConfig?.let {
                Log.i(TAG, "Received updated config, updating UI: address=${it.address}")
                updateUI(it)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate called")
        enableEdgeToEdge()
        
        setContentView(R.layout.activity_device_config)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupColorPicker()

        val address = intent.getStringExtra("address") ?: return
        Log.i(TAG, "Requesting config for address=$address")
        val getConfigIntent = Intent(BleIntents.ACTION_GET_CONFIG).apply {
            putExtra("address", address)
        }
        getConfigIntent.setPackage(this.packageName)
        sendBroadcast(getConfigIntent)
        logBroadcastSent(TAG, getConfigIntent)

        safeRegisterReceiver(configReceiver, IntentFilter(BleIntents.ACTION_UPDATE_CONFIG))
        Log.i(TAG, "Broadcast receiver registered for config updates")

        val aliasEditIcon = findViewById<android.widget.ImageView>(R.id.alias_edit_icon)
        val aliasLabel = findViewById<TextView>(R.id.alias_label)
        val powerSwitch = findViewById<Switch>(R.id.power_switch)
        val modeGroup = findViewById<RadioGroup>(R.id.mode_radio_group)
        val colorFormatDisplay = findViewById<TextView>(R.id.color_format_display)
        val colorFormatHelp = findViewById<android.widget.ImageView>(R.id.color_format_help)

        aliasEditIcon.setOnClickListener {
            Log.i(TAG, "Alias edit icon clicked")
            val editText = android.widget.EditText(this).apply {
                setText(aliasLabel.text)
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("Edit Alias")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    val newAlias = editText.text.toString()
                    aliasLabel.text = newAlias
                    lampConfig?.let {
                        it.alias = newAlias
                        sendUpdateConfigBroadcast(it)
                        Log.i(TAG, "Alias updated: $newAlias")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        powerSwitch.setOnCheckedChangeListener { _, isChecked ->
            lampConfig?.let {
                if (it.enabled != isChecked) {
                    it.enabled = isChecked
                    sendUpdateConfigBroadcast(it)
                    Log.i(TAG, "Power switch toggled: enabled=$isChecked, address=${it.address}")
                }
            }
        }

        modeGroup.setOnCheckedChangeListener { group, checkedId ->
            val radioButton = group.findViewById<android.widget.RadioButton>(checkedId)
            if (radioButton == null || !radioButton.isPressed) {
                return@setOnCheckedChangeListener
            }
            lampConfig?.let {
                val newMode = when (checkedId) {
                    R.id.radio_specified -> DeviceMode.SPECIFIED
                    R.id.radio_random -> DeviceMode.RANDOM
                    R.id.radio_api -> DeviceMode.API
                    R.id.radio_sync -> DeviceMode.SYNC
                    else -> it.mode
                }
                if (it.mode != newMode) {
                    it.mode = newMode
                    sendUpdateConfigBroadcast(it)
                    Log.i(TAG, "Mode changed: $newMode, address=${it.address}")
                }
            }
        }

        colorFormatDisplay.setOnClickListener {
            Log.i(TAG, "Color format display clicked")
            val editText = android.widget.EditText(this).apply {
                setText(colorFormatDisplay.text)
                hint = "Enter RGBA format (e.g., RGBA, GRBA)"
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("Edit Color Format")
                .setView(editText)
                .setPositiveButton("OK") { _, _ ->
                    val newFormat = editText.text.toString().uppercase()
                    if (isValidColorFormat(newFormat)) {
                        colorFormatDisplay.text = newFormat
                        lampConfig?.let {
                            it.colorFormat = newFormat
                            sendUpdateConfigBroadcast(it)
                            Log.i(TAG, "Color format updated: $newFormat, address=${it.address}")
                        }
                    } else {
                        android.widget.Toast.makeText(this, "Invalid format. Please use only A, R, G, B letters.", android.widget.Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Invalid color format entered: $newFormat")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        colorFormatHelp.setOnClickListener {
            Log.i(TAG, "Color format help icon clicked")
            android.app.AlertDialog.Builder(this)
                .setTitle("Color Format Help")
                .setMessage("Due to different LED control component protocols, you may need to modify this color format for Bluetooth control commands.\n\n" +
                        "Common formats:\n" +
                        "• RGBA (Red, Green, Blue, Alpha) - Default\n" +
                        "• GRBA (Green, Red, Blue, Alpha) - If red appears inverted\n" +
                        "• AAAA (All Alpha) - For alpha-only control\n\n" +
                        "Tip: If you notice red colors appearing inverted in RGBA mode, try switching to GRBA format.\n\n" +
                        "Please ensure your format is exactly 4 letters using only A, R, G, B")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(configReceiver)
        Log.i(TAG, "onDestroy called, receiver unregistered")
    }

    private fun initializeViews() {
        colorPickerView = findViewById(R.id.colorPickerView)
        alphaSlideBar = findViewById(R.id.alphaSlideBar)
    }

    private fun setupColorPicker() {
        colorPickerView.attachAlphaSlider(alphaSlideBar)

        colorPickerView.setColorListener(object : ColorEnvelopeListener {
            override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                updateColorDisplay(envelope)
            }
        })

        colorPickerView.setInitialColor(Color.WHITE)
    }

    @SuppressLint("SetTextI18n")
    private fun updateColorDisplay(envelope: ColorEnvelope) {
        val color = envelope.color
        val hexCode = envelope.hexCode

        findViewById<TextView>(R.id.hexColorText).text = "#$hexCode"

        lampConfig?.let {
            if (it.color == color) {
                return@let
            }
            it.color = color
            sendUpdateColorBroadcast(it.address, color)
            Log.i(TAG, "Color updated: $color, address=${it.address}")
        }
    }

    private fun sendUpdateColorBroadcast(address: String, color: Int) {
        val intent = Intent(BleIntents.ACTION_UPDATE_COLOR).apply {
            putExtra("address", address)
            putExtra("color", color)
        }
        intent.setPackage(this.packageName)
        sendBroadcast(intent)
        logBroadcastSent(TAG, intent)
    }

    private fun updateUI(config: LampDeviceConfig) {
        findViewById<TextView>(R.id.alias_label).text = config.alias
        findViewById<TextView>(R.id.device_addr).text = config.address
        val modeGroup = findViewById<RadioGroup>(R.id.mode_radio_group)
        val modeId = when (config.mode) {
            DeviceMode.SPECIFIED -> R.id.radio_specified
            DeviceMode.RANDOM -> R.id.radio_random
            DeviceMode.API -> R.id.radio_api
            DeviceMode.SYNC -> R.id.radio_sync
        }
        modeGroup.check(modeId)
        findViewById<Switch>(R.id.power_switch).isChecked = config.enabled

        findViewById<TextView>(R.id.color_format_display).text = config.colorFormat

        colorPickerView.setInitialColor(config.color)
        findViewById<TextView>(R.id.hexColorText).text = String.format("#%08X", config.color)
        Log.d(TAG, "UI updated for address=${config.address}")
    }

    private fun sendUpdateConfigBroadcast(config: LampDeviceConfig) {
        val intent = Intent(BleIntents.ACTION_UPDATE_CONFIG).apply {
            putExtra("updated_config", config.serialize())
        }
        intent.setPackage(this.packageName)
        sendBroadcast(intent)
        logBroadcastSent(TAG, intent)
    }

    private fun isValidColorFormat(format: String): Boolean {
        if (format.length != 4) return false
        
        val validChars = setOf('A', 'R', 'G', 'B')
        return format.all { it in validChars }
    }
}

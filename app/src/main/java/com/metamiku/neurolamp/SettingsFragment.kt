package com.metamiku.neurolamp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.content.Context
import androidx.core.content.edit

class SettingsFragment : Fragment() {
    private val TAG = "SettingsFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate called")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(TAG, "onCreateView called")
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val defaultUrl = LampDeviceConfig.apiUrl
        val editText = view.findViewById<EditText>(R.id.edit_color_api_url)
        val savedUrl = prefs.getString("color_api_url", defaultUrl) ?: defaultUrl
        LampDeviceConfig.apiUrl = savedUrl

        editText.setText(savedUrl)

        editText.setSelection(editText.text.length)

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString() ?: defaultUrl
                if (url == prefs.getString("color_api_url", defaultUrl)) {
                    return
                }
                prefs.edit { putString("color_api_url", url) }
                LampDeviceConfig.apiUrl = url
            }
        })
        return view
    }
}
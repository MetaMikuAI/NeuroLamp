package com.metamiku.neurolamp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.widget.SwitchCompat
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

class HomeFragment : Fragment() {

    private lateinit var deviceCountText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: DeviceAdapter
    private val TAG = "HomeFragment"

    private val lampDeviceConfigList = mutableListOf<LampDeviceConfig>()

    private val lampConfigReceiver = object : BroadcastReceiver() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BleIntents.ACTION_ALL_LAMP_CONFIGS -> updateLampDeviceConfigs(intent)
                BleIntents.ACTION_LAMP_DISCONNECTED -> disableLampDisconnected(intent)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateLampDeviceConfigs(intent: Intent) {
        val rawList = intent.getStringArrayListExtra("all_lamp_configs")
        val devices = rawList?.mapNotNull { LampDeviceConfig.deserialize(it) } ?: emptyList()
        lampDeviceConfigList.clear()
        lampDeviceConfigList.addAll(devices)

        Log.i(TAG, "updateLampDeviceConfigs: received ${devices.size} device(s)")
        devices.forEachIndexed { index, device ->
            Log.d(TAG, "Device[$index]: alias=${device.alias}, address=${device.address}, enabled=${device.enabled}")
        }

        adapter.notifyDataSetChanged()
        updateDeviceCount()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun disableLampDisconnected(intent: Intent) {
        val address = intent.getStringExtra("address")
        if (address == null) {
            Log.w(TAG, "disableLampDisconnected: received null address")
            return
        }
        val device = lampDeviceConfigList.find { it.address == address }
        if (device != null && device.enabled) {
            device.enabled = false
            Log.i(TAG, "disableLampDisconnected: $address disconnected, marked as disabled")
            adapter.notifyDataSetChanged()
            updateDeviceCount()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.i(TAG, "onCreateView called")
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        deviceCountText = root.findViewById(R.id.tv_device_count)
        recyclerView = root.findViewById(R.id.recycler_devices)
        fab = root.findViewById(R.id.fab_add_device)

        adapter = DeviceAdapter(lampDeviceConfigList)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = adapter

        updateDeviceCount()

        fab.setOnClickListener {
            Log.i(TAG, "FAB clicked: launching SearchActivity")
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }

        val filter = IntentFilter().apply {
            addAction(BleIntents.ACTION_ALL_LAMP_CONFIGS)
            addAction(BleIntents.ACTION_LAMP_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            requireContext(),
            lampConfigReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "Broadcast receiver registered for lamp config updates")
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(lampConfigReceiver)
        Log.i(TAG, "onDestroyView: receiver unregistered")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: requesting all lamp configs")
        val intent = Intent(BleIntents.ACTION_GET_ALL_CONFIGS)
        intent.setPackage(requireActivity().packageName)
        requireContext().sendBroadcast(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun updateDeviceCount() {
        deviceCountText.text = "${lampDeviceConfigList.size} device(s) connected"
    }

    class DeviceAdapter(private val devices: List<LampDeviceConfig>) :
        RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

        companion object {
            private const val TAG = "HomeFragment.DeviceAdapter"
        }

        class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.device_name)
            val imageView: ImageView = view.findViewById(R.id.device_image)
            val switch: SwitchCompat = view.findViewById(R.id.device_switch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device_card, parent, false)
            return DeviceViewHolder(view)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val device = devices[position]

            holder.nameText.text = device.alias
            Log.d(TAG, "onBindViewHolder: alias=${device.alias}, address=${device.address}, color=${device.color}, enabled=${device.enabled}")

            if (device.enabled) {
                holder.imageView.clearColorFilter()
            } else {
                holder.imageView.setColorFilter(android.graphics.Color.GRAY, android.graphics.PorterDuff.Mode.SRC_IN)
            }

            holder.switch.isChecked = device.enabled

            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                device.enabled = isChecked
                Log.i(TAG, "Switch toggled: address=${device.address}, enabled=${device.enabled}")
                val toggleIntent = Intent(BleIntents.ACTION_TOGGLE_LAMP)
                toggleIntent.putExtra("address", device.address)
                toggleIntent.putExtra("enabled", device.enabled)
                toggleIntent.setPackage(holder.itemView.context.packageName)
                holder.itemView.context.sendBroadcast(toggleIntent)
                holder.itemView.post {
                    notifyDataSetChanged()
                }
            }

            val cardRoot = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.device_card_root)
            cardRoot.setOnLongClickListener {
                Log.i(TAG, "Device card long-pressed: address=${device.address}")
                val context = holder.itemView.context
                val intent = Intent(context, LampDeviceConfigActivity::class.java)
                intent.putExtra("address", device.address)
                context.startActivity(intent)
                true
            }
        }

        override fun getItemCount() = devices.size
    }
}

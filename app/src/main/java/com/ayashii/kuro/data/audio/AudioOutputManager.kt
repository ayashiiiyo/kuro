package com.ayashii.kuro.data.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OutputType {
    WIRED, BLUETOOTH, SPEAKER
}

data class AudioDeviceState(
    val title: String,
    val iconType: OutputType
)

class AudioOutputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _deviceState = MutableStateFlow(AudioDeviceState("Phone Speaker", OutputType.SPEAKER))
    val deviceState: StateFlow<AudioDeviceState> = _deviceState.asStateFlow()

    private val audioCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateState()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateState()
            }
        }
    } else null

    private val plugReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            updateState()
        }
    }

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioCallback != null) {
                audioManager.registerAudioDeviceCallback(audioCallback, null)
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(plugReceiver, filter)
        } catch (_: Exception) {}
        updateState()
    }

    fun updateState() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                // 1. Check Bluetooth output first
                val btDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
                if (btDevice != null) {
                    val rawName = btDevice.productName?.toString()?.trim()
                    val devName = if (!rawName.isNullOrBlank()) rawName else "Headset"
                    _deviceState.value = AudioDeviceState("Bluetooth: $devName", OutputType.BLUETOOTH)
                    return
                }

                // 2. Check Wired Headphones / Headset (Jack 3.5mm, USB-C)
                val wiredDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
                if (wiredDevice != null) {
                    _deviceState.value = AudioDeviceState("Wired Connection", OutputType.WIRED)
                    return
                }
            }

            // 3. Fallback to built-in speaker
            _deviceState.value = AudioDeviceState("Phone Speaker", OutputType.SPEAKER)
        } catch (_: Exception) {
            _deviceState.value = AudioDeviceState("Phone Speaker", OutputType.SPEAKER)
        }
    }

    fun openAudioSettings() {
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(btIntent)
            } catch (_: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioOutputManager? = null

        fun getInstance(context: Context): AudioOutputManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioOutputManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

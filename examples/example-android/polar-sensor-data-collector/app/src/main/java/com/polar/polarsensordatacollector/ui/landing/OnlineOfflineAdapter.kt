package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.polar.polarsensordatacollector.ui.activity.ActivityRecordingFragment
import com.polar.polarsensordatacollector.ui.devicesettings.DeviceSettingsFragment
import com.polar.polarsensordatacollector.ui.logging.LoggingFragment

const val ONLINE_OFFLINE_KEY_DEVICE_ID = "com.polar.polarsensordatacollector.ONLINE_OFFLINE_KEY_DEVICE_ID"
private const val TAG = "OnlineOfflineAdapter"

class OnlineOfflineAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    var items: MutableList<Pair<String, Fragment>> = mutableListOf()
        private set

    override fun getItemCount(): Int = items.size

    override fun createFragment(position: Int): Fragment {
        return try {
            items[position].second
        } catch (e: Exception) {
            throw Exception("Unknown fragment")
        }
    }

    override fun containsItem(itemId: Long): Boolean {
        return items.any { it.second.hashCode().toLong() == itemId }
    }

    override fun getItemId(position: Int): Long {
        return items[position].second.hashCode().toLong()
    }

    fun addOfflineRecordingFragment(deviceId: String) {
        if (!items.any { it.second is OfflineRecFragment }) {
            Log.d(TAG, "Add OfflineRecordingFragment")
            val fragment = OfflineRecFragment().apply {
                arguments = Bundle().apply {
                    putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
                }
            }
            val insertIndex = 1.coerceAtMost(items.size)
            items.add(insertIndex, "OFFLINE" to fragment)
            notifyItemInserted(insertIndex)
        } else {
            Log.w(TAG, "trying to add OfflineRecordingFragment but found already")
        }
    }

    fun addOnlineRecordingFragment(deviceId: String) {
        if (!items.any { it.second is OnlineRecFragment }) {
            Log.d(TAG, "Add OnlineRecordingFragment")
            val fragment = OnlineRecFragment().apply {
                arguments = Bundle().apply {
                    putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
                }
            }
            items.add(0, "ONLINE" to fragment)
            notifyItemInserted(0)
        } else {
            Log.w(TAG, "trying to add OnlineRecordingFragment but found already")
        }
    }

    fun addDeviceSettingsFragment(deviceId: String) {
        if (!items.any { it.second is DeviceSettingsFragment }) {
            Log.d(TAG, "Add DeviceSettingsFragment")
            val fragment = DeviceSettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
                }
            }
            items.add(Pair("SETTINGS", fragment))
            this.notifyItemInserted(items.size - 1)
        } else {
            Log.w(TAG, "trying to add DeviceSettingsFragment but found already")
        }
    }

    fun addLoggingFragment(deviceId: String) {
        if (!items.any { it.second is LoggingFragment }) {
            Log.d(TAG, "Add LoggingFragment")
            val fragment = LoggingFragment()
            fragment.arguments = Bundle().apply {
                putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
            }
            items.add(Pair("LOGGING", fragment))
            this.notifyItemInserted(items.size - 1)
        } else {
            Log.w(TAG, "trying to add LoggingFragment but found already")
        }
    }

    fun addActivityFragment(deviceId: String) {
        if (!items.any { it.second is ActivityRecordingFragment }) {
            Log.d(TAG, "Add ActivityFragment")
            val fragment = ActivityRecordingFragment()
            fragment.arguments = Bundle().apply {
                putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
            }
            items.add(Pair("LOAD", fragment))
            this.notifyItemInserted(items.size - 1)
        } else {
            Log.w(TAG, "trying to add ActivityFragment but found already")
        }
    }

    private fun removeOfflineRecordingFragment() {
        val index = items.indexOfFirst { it.second is OfflineRecFragment }
        if (index > -1) {
            items.removeAt(index)
            this.notifyItemRemoved(index)
        }
    }

    private fun removeOnlineRecordingFragment() {
        val index = items.indexOfFirst { it.second is OnlineRecFragment }
        if (index > -1) {
            items.removeAt(index)
            this.notifyItemRemoved(index)
        }
    }

    private fun removeDeviceSettingsFragment() {
        val index = items.indexOfFirst { it.second is DeviceSettingsFragment }
        if (index > -1) {
            items.removeAt(index)
            this.notifyItemRemoved(index)
        }
    }

    private fun removeLoggingFragment() {
        val index = items.indexOfFirst { it.second is LoggingFragment }
        if (index > -1) {
            items.removeAt(index)
            this.notifyItemRemoved(index)
        }
    }

    private fun removeActivityFragment() {
        val index = items.indexOfFirst { it.second is ActivityRecordingFragment }
        if (index > -1) {
            items.removeAt(index)
            this.notifyItemRemoved(index)
        }
    }

    fun removeFragments(isAlreadyConnected: Boolean = false) {
        Log.d(TAG, "removeFragments(), isAlreadyConnected: $isAlreadyConnected")
        removeOfflineRecordingFragment()
        removeOnlineRecordingFragment()
        if (!isAlreadyConnected) {
            removeDeviceSettingsFragment()
        }
        removeLoggingFragment()
        removeActivityFragment()
    }
}

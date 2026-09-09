package com.polar.polarsensordatacollector.ui.landing

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.polar.polarsensordatacollector.ui.activity.ActivityRecordingFragment
import com.polar.polarsensordatacollector.ui.exercisev2.ExerciseV2Fragment
import com.polar.polarsensordatacollector.ui.devicesettings.DeviceSettingsFragment
import com.polar.polarsensordatacollector.ui.h10exercise.H10ExerciseFragment
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

    fun hasExerciseV2Fragment(): Boolean = items.any { it.second is ExerciseV2Fragment }

    fun addOfflineRecordingFragment(deviceId: String) {
        if (items.any { it.second is OfflineRecFragment }) {
            Log.w(TAG, "trying to add OfflineRecordingFragment but found already")
            return
        }
        Log.d(TAG, "Add OfflineRecordingFragment for $deviceId")
        val fragment = OfflineRecFragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        // Insert at position 1 (after ONLINE tab) so ONLINE always comes first.
        val insertIndex = if (items.isNotEmpty()) 1 else 0
        items.add(insertIndex, "OFFLINE" to fragment)
        notifyItemInserted(insertIndex)
    }

    fun addOnlineRecordingFragment(deviceId: String) {
        if (items.any { it.second is OnlineRecFragment }) {
            Log.w(TAG, "trying to add OnlineRecordingFragment but found already")
            return
        }
        Log.d(TAG, "Add OnlineRecordingFragment for $deviceId")
        val fragment = OnlineRecFragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        items.add(0, "ONLINE" to fragment)
        notifyItemInserted(0)
    }

    fun addDeviceSettingsFragment(deviceId: String) {
        if (items.any { it.second is DeviceSettingsFragment }) {
            Log.w(TAG, "trying to add DeviceSettingsFragment but found already")
            return
        }
        Log.d(TAG, "Add DeviceSettingsFragment for $deviceId")
        val fragment = DeviceSettingsFragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        items.add("SETTINGS" to fragment)
        notifyItemInserted(items.size - 1)
    }

    fun addLoggingFragment(deviceId: String) {
        if (items.any { it.second is LoggingFragment }) {
            Log.w(TAG, "trying to add LoggingFragment but found already")
            return
        }
        Log.d(TAG, "Add LoggingFragment for $deviceId")
        val fragment = LoggingFragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        items.add("LOGGING" to fragment)
        notifyItemInserted(items.size - 1)
    }

    fun addActivityFragment(deviceId: String) {
        if (items.any { it.second is ActivityRecordingFragment }) {
            Log.w(TAG, "trying to add ActivityFragment but found already")
            return
        }
        Log.d(TAG, "Add ActivityFragment for $deviceId")
        val fragment = ActivityRecordingFragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        items.add("LOAD" to fragment)
        notifyItemInserted(items.size - 1)
    }

    fun addH10ExerciseFragment(deviceId: String) {
        if (items.any { it.second is H10ExerciseFragment }) {
            Log.w(TAG, "trying to add H10ExerciseFragment but found already")
            return
        }
        Log.d(TAG, "Add H10ExerciseFragment for $deviceId")
        val fragment = H10ExerciseFragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        items.add("H10 EXERCISE" to fragment)
        notifyItemInserted(items.size - 1)
    }

    fun addExerciseV2Fragment(deviceId: String) {
        if (items.any { it.second is ExerciseV2Fragment }) {
            Log.w(TAG, "trying to add ExerciseV2Fragment but found already")
            return
        }
        Log.d(TAG, "Add ExerciseV2Fragment for $deviceId")
        val fragment = ExerciseV2Fragment().apply {
            arguments = Bundle().apply { putString(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId) }
        }
        items.add("EXERCISE" to fragment)
        notifyItemInserted(items.size - 1)
    }

    /** Called when DeviceSettingsFragment signals a device operation (restart, factory reset, etc.) */
    fun removeFragments() {
        Log.d(TAG, "removeFragments()")
        items.clear()
        notifyDataSetChanged()
    }
}

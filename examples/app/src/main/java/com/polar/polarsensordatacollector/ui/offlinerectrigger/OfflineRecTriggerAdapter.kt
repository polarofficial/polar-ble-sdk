package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

internal const val OFFLINE_REC_TRIG_KEY_DEVICE_ID = "com.polar.polarsensordatacollector.ui.offlinerectrigger.OFFLINE_REC_TRIG_KEY_DEVICE_ID"
private const val TAG = "OfflineRecTriggerAdapter"

internal class OfflineRecTriggerAdapter(fragment: Fragment, deviceId: String) : FragmentStateAdapter(fragment) {
    var items: MutableList<Pair<String, Fragment>> = mutableListOf()
        private set

    init {
        addOfflineRecTriggerStatusFragment(deviceId)
        addOfflineRecTriggerSettingsFragment(deviceId)
    }

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return try {
            items[position].second
        } catch (e: Exception) {
            throw Exception("Unknown fragment")
        }
    }

    private fun addOfflineRecTriggerStatusFragment(deviceId: String) {
        if (!items.any { it.second is OfflineRecTriggerStatusFragment }) {
            Log.d(TAG, "Add OfflineRecTriggerStatusFragment")
            val fragment = OfflineRecTriggerStatusFragment()
            fragment.arguments = Bundle().apply {
                putString(OFFLINE_REC_TRIG_KEY_DEVICE_ID, deviceId)
            }
            items.add(0, Pair("STATUS", fragment))
            this.notifyItemInserted(items.size - 1)
        } else {
            Log.w(TAG, "trying to add OfflineRecTriggerStatusFragment but found already")
        }
    }

    private fun addOfflineRecTriggerSettingsFragment(deviceId: String) {
        if (!items.any { it.second is OfflineRecTriggerSettingsFragment }) {
            Log.d(TAG, "Add OfflineRecTriggerSettingsFragment")
            val fragment = OfflineRecTriggerSettingsFragment()
            fragment.arguments = Bundle().apply {
                putString(OFFLINE_REC_TRIG_KEY_DEVICE_ID, deviceId)
            }
            items.add(1, Pair("SETUP", fragment))
            this.notifyItemInserted(items.size - 1)
        } else {
            Log.w(TAG, "trying to add OfflineRecTriggerSettingsFragment but found already")
        }
    }
}
package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.polar.polarsensordatacollector.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val TAG = "OfflineRecTriggerFragment"

@AndroidEntryPoint
class OfflineRecTriggerFragment : Fragment(R.layout.fragment_offline_rec_trigger) {
    private val offlineTriggerViewModel: OfflineTriggerViewModel by viewModels()
    private val args: OfflineRecTriggerFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val deviceId = args.deviceIdRecTriggerArgument
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                offlineTriggerViewModel.uiConnectionState.collect {
                    if (!it.isConnected) {
                        val navigateActionToHome = OfflineRecTriggerFragmentDirections.offlineRecordingTriggerToHome()
                        findNavController().navigate(navigateActionToHome)
                    }
                }
            }
        }

        val viewPager: ViewPager2 = view.findViewById(R.id.pager_offline_trigger)
        val offlineRecTriggerAdapter = OfflineRecTriggerAdapter(this, deviceId)
        viewPager.adapter = offlineRecTriggerAdapter

        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout_offline_trigger)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = offlineRecTriggerAdapter.items[position].first
        }.attach()
    }
}


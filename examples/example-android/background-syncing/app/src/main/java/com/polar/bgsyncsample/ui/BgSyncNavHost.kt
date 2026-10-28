// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.ui

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.polar.bgsyncsample.ui.graph.LogGraphView

@Composable
fun BgSyncNavHost() {
    val navController = rememberNavController()
    val viewModel: SyncViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "sync_dashboard") {
        composable("sync_dashboard") {
            SyncDashboardScreen(
                viewModel = viewModel,
                onNavigateToDataBrowser = {
                    navController.navigate("data_browser")
                },
                onNavigateToGraph = { deviceId, logLines ->
                    val graphData = com.polar.bgsyncsample.ui.graph.LogGraphData.parse(deviceId, logLines)
                    navController.navigate("log_graph/$deviceId")
                    // Store in viewModel for retrieval
                    viewModel.setCurrentGraphData(graphData)
                }
            )
        }
        composable("data_browser") {
            DataBrowserScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack(route = "sync_dashboard", inclusive = false)
                }
            )
        }
        composable("log_graph/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val graphData = viewModel.getCurrentGraphData()
            if (graphData != null && graphData.deviceId == deviceId) {
                LogGraphView(
                    graphData = graphData,
                    allDevices = viewModel.getAllDeviceIds(),
                    onBackClick = {
                        navController.popBackStack(route = "sync_dashboard", inclusive = false)
                    },
                    loadGraphData = { newDeviceId ->
                        com.polar.bgsyncsample.ui.graph.LogGraphData.parse(newDeviceId, viewModel.getRawLogMessages())
                    }
                )
            }
        }
    }
}

package com.polar.polarsensordatacollector.ui.landing

import android.Manifest
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import com.polar.polarsensordatacollector.R
import dagger.hilt.android.AndroidEntryPoint
import androidx.navigation.navOptions

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val topAppBar = findViewById<Toolbar>(R.id.top_tool_bar)
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        topAppBar.setupWithNavController(navController, appBarConfiguration)
        topAppBar.setOnMenuItemClickListener { item ->
            item.onNavDestinationSelected(navController) || super.onOptionsItemSelected(item)
        }

        topAppBar.setNavigationOnClickListener {
            val current = navController.currentDestination?.id
            if (
                current == R.id.offlineRecTriggerFragment ||
                current == R.id.settings_dest ||
                current == R.id.about_dest
            ) {
                navController.navigate(
                    R.id.mainFragment,
                    null,
                    navOptions {
                        popUpTo(R.id.mainFragment) { inclusive = true }
                        launchSingleTop = true
                    }
                )
            } else {
                navController.navigateUp()
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val dest: String = try {
                resources.getResourceName(destination.id)
            } catch (e: Resources.NotFoundException) {
                destination.id.toString()
            }

            // Set the settings invisible in other than main destination
            topAppBar.menu.findItem(R.id.settings_dest).isVisible = destination.id == R.id.mainFragment
            topAppBar.menu.findItem(R.id.about_dest).isVisible = destination.id == R.id.mainFragment

            // Debugging of navigation
            //Toast.makeText(this@NewMainActivity, "Navigated to $dest", Toast.LENGTH_SHORT).show()
            Log.d("Navigation", "Navigated to $dest")
            //navController.backQueue.mapNotNull { dest ->
            //    Log.d("Navigation", "Current back stack $dest")
            //}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), PERMISSION_REQUEST_CODE)
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            }
        }

        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Log.d(TAG, "bt ready")
        }
    }
}


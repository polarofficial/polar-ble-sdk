package com.polar.polarsensordatacollector.ui.appsettings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.polar.polarsensordatacollector.ui.theme.PolarsensordatacollectorTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class AboutFragment : Fragment() {
    private val viewModel: AboutViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                PolarsensordatacollectorTheme {
                    Surface {
                        AboutComposable(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutComposable(viewModel: AboutViewModel = viewModel()) {
    val appVersion by viewModel.uiAppVersion.collectAsState()
    val sdkVersion by viewModel.uiPolarBleSdkVersion.collectAsState()
    AboutScreen(appVersion, sdkVersion)
}

@Composable
private fun AboutScreen(appVersion: String, sdkVersion: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        InformationItem(label = "PSDC version", appVersion)
        Spacer(modifier = Modifier.height(16.dp))
        InformationItem(label = "Using Polar BLE SDK", sdkVersion)
    }
}

@Composable
fun InformationItem(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Preview("AboutScreen")
@Composable
private fun AboutScreenPreview() {
    PolarsensordatacollectorTheme(content = {
        Surface {
            AboutScreen("6.0.0", "5.0.0-Beta2")
        }
    })
}
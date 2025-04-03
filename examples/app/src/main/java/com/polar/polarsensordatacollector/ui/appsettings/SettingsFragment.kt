package com.polar.polarsensordatacollector.ui.appsettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.polar.polarsensordatacollector.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val backUpLocation: Preference? = findPreference(getString(R.string.back_up_location))
        val backUpEnabled: SwitchPreferenceCompat? = findPreference(getString(R.string.back_up_enabled))

        backUpLocation?.let { preference ->
            val uri = preference.sharedPreferences?.getString(preference.key, "") ?: ""
            activity?.applicationContext?.let { ctx ->
                if (uri.isNotEmpty() && isUriPointingToValidDirectory(ctx, Uri.parse(uri))) {
                    preference.summary = mapUriToDisplayName(ctx, Uri.parse(uri))
                } else {
                    backUpEnabled?.isChecked = false
                }
            }
            preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startDirectorySelectionDialog()
                true
            }
        }

        backUpEnabled?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                backUpLocation?.let { preference ->
                    val uri = preference.sharedPreferences?.getString(preference.key, "") ?: ""
                    activity?.applicationContext?.let { ctx ->
                        if (uri.isEmpty() || !isUriPointingToValidDirectory(ctx, Uri.parse(uri))) {
                            startDirectorySelectionDialog()
                        }
                    }
                }
            }
            true
        }
        val exportLogsPreference: Preference? = findPreference("export_logs")
        exportLogsPreference?.setOnPreferenceClickListener {
            dumpLogcatToFile()
            true
        }
    }

    private fun startDirectorySelectionDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.DIRECTORY_DOCUMENTS)
        }
        createFileActivityResultLauncher.launch(intent)
    }

    private var createFileActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                activity?.applicationContext?.let { ctx ->

                    // grant permanent access to phone folder
                    val contentResolver = ctx.contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver?.takePersistableUriPermission(uri, takeFlags)

                    // update preference summary on UI
                    val extStoragePreference: Preference? = findPreference(getString(R.string.back_up_location))
                    extStoragePreference?.let {
                        it.summary = mapUriToDisplayName(ctx, uri) ?: resources.getString(R.string.ext_storage_prefs_summary_not_set)
                    }

                    // update the shared preference value
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx)
                    sharedPreferences?.let {
                        with(it.edit()) {
                            putString(getString(R.string.back_up_location), uri.toString())
                            apply()
                        }
                    }
                }
            }
        }
    }

    private fun mapUriToDisplayName(ctx: Context, uri: Uri): String? {
        return DocumentFile.fromTreeUri(ctx, uri)?.name
    }

    private fun isUriPointingToValidDirectory(ctx: Context, uri: Uri): Boolean {
        return DocumentFile.fromTreeUri(ctx, uri)?.isDirectory ?: false
    }

    private fun dumpLogcatToFile() {
        val context = activity?.applicationContext ?: return
        val logsFile = File(context.getExternalFilesDir(null), "logcat.txt")

        try {
            val process = ProcessBuilder()
                    .command("logcat", "-d")
                    .redirectErrorStream(true)
                    .start()

            val inputStream = process.inputStream
            val outputStream = FileOutputStream(logsFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            shareLogs(logsFile)
        } catch (e: IOException) {
            Toast.makeText(context, "Failed to dump logcat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLogs(logsFile: File) {
        val context = activity?.applicationContext ?: return

        if (logsFile.exists()) {
            val uri = FileProvider.getUriForFile(
                    context,
                    "com.polar.polarsensordatacollector.fileprovider",
                    logsFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_share_subject))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.log_share_text))
            }
            startActivity(Intent.createChooser(intent, context.getString(R.string.share_logs_via)))
        } else {
            Toast.makeText(context, "Log file not found", Toast.LENGTH_SHORT).show()
        }
    }
}
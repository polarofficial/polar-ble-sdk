package com.polar.polarsensordatacollector.ui.utils

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.polarsensordatacollector.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

@AndroidEntryPoint
class DataViewer: AppCompatActivity() {

    private lateinit var dataText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            setContentView(R.layout.fragment_data_text_view)
            initViews()
            val filesDirPath = intent.getStringExtra("DATA_FILES_DIR_PATH")
            if (filesDirPath != null) {
                val uri = intent.getStringExtra("DATA_URI" )?.toUri()
                try {
                    val file = getFile(uri, filesDirPath)
                    dataText.text = "${file.name}\n" + file.inputStream().readBytes().toString(Charsets.UTF_8)
                } catch (e: Exception) {
                    showToast("${intent.getStringExtra("TOAST_TEXT")}: $filesDirPath")
                }
            }
        }
    }

    private fun initViews() {
        dataText = findViewById(R.id.data_contents)
    }

    @Throws(IOException::class)
    fun getFile(uri: Uri?, filesDirPath: String): File {
        val destinationFilename =
            File(filesDirPath + File.separatorChar + uri?.let { queryName(it) })
        try {
            contentResolver.openInputStream(uri!!).use { inputStream ->
                if (inputStream != null) {
                    createFileFromStream(inputStream, destinationFilename)
                }
            }
        } catch (e: Exception) {
            BleLogger.e("Failed to load data file", e.message.toString())
        }
        return destinationFilename
    }

    fun createFileFromStream(inputStream: InputStream, destination: File?) {
        try {
            FileOutputStream(destination).use { fos ->
                val buffer = ByteArray(4096)
                var length: Int
                while ((inputStream.read(buffer).also { length = it }) > 0) {
                    fos.write(buffer, 0, length)
                }
                fos.flush()
            }
        } catch (e: Exception) {
            BleLogger.e("Failed to create data file", e.message.toString())
        }
    }

    private fun queryName(uri: Uri): String {
        val query = checkNotNull(
            contentResolver.query(uri, null, null, null, null)
        )
        val nameIndex = query.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        query.moveToFirst()
        val name = query.getString(nameIndex)
        query.close()

        return name
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
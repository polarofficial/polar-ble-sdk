package com.polar.polarsensordatacollector.ui.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.apache.commons.io.FileUtils
import java.io.File

class FileUtils(private val context: Context) {

    fun saveToFile(fileData: ByteArray, fileName: String): Uri {
        val file = File(context.filesDir, fileName)
        FileUtils.writeByteArrayToFile(file, fileData)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", file
        )
    }
}
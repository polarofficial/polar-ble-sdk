package com.polar.polarsensordatacollector.ui.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.polar.polarsensordatacollector.R
import com.polar.sdk.api.model.Errorlog
import org.apache.commons.io.FileUtils
import java.io.File

object ErrorLogUtils {

    private const val TAG = "FileUtils"

    fun saveErrorLogToFile(context: Context, errorLogData: ByteArray) {
        val errorLogFile = File(context.filesDir, Errorlog.ERRORLOG_FILENAME)
        FileUtils.writeByteArrayToFile(errorLogFile, errorLogData)
    }

    fun shareErrorLogWithMail(context: Context) {
        try {
            val errorLogFile = File(context.filesDir, Errorlog.ERRORLOG_FILENAME)

            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.errorlog_exported_mail_subject)
            )
            intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.errorlog_exported_mail_text))
            intent.putExtra(Intent.EXTRA_EMAIL, "")
            val attachmentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", errorLogFile
            )
            intent.putExtra(Intent.EXTRA_STREAM, attachmentUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.choose_email_app)
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to share errorlog: $e", e)
            Toast.makeText(
                context,
                "Failed to share errorlog: $e",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
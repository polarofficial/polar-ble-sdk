package com.polar.polarsensordatacollector.ui.genericapi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.repository.PolarDeviceRepository
import com.polar.polarsensordatacollector.repository.ResultOfRequest
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.polarsensordatacollector.ui.utils.FileUtils
import com.polar.polarsensordatacollector.ui.utils.MessageUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class GenericDataUiState {
    class Failure(
        val message: String,
        val throwable: Throwable?
    ) : GenericDataUiState()
}

class GenericApiViewModel(
    context: Context,
    polarDeviceRepository: PolarDeviceRepository,
    fileUtils: FileUtils,
    identifier: String
) : ViewModel() {

    private val polarDeviceRepository = polarDeviceRepository
    private val fileUtils = fileUtils
    private val identifier = identifier
    private val context = context

    private val _uiShowError = MutableSharedFlow<MessageUiState>(extraBufferCapacity = 1)
    val uiShowError: SharedFlow<MessageUiState> = _uiShowError.asSharedFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun writeFile(filePath: String, fileData: ByteArray) = runBlocking {
        when (polarDeviceRepository.writeFile(identifier, filePath, fileData)) {
            is ResultOfRequest.Success -> {
                Toast.makeText(context, "Successfully written file at path '$filePath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
            is ResultOfRequest.Failure -> {
                Toast.makeText(context, "Failed to write file at path '$filePath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun readFile(filePath: String) = runBlocking {
        when (val result = polarDeviceRepository.readFile(identifier, filePath)) {
            is ResultOfRequest.Success -> {
                result.value?.let {
                    val fileUri = fileUtils.saveToFile(
                        result.value,
                        "GENERIC_API/${filePath.split("/").last()}"
                    )
                    openDataTextView(context,  fileUri)
                }
            }
            is ResultOfRequest.Failure -> {
                Toast.makeText(context, "Failed to read file '$filePath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun listFiles(filePath: String, deleteDeep: Boolean) = runBlocking {
        when (val result = polarDeviceRepository.listFiles(identifier, filePath, deleteDeep)) {
            is ResultOfRequest.Success -> {
                result.value?.let {
                    var listItems = ""
                    for (item in result.value) {
                        listItems = listItems.plus(item).plus("\n")
                    }
                    val fileUri = fileUtils.saveToFile(
                        listItems.toByteArray(),
                        "GENERIC_API/FILES_LIST.txt"
                    )
                    openDataTextView(context,  fileUri)
                }
            }
            is ResultOfRequest.Failure -> {
                Toast.makeText(context, "Failed to list files at path '$filePath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteFile(filePath: String) = runBlocking {
        when (polarDeviceRepository.deleteFile(identifier, filePath)) {
            is ResultOfRequest.Success -> {
                Toast.makeText(context, "Successfully deleted file '$filePath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
            is ResultOfRequest.Failure -> {
                Toast.makeText(context, "Failed to delete file at path '$filePath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun createFolder(folderPath: String) = runBlocking {
        when (polarDeviceRepository.createFolder(identifier, folderPath)) {
            is ResultOfRequest.Success -> {
                Toast.makeText(context, "Successfully created folder '$folderPath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
            is ResultOfRequest.Failure -> {
                Toast.makeText(context, "Failed to create folder '$folderPath' on device $identifier", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDataTextView(context: Context, uri: Uri) {
        val intent = Intent(context, DataViewer::class.java)
        intent.putExtra("DATA_FILES_DIR_PATH", context.filesDir.path)
        intent.putExtra("DATA_URI", uri.toString())
        intent.putExtra("TOAST_TEXT", context.getString(R.string.toast_data_viewer_failed))
        context.startActivity(intent)
    }
}

class GenericApiViewModelFactory(
    private val polarDeviceRepository: PolarDeviceRepository,
    private val fileUtils: FileUtils,
    private val context: Context,
    private val identifier: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GenericApiViewModel(context, polarDeviceRepository, fileUtils, identifier) as T
    }
}
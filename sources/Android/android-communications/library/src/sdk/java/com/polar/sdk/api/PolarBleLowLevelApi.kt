package com.polar.sdk.api

interface PolarBleLowLevelApi {

    /**
     * Read any file over PFtp BLE client. API user must know the exact path to the desired file.
     * API user must also take care of parsing the returned ByteArray payload to the desired data object.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @param identifier Polar device ID or BT address
     * @param filePath Path to the desired file in a Polar device.
     * @return ByteArray with payload data, payload may also be empty ByeArray.
     */
    @OptIn
    suspend fun readFile(
        identifier: String,
        filePath: String
    ): ByteArray?

    /**
     * Write any file over PFtp BLE client. API user must know the exact path to the desired file.
     * API user must also take care of parsing the returned ByteArray payload to the desired data object.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @param identifier Polar device ID or BT address
     * @param filePath Path to the directory in device  in a Polar device.
     * @param fileData, file data in already serialized into ByteArray format.
     * @return Success or error
     */
    @OptIn
    suspend fun writeFile(
        identifier: String,
        filePath: String,
        fileData: ByteArray
    )

    /**
     * Delete any file or directory over PFtp BLE client. API user must know the exact path to the desired file.
     * API user must also take care of parsing the returned ByteArray payload to the desired data object.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @param identifier Polar device ID or BT address
     * @param filePath Path of the file or directory to be deleted at a Polar device.
     * @return Success or error
     */
    @OptIn
    suspend fun deleteFileOrDirectory(
        identifier: String,
        filePath: String
    )

    /**
     * List all files in the given path
     *
     * @param identifier Polar device ID or BT address
     * @param directoryPath Path to the desired directory in a Polar device from which to list all files.
     * @param recurseDeep Recursion goes to the bottom of the file tree when true.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @return List of files or error
     */
    @OptIn
    suspend fun getFileList(
        identifier: String,
        filePath: String,
        recurseDeep: Boolean
    ): List<String>
}
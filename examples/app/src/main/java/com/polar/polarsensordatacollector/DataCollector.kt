package com.polar.polarsensordatacollector

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.polar.sdk.api.model.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class DataCollector(private val context: Context) {
    companion object {
        private const val TAG = "DataCollector"
        private const val LOGS_DIRECTORY = "/sensorDataLogs/"
    }

    interface FileOperations {
        val fileName: String
        fun isStarted(): Boolean
        fun write(line: String)
        fun close()
        fun getUri(): Uri
        fun delete(): Boolean
        fun length(): Long
    }

    internal class DocumentFileOperationWrapper(context: Context, fileName: String, uriToPhoneStorage: Uri) : FileOperations {
        private val outputStream: OutputStream
        private val documentFile: DocumentFile
        private val uriToFile: Uri
        private var isWriteOngoing = false

        init {
            val baseTreeFolder: DocumentFile = requireNotNull(DocumentFile.fromTreeUri(context, uriToPhoneStorage))
            documentFile = requireNotNull(baseTreeFolder.createFile("plain/text", fileName))
            uriToFile = documentFile.uri
            outputStream = requireNotNull(context.contentResolver.openOutputStream(uriToFile))
        }

        override val fileName: String
            get() = documentFile.name.toString()

        override fun isStarted(): Boolean {
            return isWriteOngoing
        }

        override fun write(line: String) {
            isWriteOngoing = true
            outputStream.write(line.toByteArray())
        }

        override fun close() {
            outputStream.close()
            isWriteOngoing = false
        }

        override fun getUri(): Uri {
            return uriToFile
        }

        override fun delete(): Boolean {
            return documentFile.delete()
        }

        override fun length(): Long {
            return documentFile.length()
        }
    }

    internal class FileOperationWrapper(private val context: Context, override val fileName: String, tag: String) : FileOperations {
        private val outputStream: FileOutputStream
        private val file: File
        private var isWriteOngoing = false

        init {
            val directoryName = makeParentDir(tag)
            file = File(context.getExternalFilesDir(null).toString() + directoryName + fileName)
            file.createNewFile()
            outputStream = FileOutputStream(file)
        }

        override fun isStarted(): Boolean {
            return isWriteOngoing
        }

        private fun makeParentDir(tag: String): String {
            val directoryName = "${LOGS_DIRECTORY}$tag/"
            val dir = File(context.getExternalFilesDir(null), directoryName)
            if (dir.exists()) {
                if (dir.isDirectory) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            } else {
                val result = dir.mkdirs()
                if (!result) {
                    throw FileSystemException(file = dir, reason = "Couldn't create directory $directoryName")
                }
            }
            return directoryName
        }

        override fun write(line: String) {
            isWriteOngoing = true
            outputStream.write(line.toByteArray())
        }

        override fun close() {
            outputStream.close()
            isWriteOngoing = false
        }

        override fun getUri(): Uri {
            return FileProvider.getUriForFile(context, "com.polar.polarsensordatacollector.fileprovider", file)
        }

        override fun delete(): Boolean {
            return file.delete()
        }

        override fun length(): Long {
            return file.length()
        }
    }

    private val logStreams: MutableMap<StreamType, FileOperations> = EnumMap(StreamType::class.java)
    private var latestTimeStamp: Long = 0
    private var ppgTimeStamp: Long = 0
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private fun startNewLogWithTag(tag: String, logId: String, startTime: Calendar): FileOperations {
        val timeTag = dateFormat.format(startTime.time)
        val fileName = logId + "_" + timeTag + "_" + tag + ".txt"
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val isBackUpEnabled = sharedPreferences.getBoolean(context.getString(R.string.back_up_enabled), false)
        val backUpUri = sharedPreferences.getString(context.getString(R.string.back_up_location), "") ?: ""
        return if (isBackUpEnabled && backUpUri.isNotEmpty()) {
            DocumentFileOperationWrapper(context, fileName, Uri.parse(backUpUri))
        } else {
            FileOperationWrapper(context, fileName, tag)
        }
    }

    @Throws(IOException::class)
    fun startAccLog(logId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.ACC)) {
            logStreams[StreamType.ACC] = startNewLogWithTag("ACC", logId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startPpgLog(logId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.PPG)) {
            logStreams[StreamType.PPG] = startNewLogWithTag("PPG", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_DATA_16)) {
            logStreams[StreamType.PPG_DATA_16] = startNewLogWithTag("PPG_DATA_16", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_DATA_24)) {
            logStreams[StreamType.PPG_DATA_24] = startNewLogWithTag("PPG_DATA_24", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_GREEN)) {
            logStreams[StreamType.PPG_GREEN] = startNewLogWithTag("PPG_GRN", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_RED)) {
            logStreams[StreamType.PPG_RED] = startNewLogWithTag("PPG_RED", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_IR)) {
            logStreams[StreamType.PPG_IR] = startNewLogWithTag("PPG_IR", logId, startTime)
        }

        if (!logStreams.containsKey(StreamType.SPORT_ID)) {
            logStreams[StreamType.SPORT_ID] = startNewLogWithTag("SPORT_ID", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_ADPD4000)) {
            logStreams[StreamType.PPG_ADPD4000] = startNewLogWithTag("PPG_ADPD_4000", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_ADPD4100)) {
            logStreams[StreamType.PPG_ADPD4100] = startNewLogWithTag("PPG_ADPD_4100", logId, startTime)
        }
        if (!logStreams.containsKey(StreamType.PPG_OPERATION_MODE)) {
            logStreams[StreamType.PPG_OPERATION_MODE] = startNewLogWithTag("PPG_OPERATION_MODE", logId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startPpiLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.PPI)) {
            logStreams[StreamType.PPI] = startNewLogWithTag("PPI", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startEcgLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.ECG)) {
            logStreams[StreamType.ECG] = startNewLogWithTag("ECG", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startHrLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.HR)) {
            logStreams[StreamType.HR] = startNewLogWithTag("HR", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    private fun startMarkerLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.MARKER)) {
            logStreams[StreamType.MARKER] = startNewLogWithTag("MARKER", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startMagnetometerLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.MAGNETOMETER)) {
            logStreams[StreamType.MAGNETOMETER] = startNewLogWithTag("MAGNETOMETER", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startGyroLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.GYRO)) {
            logStreams[StreamType.GYRO] = startNewLogWithTag("GYRO", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startPressureLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.PRESSURE)) {
            logStreams[StreamType.PRESSURE] = startNewLogWithTag("PRESSURE", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startLocationLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.LOCATION_COORDINATES)) {
            logStreams[StreamType.LOCATION_COORDINATES] = startNewLogWithTag("LOCATION_COORDINATES", deviceId, startTime)
        }
        if (!logStreams.containsKey(StreamType.LOCATION_DILUTION)) {
            logStreams[StreamType.LOCATION_DILUTION] = startNewLogWithTag("LOCATION_DILUTION", deviceId, startTime)
        }
        if (!logStreams.containsKey(StreamType.LOCATION_SUMMARY)) {
            logStreams[StreamType.LOCATION_SUMMARY] = startNewLogWithTag("LOCATION_SUMMARY", deviceId, startTime)
        }
        if (!logStreams.containsKey(StreamType.LOCATION_NMEA)) {
            logStreams[StreamType.LOCATION_NMEA] = startNewLogWithTag("LOCATION_NMEA", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startTemperatureLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.TEMPERATURE)) {
            logStreams[StreamType.TEMPERATURE] = startNewLogWithTag("TEMPERATURE", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun startSkinTemperatureLog(deviceId: String, startTime: Calendar = Calendar.getInstance()) {
        if (!logStreams.containsKey(StreamType.SKIN_TEMPERATURE)) {
            logStreams[StreamType.SKIN_TEMPERATURE] = startNewLogWithTag("SKIN_TEMPERATURE", deviceId, startTime)
        }
    }

    @Throws(IOException::class)
    fun logAcc(timeStamp: Long, x: Int, y: Int, z: Int) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.ACC]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP X(mg) Y(mg) Z(mg)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $x $y $z\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logEcg(timeStamp: Long, ecg: Int) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.ECG]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP ECG(microV)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $ecg\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logFecg(timeStamp: Long, ecg: Int, bioz: Int, status: UByte) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.ECG]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP ECG BIOZ STATUS\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $ecg $bioz $status\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logHr(timeStamp: Long? = null, data: PolarHrData.PolarHrSample) {
        logStreams[StreamType.HR]?.let { stream ->
            if (!stream.isStarted()) {
                var headerLine = "TIMESTAMP HR PPQ_QUALITY CORRECTED_HR RR_AVAILABLE CONTACT_SUPPORTED CONTACT_STATUS RR(ms)\n"
                stream.write(headerLine)
            }
            val logLine = "${timeStamp ?: ""} ${data.hr} ${data.ppgQuality} ${data.correctedHr} ${data.rrAvailable} ${data.contactStatusSupported} ${data.contactStatus} ${if (data.rrsMs.isEmpty()) "NA" else data.rrsMs.joinToString(separator = " ")}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpg(timeStamp: Long, ppgs: List<Int>, ambient: Int) {
        latestTimeStamp = timeStamp
        ppgTimeStamp = timeStamp
        logStreams[StreamType.PPG]?.let { stream ->
            if (!stream.isStarted()) {
                val ppgChannels = mutableListOf<String>()
                for (index in ppgs.indices) {
                    ppgChannels.add("PPG$index")
                }
                val headerLine = "TIMESTAMP ${ppgChannels.joinToString(separator = " ")} AMBIENT\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${ppgs.joinToString(separator = " ")} $ambient\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgData16Channels(timeStamp: Long, ppgs: List<Int>, status: Long) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_DATA_16]?.let { stream ->
            if (!stream.isStarted()) {
                val ppgChannels = mutableListOf<String>()
                for (index in ppgs.indices) {
                    ppgChannels.add("PPG$index")
                }
                val headerLine = "TIMESTAMP ${ppgChannels.joinToString(separator = " ")} STATUS\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${ppgs.joinToString(separator = " ")} $status\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgGreen(timeStamp: Long, ppgs: List<Int>) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_GREEN]?.let { stream ->
            if (!stream.isStarted()) {
                val ppgChannels = mutableListOf<String>()
                for (index in ppgs.indices) {
                    ppgChannels.add("GREEN${index + 1}")
                }
                val headerLine = "TIMESTAMP ${ppgChannels.joinToString(separator = " ")}\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${ppgs.joinToString(separator = " ")}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgRed(timeStamp: Long, ppgs: List<Int>) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_RED]?.let { stream ->
            if (!stream.isStarted()) {
                val ppgChannels = mutableListOf<String>()
                for (index in ppgs.indices) {
                    ppgChannels.add("RED${index + 1}")
                }
                val headerLine = "TIMESTAMP ${ppgChannels.joinToString(separator = " ")}\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${ppgs.joinToString(separator = " ")}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgIr(timeStamp: Long, ppgs: List<Int>) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_IR]?.let { stream ->
            if (!stream.isStarted()) {
                val ppgChannels = mutableListOf<String>()
                for (index in ppgs.indices) {
                    ppgChannels.add("IR${index + 1}")
                }
                val headerLine = "TIMESTAMP ${ppgChannels.joinToString(separator = " ")}\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${ppgs.joinToString(separator = " ")}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgSportIdData(timeStamp: Long, sportId: Long) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.SPORT_ID]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP SPORT_ID\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${sportId}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgAdpd4000Data(timeStamp: Long, channel1GainTs: List<Int>, channel2GainTs: List<Int>, numIntTs: List<Int>) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_ADPD4000]?.let { stream ->
            if (!stream.isStarted()) {
                val numIntTsArray = mutableListOf<String>()
                for (value in 1..12) {
                    numIntTsArray.add("NUMINT_TS$value")
                }
                val tiaGainChTsArray = mutableListOf<String>()
                for (value in 1..12) {
                    tiaGainChTsArray.add("TIA_GAIN_CH1_TS$value")
                    tiaGainChTsArray.add("TIA_GAIN_CH2_TS$value")
                }

                val headerLine = "TIMESTAMP ${numIntTsArray.joinToString(separator = " ")} ${tiaGainChTsArray.joinToString(separator = " ")}\n"
                stream.write(headerLine)
            }
            val channelGainTs = channel1GainTs.zip(channel2GainTs)
            val logLine = "$timeStamp ${numIntTs.joinToString(separator = " ")} ${channelGainTs.toList().joinToString(separator = " ") { "${it.first} ${it.second}" }}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgAdpd4100Data(timeStamp: Long, channel1GainTs: List<Int>, channel2GainTs: List<Int>, numIntTs: List<Int>) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_ADPD4100]?.let { stream ->
            if (!stream.isStarted()) {
                val numIntTsArray = mutableListOf<String>()
                for (value in 1..12) {
                    numIntTsArray.add("NUMINT_TS$value")
                }
                val tiaGainChTsArray = mutableListOf<String>()
                for (value in 1..12) {
                    tiaGainChTsArray.add("TIA_GAIN_CH1_TS$value")
                    tiaGainChTsArray.add("TIA_GAIN_CH2_TS$value")
                }

                val headerLine = "TIMESTAMP ${numIntTsArray.joinToString(separator = " ")} ${tiaGainChTsArray.joinToString(separator = " ")}\n"
                stream.write(headerLine)
            }
            val channelGainTs = channel1GainTs.zip(channel2GainTs)
            val logLine = "$timeStamp ${numIntTs.joinToString(separator = " ")} ${channelGainTs.toList().joinToString(separator = " ") { "${it.first} ${it.second}" }}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpgOperationMode(timeStamp: Long, operationMode: UInt) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PPG_OPERATION_MODE]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP OPERATION_MODE\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $operationMode\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPpi(ppi: Int, errorEstimate: Int, blocker: Boolean, contact: Boolean, contactSupported: Boolean, hr: Int, timeStamp: ULong) {
        logStreams[StreamType.PPI]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP PPI(ms) ERROR_ESTIMATE BLOCKER_BIT SKIN_CONTACT_STATUS SKIN_CONTACT_SUPPORT HR\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $ppi $errorEstimate ${if (blocker) 1 else 0} ${if (contact) 1 else 0} ${if (contactSupported) 1 else 0} $hr\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logMagnetometer(timeStamp: Long, x: Float, y: Float, z: Float) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.MAGNETOMETER]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP X(Gauss) Y(Gauss) Z(Gauss)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $x $y $z\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logGyro(timeStamp: Long, x: Float, y: Float, z: Float) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.GYRO]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP X(deg/sec) Y(deg/sec) Z(deg/sec)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $x $y $z\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logPressure(timeStamp: Long, pressure: Float) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.PRESSURE]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP Pressure(mBar)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $pressure\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logLocationCoordinates(timeStamp: Long, location: GpsCoordinatesSample) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.LOCATION_COORDINATES]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP LATITUDE LONGITUDE TIME CUMULATIVE_DISTANCE SPEED USED_ACCELERATION_SPEED COORDINATE_SPEED ACCELERATION_SPEED_FACTORY COURSE GPS_CHIP_SPEED FIX SPEED_FLAG FUSION_STATE\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${location.latitude} ${location.longitude} ${location.time} ${location.cumulativeDistance} ${location.speed} ${location.usedAccelerationSpeed} ${location.coordinateSpeed} ${location.accelerationSpeedFactor} ${location.course} ${location.gpsChipSpeed} ${location.fix} ${location.speedFlag} ${location.fusionState}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logLocationDilution(timeStamp: Long, satelliteDilutionSample: GpsSatelliteDilutionSample) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.LOCATION_DILUTION]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP DILUTION ALTITUDE NUMBER_OF_SATELLITES FIX\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${satelliteDilutionSample.dilution} ${satelliteDilutionSample.altitude} ${satelliteDilutionSample.numberOfSatellites} ${satelliteDilutionSample.fix}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logLocationSatelliteSummary(timeStamp: Long, summarySample: GpsSatelliteSummarySample) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.LOCATION_SUMMARY]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP " +
                        "SEEN_B1_GPS_NBR_OF_SAT " +
                        "SEEN_B1_GPS_MAX_SNR " +
                        "SEEN_B1_GLONASS_NBR_OF_SAT " +
                        "SEEN_B1_GLONASS_MAX_SNR " +
                        "SEEN_B1_GALILEO_NBR_OF_SAT " +
                        "SEEN_B1_GALILEO_MAX_SNR " +
                        "SEEN_B1_BEIDOU_NBR_OF_SAT " +
                        "SEEN_B1_BEIDOU_MAX_SNR " +
                        "SEEN_B1_NBR_OF_SAT " +
                        "SEEN_B1_SNR_TOP5_AVG " +
                        "USED_B1_GPS_NBR_OF_SAT " +
                        "USED_B1_GPS_MAX_SNR " +
                        "USED_B1_GLONASS_NBR_OF_SAT " +
                        "USED_B1_GLONASS_MAX_SNR " +
                        "USED_B1_GALILEO_NBR_OF_SAT " +
                        "USED_B1_GALILEO_MAX_SNR " +
                        "USED_B1_BEIDOU_NBR_OF_SAT " +
                        "USED_B1_BEIDOU_MAX_SNR " +
                        "USED_B1_NBR_OF_SAT " +
                        "USED_B1_SNR_TOP5_AVG " +
                        "SEEN_B2_GPS_NBR_OF_SAT " +
                        "SEEN_B2_GPS_MAX_SNR " +
                        "SEEN_B2_GLONASS_NBR_OF_SAT " +
                        "SEEN_B2_GLONASS_MAX_SNR " +
                        "SEEN_B2_GALILEO_NBR_OF_SAT " +
                        "SEEN_B2_GALILEO_MAX_SNR " +
                        "SEEN_B2_BEIDOU_NBR_OF_SAT " +
                        "SEEN_B2_BEIDOU_MAX_SNR " +
                        "SEEN_B2_NBR_OF_SAT " +
                        "SEEN_B2_SNR_TOP5_AVG " +
                        "USED_B2_GPS_NBR_OF_SAT " +
                        "USED_B2_GPS_MAX_SNR " +
                        "USED_B2_GLONASS_NBR_OF_SAT " +
                        "USED_B2_GLONASS_MAX_SNR " +
                        "USED_B2_GALILEO_NBR_OF_SAT " +
                        "USED_B2_GALILEO_MAX_SNR " +
                        "USED_B2_BEIDOU_NBR_OF_SAT " +
                        "USED_B2_BEIDOU_MAX_SNR " +
                        "USED_B2_NBR_OF_SAT " +
                        "USED_B2_SNR_TOP5_AVG " +
                        "MAX_SNR" +
                        "\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp " +
                    "${summarySample.seenSatelliteSummaryBand1.gpsNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand1.gpsMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand1.glonassNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand1.glonassMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand1.galileoNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand1.galileoMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand1.beidouNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand1.beidouMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand1.nbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand1.snrTop5Avg} " +
                    "${summarySample.usedSatelliteSummaryBand1.gpsNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand1.gpsMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand1.glonassNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand1.glonassMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand1.galileoNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand1.galileoMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand1.beidouNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand1.beidouMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand1.nbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand1.snrTop5Avg} " +
                    "${summarySample.seenSatelliteSummaryBand2.gpsNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand2.gpsMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand2.glonassNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand2.glonassMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand2.galileoNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand2.galileoMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand2.beidouNbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand2.beidouMaxSnr} " +
                    "${summarySample.seenSatelliteSummaryBand2.nbrOfSat} " +
                    "${summarySample.seenSatelliteSummaryBand2.snrTop5Avg} " +
                    "${summarySample.usedSatelliteSummaryBand2.gpsNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand2.gpsMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand2.glonassNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand2.glonassMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand2.galileoNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand2.galileoMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand2.beidouNbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand2.beidouMaxSnr} " +
                    "${summarySample.usedSatelliteSummaryBand2.nbrOfSat} " +
                    "${summarySample.usedSatelliteSummaryBand2.snrTop5Avg} " +
                    "${summarySample.maxSnr} " +
                    "\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logLocationNmeaSummary(timeStamp: Long, nmeaSample: GpsNMEASample) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.LOCATION_NMEA]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP MEASUREMENT_PERIOD STATUS_FLAGS NMEA_MESSAGE\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp ${nmeaSample.measurementPeriod} ${nmeaSample.statusFlags} ${nmeaSample.nmeaMessage}\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logTemperature(timeStamp: Long, temperature: Float) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.TEMPERATURE]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP TEMPERATURE(Celsius)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $temperature\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun logSkinTemperature(timeStamp: Long, temperature: Float) {
        latestTimeStamp = timeStamp
        logStreams[StreamType.SKIN_TEMPERATURE]?.let { stream ->
            if (!stream.isStarted()) {
                val headerLine = "TIMESTAMP SKIN TEMPERATURE(Celsius)\n"
                stream.write(headerLine)
            }
            val logLine = "$timeStamp $temperature\n"
            stream.write(logLine)
        }
    }

    @Throws(IOException::class)
    fun marker(start: Boolean, deviceId: String, isStartMark: Boolean, timeStamp: Long = 0L) {
        val markerStamp = if (timeStamp == 0L) {
            latestTimeStamp
        } else {
            timeStamp
        }
        startMarkerLog(deviceId)
        logStreams[StreamType.MARKER]?.let { stream ->
            val logLine = "MARKER_${if (isStartMark) "START" else "STOP"} $markerStamp\n"
            stream.write(logLine)
        }
    }

    @Throws(Exception::class)
    fun finalizeAllStreams(): ArrayList<Uri> {
        val fileUris = ArrayList<Uri>()
        for (value in logStreams.values) {
            try {
                value.close()
                if (value.length() > 0) {
                    fileUris.add(value.getUri())
                } else {
                    value.delete()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Closing the file ${value.fileName} failed")
            } catch (e: SecurityException) {
                Log.e(TAG, "Length check the file ${value.fileName} failed")
            }
        }
        logStreams.clear()
        return fileUris
    }

    enum class StreamType {
        ACC,
        PPG,
        PPG_DATA_16,
        PPG_DATA_24,
        PPG_GREEN,
        PPG_RED,
        PPG_IR,
        PPG_ADPD4000,
        PPG_ADPD4100,
        PPG_OPERATION_MODE,
        PPI,
        MARKER,
        ECG,
        HR,
        SPORT_ID,
        MAGNETOMETER,
        GYRO,
        PRESSURE,
        LOCATION_COORDINATES,
        LOCATION_DILUTION,
        LOCATION_SUMMARY,
        LOCATION_NMEA,
        TEMPERATURE,
        SKIN_TEMPERATURE
    }

}
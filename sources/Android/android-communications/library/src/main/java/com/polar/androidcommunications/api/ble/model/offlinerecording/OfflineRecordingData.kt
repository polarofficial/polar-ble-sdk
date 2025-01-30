package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecret
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.common.ble.TypeUtils
import org.joda.time.DateTime
import java.util.*

internal class OfflineRecordingData<out T>(
    val offlineRecordingHeader: OfflineRecordingHeader,
    val startTime: Calendar,
    val recordingSettings: PmdSetting?,
    val data: T
) {

    data class OfflineRecordingHeader internal constructor(
        val magic: UInt,
        val version: UInt,
        val free: UInt,
        val eswHash: UInt,
    )

    data class OfflineRecordingMetaData internal constructor(
        val offlineRecordingHeader: OfflineRecordingHeader,
        val startTime: Calendar,
        val recordingSettings: PmdSetting?,
        val securityInfo: PmdSecret,
        val dataPayloadSize: Int
    )

    companion object {
        private const val TAG = "OfflineRecordingData"

        private const val SECURITY_STRATEGY_INDEX = 0
        private const val SECURITY_STRATEGY_LENGTH = 1

        private const val OFFLINE_HEADER_MAGIC = 0x3D7C4C2BU

        private const val OFFLINE_HEADER_LENGTH = 16
        private const val OFFLINE_SETTINGS_SIZE_FIELD_LENGTH = 1
        private const val DATE_TIME_LENGTH = 20
        private const val PACKET_SIZE_LENGTH = 2

        @Throws(Exception::class)
        fun parseDataFromOfflineFile(fileData: ByteArray, type: PmdMeasurementType, secret: PmdSecret? = null, lastTimestamp: ULong = 0uL): OfflineRecordingData<Any> {
            BleLogger.d(TAG, "Start offline file parsing. File size is ${fileData.size} and type $type, previous file last timestamp: $lastTimestamp")

            // guard
            if (fileData.isEmpty()) {
                throw OfflineRecordingError.OfflineRecordingEmptyFile
            }

            val (metaData, metaDataLength) = try {
                parseMetaData(fileData, secret)
            } catch (e: Exception) {
                throw OfflineRecordingError.OfflineRecordingErrorMetaDataParseFailed(detailMessage = e.toString())
            }

            val payloadDataBytes = fileData.drop(metaDataLength)

            // guard
            if (payloadDataBytes.isEmpty()) {
                throw OfflineRecordingError.OfflineRecordingNoPayloadData
            }

            val parsedData = parseData(
                dataBytes = payloadDataBytes,
                metaData = metaData,
                builder = getDataBuilder(type),
                lastTimestamp = lastTimestamp
            )

            return OfflineRecordingData(
                offlineRecordingHeader = metaData.offlineRecordingHeader,
                recordingSettings = metaData.recordingSettings,
                startTime = metaData.startTime,
                data = parsedData
            )
        }

        private fun getDataBuilder(type: PmdMeasurementType): Any {
            val builder: Any = when (type) {
                PmdMeasurementType.ECG -> EcgData()
                PmdMeasurementType.PPG -> PpgData()
                PmdMeasurementType.ACC -> AccData()
                PmdMeasurementType.PPI -> PpiData()
                PmdMeasurementType.GYRO -> GyrData()
                PmdMeasurementType.MAGNETOMETER -> MagData()
                PmdMeasurementType.SKIN_TEMP -> SkinTemperatureData()
                PmdMeasurementType.LOCATION -> GnssLocationData()
                PmdMeasurementType.PRESSURE -> PressureData()
                PmdMeasurementType.TEMPERATURE -> TemperatureData()
                PmdMeasurementType.OFFLINE_HR -> OfflineHrData()
                else -> {
                    throw OfflineRecordingError.OfflineRecordingErrorNoParserForData
                }
            }
            return builder
        }

        private fun parseSecurityStrategy(strategyBytes: List<Byte>): PmdSecret.SecurityStrategy {
            val strategy = TypeUtils.convertArrayToUnsignedByte(strategyBytes.toByteArray())
            return PmdSecret.SecurityStrategy.fromByte(strategy)
        }

        private fun parseMetaData(fileBytes: ByteArray, secret: PmdSecret?): Pair<OfflineRecordingMetaData, Int> {
            var securityOffset = 0
            val offlineRecordingSecurityStrategy = parseSecurityStrategy(fileBytes.slice(SECURITY_STRATEGY_INDEX until SECURITY_STRATEGY_LENGTH))
            securityOffset += SECURITY_STRATEGY_LENGTH

            val metaDataBytes = decryptMetaData(offlineRecordingSecurityStrategy, fileBytes.slice(securityOffset until fileBytes.size).toByteArray(), secret)

            val offlineRecordingHeader = parseHeader(metaDataBytes.slice(0 until OFFLINE_HEADER_LENGTH))
            var metaDataOffset = OFFLINE_HEADER_LENGTH

            require(offlineRecordingHeader.magic == OFFLINE_HEADER_MAGIC) {
                throw OfflineRecordingError.OfflineRecordingHasWrongSignature
            }

            val offlineRecordingStartTime = parseStartTime(metaDataBytes.slice(metaDataOffset until metaDataOffset + DATE_TIME_LENGTH))
            metaDataOffset += DATE_TIME_LENGTH

            val (pmdSetting, settingsLength) = parseSettings(metaDataBytes.slice(metaDataOffset until metaDataBytes.size))
            metaDataOffset += settingsLength

            val (payloadSecurity, securityInfoLength) = parseSecurityInfo(metaDataBytes.slice(metaDataOffset until metaDataBytes.size), secret)
            metaDataOffset += securityInfoLength

            // padding bytes
            val paddingBytes1Length = parsePaddingBytes(metaDataOffset, offlineRecordingSecurityStrategy)
            metaDataOffset += paddingBytes1Length

            val dataPayloadSize = try {
                parsePacketSize(metaDataBytes.slice(metaDataOffset until metaDataOffset + PACKET_SIZE_LENGTH)).toInt()
            } catch (e: Exception) {
                0
            }
            require(dataPayloadSize > 0) {
                throw OfflineRecordingError.OfflineRecordingErrorMetaDataParseFailed(detailMessage = "Data payload size parse failed. The size of the file is ${fileBytes.size}, accessing the index ${metaDataOffset + PACKET_SIZE_LENGTH}  ")
            }

            metaDataOffset += PACKET_SIZE_LENGTH

            val paddingBytes2Length = parsePaddingBytes(metaDataOffset, offlineRecordingSecurityStrategy)
            metaDataOffset += paddingBytes2Length

            val metaData = OfflineRecordingMetaData(
                offlineRecordingHeader = offlineRecordingHeader,
                startTime = offlineRecordingStartTime,
                recordingSettings = pmdSetting,
                securityInfo = payloadSecurity,
                dataPayloadSize = dataPayloadSize
            )
            return Pair(metaData, securityOffset + metaDataOffset)
        }

        private fun parseSettings(metaDataBytes: List<Byte>): Pair<PmdSetting?, Int> {
            var offset = 0
            val settingsLength = metaDataBytes[offset]
            offset += OFFLINE_SETTINGS_SIZE_FIELD_LENGTH
            val settingBytes = metaDataBytes.slice(offset until (offset + settingsLength))
            val pmdSetting = if (settingBytes.isNotEmpty()) {
                PmdSetting(settingBytes.toByteArray())
            } else {
                null
            }
            return Pair(pmdSetting, offset + settingsLength)
        }

        private fun decryptMetaData(offlineRecordingSecurityStrategy: PmdSecret.SecurityStrategy, metaData: ByteArray, secret: PmdSecret?): ByteArray {
            return when (offlineRecordingSecurityStrategy) {
                PmdSecret.SecurityStrategy.NONE -> {
                    metaData
                }
                PmdSecret.SecurityStrategy.XOR -> {
                    // guard
                    if (secret == null) {
                        throw OfflineRecordingError.OfflineRecordingErrorSecretMissing
                    }
                    secret.decryptArray(metaData)

                }
                PmdSecret.SecurityStrategy.AES128,
                PmdSecret.SecurityStrategy.AES256 -> {
                    // guard
                    if (secret == null) {
                        throw OfflineRecordingError.OfflineRecordingErrorSecretMissing
                    }
                    // guard
                    if (secret.strategy != offlineRecordingSecurityStrategy) {
                        throw OfflineRecordingError.OfflineRecordingSecurityStrategyMissMatch("Offline file is encrypted using $offlineRecordingSecurityStrategy. The key provided is ${secret.strategy} ")
                    }

                    val endOffset = (metaData.size / 16 * 16)
                    val metaDataChunk = metaData.slice(0 until endOffset).toByteArray()
                    secret.decryptArray(metaDataChunk)
                }
            }
        }

        private fun parsePaddingBytes(metaDataOffset: Int, offlineRecordingSecurityStrategy: PmdSecret.SecurityStrategy): Int {
            return when (offlineRecordingSecurityStrategy) {
                PmdSecret.SecurityStrategy.NONE,
                PmdSecret.SecurityStrategy.XOR -> 0
                PmdSecret.SecurityStrategy.AES128,
                PmdSecret.SecurityStrategy.AES256 -> 16 - metaDataOffset.mod(16)
            }
        }

        private fun parseSecurityInfo(securityInfoBytes: List<Byte>, secret: PmdSecret?): Pair<PmdSecret, Int> {
            var offset = 0
            val infoLength = securityInfoBytes[offset].toInt()
            offset++

            if (infoLength == 0) {
                return if (secret == null) {
                    Pair(PmdSecret(strategy = PmdSecret.SecurityStrategy.NONE, key = byteArrayOf()), offset)
                } else {
                    Pair(secret, offset)
                }
            }

            val strategy = securityInfoBytes[offset].toUByte()
            offset++

            return when (PmdSecret.SecurityStrategy.fromByte((strategy))) {
                PmdSecret.SecurityStrategy.NONE -> {
                    Pair(PmdSecret(strategy = PmdSecret.SecurityStrategy.NONE, key = byteArrayOf()), offset)
                }
                PmdSecret.SecurityStrategy.XOR -> {
                    val indexOfXor = securityInfoBytes[offset].toInt()
                    val xor = secret?.key?.get(indexOfXor) ?: throw OfflineRecordingError.OfflineRecordingErrorSecretMissing
                    offset++
                    Pair(PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = byteArrayOf(xor)), offset)
                }
                PmdSecret.SecurityStrategy.AES128 -> {
                    val key = secret?.key ?: throw OfflineRecordingError.OfflineRecordingErrorSecretMissing
                    Pair(PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = key), offset)
                }
                PmdSecret.SecurityStrategy.AES256 -> {
                    val key = secret?.key ?: throw OfflineRecordingError.OfflineRecordingErrorSecretMissing
                    Pair(PmdSecret(strategy = PmdSecret.SecurityStrategy.AES256, key = key), offset)
                }
            }
        }

        private fun parseHeader(headerBytes: List<Byte>): OfflineRecordingHeader {
            var offset = 0
            val step = 4
            val magic = TypeUtils.convertArrayToUnsignedInt(headerBytes.toByteArray(), offset, step)
            offset += step
            val version = TypeUtils.convertArrayToUnsignedInt(headerBytes.toByteArray(), offset, step)
            offset += step
            val free = TypeUtils.convertArrayToUnsignedInt(headerBytes.toByteArray(), offset, step)
            offset += step
            val eswHash = TypeUtils.convertArrayToUnsignedInt(headerBytes.toByteArray(), offset, step)
            offset += step

            return OfflineRecordingHeader(magic = magic, version = version, free = free, eswHash = eswHash)
        }

        private fun parseStartTime(startTimeBytes: List<Byte>): Calendar {
            val startTimeInIso8601 = String(startTimeBytes.dropLast(1).toByteArray()).replace(' ', 'T') + "Z"
            val dt = DateTime(startTimeInIso8601)
            return dt.toCalendar(null)
        }

        private fun parsePacketSize(packetSize: List<Byte>): UInt {
            return TypeUtils.convertArrayToUnsignedInt(packetSize.toByteArray(), 0, 2)
        }

        private fun <T> parseData(dataBytes: List<Byte>, metaData: OfflineRecordingMetaData, builder: T, lastTimestamp: ULong = 0uL): T {

            var previousTimeStamp: ULong = lastTimestamp
            var packetSize = metaData.dataPayloadSize
            val sampleRate = metaData.recordingSettings?.settings?.get(PmdSetting.PmdSettingType.SAMPLE_RATE)?.first() ?: 0
            val factor = metaData.recordingSettings?.settings?.get(PmdSetting.PmdSettingType.FACTOR)?.first()?.let {
                val ieee754 = it
                java.lang.Float.intBitsToFloat(ieee754)
            } ?: 1.0f

            var offset = 0
            val decryptedData = metaData.securityInfo.decryptArray(dataBytes.toByteArray())
            do {
                val data = decryptedData.slice(offset until packetSize + offset)
                offset += packetSize
                val dataFrame = PmdDataFrame(data.toByteArray(),
                    getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
                    getFactor = { factor }
                ) { sampleRate }

                previousTimeStamp = dataFrame.timeStamp

                when (builder) {
                    is EcgData -> {
                        val ecgData = EcgData.parseDataFromDataFrame(dataFrame)
                        builder.ecgSamples.addAll(ecgData.ecgSamples)
                    }
                    is AccData -> {
                        val accData = AccData.parseDataFromDataFrame(dataFrame)
                        builder.accSamples.addAll(accData.accSamples)
                    }
                    is GyrData -> {
                        val gyrData = GyrData.parseDataFromDataFrame(dataFrame)
                        builder.gyrSamples.addAll(gyrData.gyrSamples)
                    }
                    is MagData -> {
                        val magData = MagData.parseDataFromDataFrame(dataFrame)
                        builder.magSamples.addAll(magData.magSamples)
                    }
                    is PpgData -> {
                        val ppgData = PpgData.parseDataFromDataFrame(dataFrame)
                        builder.ppgSamples.addAll(ppgData.ppgSamples)
                    }
                    is PressureData -> {
                        val pressureData = PressureData.parseDataFromDataFrame(dataFrame)
                        builder.pressureSamples.addAll(pressureData.pressureSamples)
                    }
                    is GnssLocationData -> {
                        val gnssLocationData = GnssLocationData.parseDataFromDataFrame(dataFrame)
                        builder.gnssLocationDataSamples.addAll(gnssLocationData.gnssLocationDataSamples)
                    }
                    is PpiData -> {
                        val ppiData = PpiData.parseDataFromDataFrame(dataFrame)
                        builder.ppiSamples.addAll(ppiData.ppiSamples)
                    }

                    is OfflineHrData -> {
                        val offlineHrData = OfflineHrData.parseDataFromDataFrame(dataFrame)
                        builder.hrSamples.addAll(offlineHrData.hrSamples)
                    }

                    is TemperatureData -> {
                        val offlineTemperatureData = TemperatureData.parseDataFromDataFrame(dataFrame)
                        builder.temperatureSamples.addAll(offlineTemperatureData.temperatureSamples)
                    }
                }

                if (offset < decryptedData.size) {
                    packetSize = parsePacketSize(decryptedData.slice(offset until offset + PACKET_SIZE_LENGTH)).toInt()
                    offset += 2
                }
            } while (offset < decryptedData.size)
            return builder
        }
    }
}


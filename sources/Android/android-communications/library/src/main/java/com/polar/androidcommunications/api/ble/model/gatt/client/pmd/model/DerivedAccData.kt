package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.PmdDataParseException
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.common.ble.TypeUtils

/**
 * Internal PMD model for derived ACC measurement data.
 *
 * One [DerivedAccSample] is produced per time window (e.g. 1 sample/s with 1000 ms window).
 *
 * @param activeMethods set of active method IDs (0-9, aligned to PolarDerivedMeasurementMethod.id)
 *   obtained from the recording settings [DERIVED_MEASUREMENT_METHOD].
 */
internal class DerivedAccData(val activeMethods: Set<Int> = emptySet()) {

    /**
     * One derived ACC output sample aligned to the end of its time window.
     *
     * @property timeStamp sample timestamp in nanoseconds
     * @property methodValues map from method ID → ordered list of output values.
     *   For spec-compliant type-15 frames: component methods (0-4) produce [x, y, z];
     *   scalar methods (5-9) produce [v].
     *   For current-firmware type-2 ACC frames: methods 0-3 produce [x, y, z];
     *   method 4 (Std) and methods 5-9 produce [v] (firmware scalar deviation).
     */
    data class DerivedAccSample internal constructor(
        val timeStamp: ULong,
        val methodValues: Map<Int, List<Int>>
    )

    val derivedSamples: MutableList<DerivedAccSample> = mutableListOf()

    companion object {

        /**
         * Methods 0-4 are all D→D (component-wise).
         * For 3D ACC: Downsample, Min, Max, Avg, Std each produce X, Y, Z values.
         * Used when parsing spec-compliant type-15 DERIVED_MEASUREMENT frames.
         */
        private val COMPONENT_METHODS_SPEC = setOf(0, 1, 2, 3, 4)

        /**
         * Method 4 (Std) deviates from spec and outputs one scalar per window.
         * Methods 5-9 are D→1 scalars per spec and firmware.
         */
        private val COMPONENT_METHODS_FIRMWARE = setOf(0, 1, 2, 3)

        /** Methods whose outputs are non-negative (unsigned-promoted per spec). */
        private val UNSIGNED_METHODS = setOf(4, 5, 6, 7, 8, 9)

        /**
         * Derived Data Measurement Frame header layout inside dataContent.
         *
         * Offset 0: Derived Data Frame Type (shall be 0)
         * Offset 1: Source Measurement Type
         * Offset 2: Source Measurement Frame Type
         * Offsets 3-4: Derived Measurement Method Bits (uint16 LE) — bitmask of active methods.
         * Offset 5+: Sample data
         *
         * This header is present ONLY for spec-compliant type-15 (DERIVED_MEASUREMENT) frames.
         * Current firmware stores derived output as type-2 ACC frames with no such header.
         */
        private const val DERIVED_FRAME_HEADER_SIZE = 5
        private const val DERIVED_FRAME_METHOD_BITS_OFFSET = 3

        fun parseDataFromDataFrame(frame: PmdDataFrame, activeMethods: Set<Int>): DerivedAccData {
            val result = DerivedAccData(activeMethods)
            if (frame.dataContent.isEmpty()) return result

            val isSpecDerivedFrame = frame.measurementType == PmdMeasurementType.DERIVED_MEASUREMENT

            val sampleDataBytes: ByteArray
            val effectiveMethods: List<Int>
            val componentMethods: Set<Int>

            if (isSpecDerivedFrame) {
                if (frame.dataContent.size < DERIVED_FRAME_HEADER_SIZE) return result
                val methodBits =
                    (frame.dataContent[DERIVED_FRAME_METHOD_BITS_OFFSET].toInt() and 0xFF) or
                    ((frame.dataContent[DERIVED_FRAME_METHOD_BITS_OFFSET + 1].toInt() and 0xFF) shl 8)
                effectiveMethods = (0..15).filter { (methodBits shr it) and 1 == 1 }
                sampleDataBytes = frame.dataContent.copyOfRange(
                    DERIVED_FRAME_HEADER_SIZE, frame.dataContent.size)
                componentMethods = COMPONENT_METHODS_SPEC
            } else {
                val sampleSizeInBytes = sampleSizeFromFrameType(frame.frameType)
                val sortedHint = activeMethods.sorted()
                var cumulative = 0
                val trimmed = mutableListOf<Int>()
                for (method in sortedHint) {
                    val vals = if (method in COMPONENT_METHODS_FIRMWARE) 3 else 1
                    val newTotal = cumulative + vals
                    if (newTotal * sampleSizeInBytes <= frame.dataContent.size) {
                        cumulative = newTotal
                        trimmed.add(method)
                    }
                }
                if (trimmed.size < sortedHint.size) {
                    BleLogger.w("DerivedAccData",
                        "parseDataFromDataFrame: hint $sortedHint trimmed to $trimmed " +
                        "to fit ${frame.dataContent.size} bytes (sampleSizeInBytes=$sampleSizeInBytes)")
                }
                effectiveMethods = trimmed
                sampleDataBytes = frame.dataContent
                componentMethods = COMPONENT_METHODS_FIRMWARE
            }

            if (effectiveMethods.isEmpty()) return result

            val sampleSizeInBytes = sampleSizeFromFrameType(frame.frameType)

            val valuesPerSample: Int = effectiveMethods.fold(0) { acc, m -> acc + if (m in componentMethods) 3 else 1 }
            val bytesPerSample: Int = valuesPerSample * sampleSizeInBytes
            if (bytesPerSample == 0) return result

            val samplesSize: Int = sampleDataBytes.size / bytesPerSample
            if (samplesSize <= 0) return result

            val timeStamps: List<ULong> = if (samplesSize == 1 || frame.previousTimeStamp == 0uL) {
                List(samplesSize) { frame.timeStamp }
            } else {
                val delta = (frame.timeStamp - frame.previousTimeStamp).toDouble() / samplesSize.toDouble()
                (0 until samplesSize).map { i ->
                    if (i == samplesSize - 1) frame.timeStamp
                    else (frame.previousTimeStamp + (delta * (i + 1)).toULong())
                }
            }

            // --- Parse samples -----------------------------------------------------------
            var offset = 0
            for (sampleIndex in 0 until samplesSize) {
                val methodValues = mutableMapOf<Int, List<Int>>()
                for (methodId in effectiveMethods) {
                    val isUnsigned = methodId in UNSIGNED_METHODS
                    if (methodId in componentMethods) {
                        val x = readValue(sampleDataBytes, offset, sampleSizeInBytes, isUnsigned); offset += sampleSizeInBytes
                        val y = readValue(sampleDataBytes, offset, sampleSizeInBytes, isUnsigned); offset += sampleSizeInBytes
                        val z = readValue(sampleDataBytes, offset, sampleSizeInBytes, isUnsigned); offset += sampleSizeInBytes
                        methodValues[methodId] = listOf(x, y, z)
                    } else {
                        val v = readValue(sampleDataBytes, offset, sampleSizeInBytes, unsigned = true); offset += sampleSizeInBytes
                        methodValues[methodId] = listOf(v)
                    }
                }
                result.derivedSamples.add(
                    DerivedAccSample(timeStamp = timeStamps[sampleIndex], methodValues = methodValues)
                )
            }
            return result
        }

        private fun sampleSizeFromFrameType(frameType: PmdDataFrame.PmdDataFrameType): Int =
            when (frameType) {
                PmdDataFrame.PmdDataFrameType.TYPE_0 -> 2
                PmdDataFrame.PmdDataFrameType.TYPE_1 -> 4
                PmdDataFrame.PmdDataFrameType.TYPE_2 -> 3
                else -> throw PmdDataParseException(
                    "DerivedAcc frame type $frameType is not supported"
                )
            }

        private fun readValue(data: ByteArray, offset: Int, length: Int, unsigned: Boolean): Int =
            if (unsigned) TypeUtils.convertArrayToUnsignedInt(data, offset, length).toInt()
            else TypeUtils.convertArrayToSignedInt(data, offset, length)
    }
}



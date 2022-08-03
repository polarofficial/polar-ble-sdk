package com.polar.androidcommunications.api.ble.model.advertisement

import androidx.annotation.VisibleForTesting
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceIdUtility
import com.polar.androidcommunications.api.ble.model.polar.PolarAdvDataUtility.getPolarModelNameFromAdvLocalName
import com.polar.androidcommunications.api.ble.model.polar.PolarAdvDataUtility.isPolarDevice
import com.polar.androidcommunications.common.ble.BleUtils.AD_TYPE
import com.polar.androidcommunications.common.ble.BleUtils.EVENT_TYPE
import java.util.*

class BleAdvertisementContent {
    companion object {
        const val BLE_ADV_POLAR_PREFIX_IN_LOCAL_NAME = "Polar"
    }

    /**
     * The latest received advertisement data. Old data is cleared when
     * new advertisement data arrives
     */
    @JvmField
    val advertisementData = HashMap<AD_TYPE, ByteArray>()

    /**
     * The latest up to date advertisement data. When new advertisement data
     * arrives the matching data fields are updated, rest are kept in previous values
     */
    @JvmField
    val advertisementDataAll = HashMap<AD_TYPE, ByteArray>()

    /**
     * @return Advertised local name <BR></BR>
     */
    var name = ""
        private set

    /**
     * Get the Device type contain in GAP local name for Polar Devices.
     *
     * @return String that for "Polar H7 20346EAB" will contain "H7"
     */
    var polarDeviceType = ""
        private set

    /**
     * @return polar device id in int
     */
    var polarDeviceIdInt: Long = 0
        private set

    /**
     * Get the Device ID contain in GAP local name for Polar Devices.
     *
     * @return String that for "Polar H7 20346EAB" will contain "20346EAB"
     */
    var polarDeviceId = ""
        private set

    /**
     * @return get current polar manufacturer hr sensor data fields, updated on every advertisement event
     */
    val polarHrAdvertisement = BlePolarHrAdvertisement()

    /**
     * @return return last advertiset timestamp in unix timestamp
     */
    var advertisementTimeStamp = System.currentTimeMillis() / 1000L
        private set

    /**
     * @return last received advertisement event type, Note endpoint might not be able to determine the event type <BR></BR>
     */
    var advertisementEventType = EVENT_TYPE.ADV_IND
        private set

    // rssi related
    private val rssiValues: MutableList<Int> = ArrayList()
    private val sortedRssiValues: MutableList<Int> = ArrayList()

    /**
     * @return median from 7 buffered rssi values
     */
    var medianRssi = -100
        private set

    /**
     * @return current rssi value
     */
    var rssi = -100
        private set

    fun processAdvertisementData(advData: Map<AD_TYPE, ByteArray>, advertisementEventType: EVENT_TYPE, rssi: Int) {
        // start access of atomic section
        advertisementData.clear()
        advertisementData.putAll(advData)
        advertisementDataAll.putAll(advData)

        this.advertisementEventType = advertisementEventType
        advertisementTimeStamp = System.currentTimeMillis() / 1000L
        val nameFromAdv = getNameFromAdvData(advertisementData)
        if (nameFromAdv.isNotEmpty()) {
            processName(nameFromAdv)
        }
        processAdvManufacturerData(advertisementData, polarHrAdvertisement)
        if (rssi < 0) {
            processRssi(rssi)
        }
    }

    @VisibleForTesting
    fun processRssi(rssi: Int) {
        this.rssi = rssi
        rssiValues.add(rssi)
        if (rssiValues.size >= 7) {
            sortedRssiValues.clear()
            sortedRssiValues.addAll(rssiValues)
            sortedRssiValues.sort()
            medianRssi = sortedRssiValues[3]
            rssiValues.removeAt(0)
        } else {
            medianRssi = rssi
        }
    }

    @VisibleForTesting
    fun processAdvManufacturerData(advertisementData: HashMap<AD_TYPE, ByteArray>, polarHrAdvertisement: BlePolarHrAdvertisement) {
        var didContainHrData = false
        if (advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC)) {
            val content = advertisementData[AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC]
            if (content != null && content.size > 3 && content[0] == 0x6B.toByte() && content[1] == 0x00.toByte()) {
                var offset = 2
                while (offset < content.size) {
                    when (content[offset].toInt() and 0x40) {
                        0 -> {
                            if (offset + 3 <= content.size) {
                                val subset = Arrays.copyOfRange(content, offset, content.size)
                                polarHrAdvertisement.processPolarManufacturerData(subset)
                                didContainHrData = true
                            }
                            offset += 5
                        }
                        0x40 -> {
                            // gpb data, no handling for now
                            offset += 1
                            if (offset < content.size) offset += (content[offset].toInt() and 0x000000FF) + 1 else offset = content.size
                        }
                        else -> offset = content.size
                    }
                }
            }
        }
        if (!didContainHrData) {
            polarHrAdvertisement.resetToDefault()
        }
    }

    @VisibleForTesting
    fun getNameFromAdvData(advertisementData: HashMap<AD_TYPE, ByteArray>): String {
        if (advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE)) {
            advertisementData[AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE]?.let {
                return String(it)
            }
        } else if (advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT)) {
            advertisementData[AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT]?.let {
                return String(it)
            }
        }
        return ""
    }

    @VisibleForTesting
    fun processName(name: String) {
        if (name.isNotEmpty() && this.name != name) {
            this.name = name
            if (isPolarDevice(name)) {
                polarDeviceType = getPolarModelNameFromAdvLocalName(name)
                val nameSplit = name.split(" ").toTypedArray()
                polarDeviceId = nameSplit[nameSplit.size - 1]
                if (polarDeviceId.length == 7) {
                    polarDeviceId = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(nameSplit[nameSplit.size - 1])
                    this.name = "Polar $polarDeviceType $polarDeviceId"
                }
                polarDeviceIdInt = try {
                    polarDeviceId.toLong(16)
                } catch (ex: NumberFormatException) {
                    // ignore
                    0
                }
            }
        }
    }

    fun resetAdvertisementData() {
        advertisementData.clear()
        polarHrAdvertisement.resetToDefault()
    }

    /**
     * @return current map of advertisement data fields
     */
    fun getAdvertisementData(): Map<AD_TYPE, ByteArray> {
        return HashMap(advertisementData)
    }

    /**
     * @return true if device is in "non" connectable mode
     */
    val isNonConnectableAdvertisement: Boolean
        get() = polarDeviceId.isNotEmpty() &&
                !(advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_MORE) ||
                        advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE))

    /**
     * @param service in hex string format like "180D"
     * @return true if found
     */
    fun containsService(service: String): Boolean {
        if (advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_MORE) ||
            advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE)
        ) {
            val uuids = if (advertisementData.containsKey(AD_TYPE.GAP_ADTYPE_16BIT_MORE)) advertisementData[AD_TYPE.GAP_ADTYPE_16BIT_MORE] else advertisementData[AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE]
            if (uuids != null && uuids.isNotEmpty()) {
                for (i in uuids.indices step 2) {
                    val hexUUid = String.format("%02X%02X", uuids[i + 1], uuids[i])
                    if (hexUUid == service) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
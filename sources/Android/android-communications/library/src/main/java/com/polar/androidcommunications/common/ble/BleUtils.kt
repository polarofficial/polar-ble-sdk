package com.polar.androidcommunications.common.ble

import com.polar.androidcommunications.api.ble.BleLogger.Companion.e
import java.util.Objects

class BleUtils private constructor() {
    init {
        throw IllegalStateException("Utility class")
    }

    enum class AD_TYPE(val numVal: Int) {
        GAP_ADTYPE_UNKNOWN(0),
        GAP_ADTYPE_FLAGS(1),
        GAP_ADTYPE_16BIT_MORE(2),  //!< Service: More 16-bit UUIDs available
        GAP_ADTYPE_16BIT_COMPLETE(3),  //!< Service: Complete list of 16-bit UUIDs
        GAP_ADTYPE_32BIT_MORE(4),  //!< Service: More 32-bit UUIDs available
        GAP_ADTYPE_32BIT_COMPLETE(5),  //!< Service: Complete list of 32-bit UUIDs
        GAP_ADTYPE_128BIT_MORE(6),  //!< Service: More 128-bit UUIDs available
        GAP_ADTYPE_128BIT_COMPLETE(7),  //!< Service: Complete list of 128-bit UUIDs
        GAP_ADTYPE_LOCAL_NAME_SHORT(8),  //!< Shortened local name
        GAP_ADTYPE_LOCAL_NAME_COMPLETE(9),  //!< Complete local name
        GAP_ADTYPE_POWER_LEVEL(10),  //!< TX Power Level: 0xXX: -127 to +127 dBm
        GAP_ADTYPE_OOB_CLASS_OF_DEVICE(11),  //!< Simple Pairing OOB Tag: Class of device (3 octets)
        GAP_ADTYPE_OOB_SIMPLE_PAIRING_HASHC(12),  //!< Simple Pairing OOB Tag: Simple Pairing Hash C (16 octets)
        GAP_ADTYPE_OOB_SIMPLE_PAIRING_RANDR(13),  //!< Simple Pairing OOB Tag: Simple Pairing Randomizer R (16 octets)
        GAP_ADTYPE_SM_TK(14),  //!< Security Manager TK Value
        GAP_ADTYPE_SM_OOB_FLAG(15),  //!< Secutiry Manager OOB Flags
        GAP_ADTYPE_SLAVE_CONN_INTERVAL_RANGE(16),  //!< Min and Max values of the connection interval (2 octets Min, 2 octets Max) (0xFFFF indicates no conn interval min or max)
        GAP_ADTYPE_SIGNED_DATA(17),  //!< Signed Data field
        GAP_ADTYPE_SERVICES_LIST_16BIT(18),  //!< Service Solicitation: list of 16-bit Service UUIDs
        GAP_ADTYPE_SERVICES_LIST_128BIT(19),  //!< Service Solicitation: list of 128-bit Service UUIDs
        GAP_ADTYPE_SERVICE_DATA(20),  //!< Service Data
        GAP_ADTYPE_MANUFACTURER_SPECIFIC(0xFF) //!< Manufacturer Specific Data: first 2 octets contain the Company Identifier Code followed by the additional manufacturer specific data
    }

    enum class EVENT_TYPE(val numVal: Int) {
        ADV_IND(0),  // Connectable undirected advertising
        ADV_DIRECT_IND(1),  // Connectable directed advertising
        ADV_SCAN_IND(2),  // Scannable undirected advertising
        ADV_NONCONN_IND(3),  // Non connectable undirected advertising
        SCAN_RSP(4) // Scan Response
    }

    companion object {
        val TAG: String = BleUtils::class.java.simpleName

        private fun getCode(type: Byte): AD_TYPE {
            return try {
                if (type.toInt() == -1) AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC else AD_TYPE.entries[type.toInt()]
            } catch (ex: ArrayIndexOutOfBoundsException) {
                AD_TYPE.GAP_ADTYPE_UNKNOWN
            }
        }

        fun advertisementBytes2Map(advBytes: ByteArray): HashMap<AD_TYPE, ByteArray> {
            var offset = 0
            val adTypeHashMap = HashMap<AD_TYPE, ByteArray>()
            try {
                while ((offset + 2) < advBytes.size) {
                    val type = getCode(advBytes[offset + 1])
                    val fieldLen = advBytes[offset].toInt()
                    if (fieldLen <= 0) {
                        // skip if incorrect adv is detected
                        break
                    }
                    if (adTypeHashMap.containsKey(type) && type == AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC) {
                        val data = ByteArray(
                            (Objects.requireNonNull(
                                adTypeHashMap[type]
                            )?.size ?: 0) + fieldLen - 1
                        )
                        System.arraycopy(advBytes, offset + 2, data, 0, fieldLen - 1)
                        Objects.requireNonNull(adTypeHashMap[type])?.let {
                            System.arraycopy(
                                Objects.requireNonNull(
                                    adTypeHashMap[type]
                                ), 0, data, fieldLen - 1,
                                it.size
                            )
                        }
                        adTypeHashMap[type] = data
                    } else {
                        val data = ByteArray(fieldLen - 1)
                        System.arraycopy(advBytes, offset + 2, data, 0, fieldLen - 1)
                        adTypeHashMap[type] = data
                    }
                    offset += fieldLen + 1
                }
            } catch (ex: ArrayIndexOutOfBoundsException) {
                e(TAG, "incorrect advertisement data detected: " + ex.localizedMessage)
            }
            return adTypeHashMap
        }

        fun validate(valid: Boolean, message: String?) {
            if (!valid) throw AssertionError(message)
        }
    }
}
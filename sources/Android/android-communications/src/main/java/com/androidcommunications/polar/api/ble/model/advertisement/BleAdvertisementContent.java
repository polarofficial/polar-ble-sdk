package com.androidcommunications.polar.api.ble.model.advertisement;

import com.androidcommunications.polar.api.ble.model.polar.BlePolarDeviceIdUtility;
import com.androidcommunications.polar.common.ble.BleUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class BleAdvertisementContent {
    private HashMap<BleUtils.AD_TYPE, byte[]> advertisementData = new HashMap<>(); // current
    private HashMap<BleUtils.AD_TYPE, byte[]> advertisementDataAll = new HashMap<>(); // current + "history"
    private String name = "";
    private String polarDeviceType = "";
    private long polarDeviceIdInt = 0;
    private String polarDeviceId = "";
    private BlePolarHrAdvertisement polarHrAdvertisement = new BlePolarHrAdvertisement();
    private long advertisetTimeStamp = System.currentTimeMillis() / 1000L;
    private BleUtils.EVENT_TYPE advertisementEventType = BleUtils.EVENT_TYPE.ADV_IND;
    // rssi related
    private List<Integer> rssiValues = new ArrayList<>();
    private List<Integer> sortedRssiValues = new ArrayList<>();
    private int medianRssi = -100;
    private int rssi = -100;

    public void processAdvertisementData(HashMap<BleUtils.AD_TYPE, byte[]> advData,
                                         BleUtils.EVENT_TYPE advertisementEventType,
                                         int rssi) {
        // start access of atomic section
        this.advertisementData.clear();
        this.advertisementData.putAll(advData);
        this.advertisementDataAll.putAll(advData);
        this.advertisementEventType = advertisementEventType;
        this.advertisetTimeStamp = System.currentTimeMillis() / 1000L;
        if (advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE)) {
            String name = new String(Objects.requireNonNull(advertisementData.get(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE)));
            processName(name);
        } else if (advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT)) {
            String name = new String(Objects.requireNonNull(advertisementData.get(BleUtils.AD_TYPE.GAP_ADTYPE_LOCAL_NAME_SHORT)));
            processName(name);
        }
        // manufacturer data
        if (advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC)) {
            byte[] content = advertisementData.get(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC);
            if (Objects.requireNonNull(content).length > 3 && content[0] == 0x6B && content[1] == 0x00) {
                int offset = 2;
                while (offset < content.length) {
                    int gpbDataBit = (content[offset] & 0x40);
                    switch (gpbDataBit) {
                        case 0: {
                            if ((offset + 3) <= content.length) {
                                byte[] subset = Arrays.copyOfRange(content, offset - 2, content.length);
                                polarHrAdvertisement.processPolarManufacturerData(subset);
                            }
                            offset += 5;
                            break;
                        }
                        case 0x40: {
                            // gpb data, no handling for now
                            offset += 1;
                            if (offset < content.length)
                                offset += (content[offset] & 0x000000FF) + 1;
                            else offset = content.length;
                            break;
                        }
                        default:
                            offset = content.length;
                    }
                }
            }
        }
        // rssi
        if (rssi < 0) {
            this.rssi = rssi;
            rssiValues.add(rssi);
            if (rssiValues.size() >= 7) {
                sortedRssiValues.clear();
                sortedRssiValues.addAll(rssiValues);
                Arrays.sort(sortedRssiValues.toArray());
                medianRssi = sortedRssiValues.get(3);
                rssiValues.remove(0);
            } else {
                medianRssi = rssi;
            }
        }
    }

    private void processName(final String name) {
        if (name != null && !this.name.equals(name)) {
            this.name = name;
            if (name.startsWith("Polar ")) {
                String[] names = this.name.split(" ");
                if (names.length > 2) {
                    polarDeviceType = names[1];
                    polarDeviceId = names[names.length - 1];
                    if (polarDeviceId.length() == 7) {
                        polarDeviceId = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(names[names.length - 1]);
                        this.name = "Polar " + polarDeviceType + " " + polarDeviceId;
                    }
                    try {
                        polarDeviceIdInt = Long.parseLong(polarDeviceId, 16);
                    } catch (NumberFormatException ex) {
                        // ignore
                        polarDeviceIdInt = 0;
                    }
                    // stupid Loop 2 format
                    if (names.length == 4) {
                        polarDeviceType += " " + names[2];
                    }
                }
            }
        }
    }

    public void resetAdvertisementData() {
        advertisementData.clear();
        advertisementDataAll.clear();
    }

    /**
     * Get the Device ID contain in GAP local name for Polar Devices.
     *
     * @return String that for "Polar H7 20346EAB" will contain "20346EAB"
     */
    public String getPolarDeviceId() {
        return polarDeviceId;
    }

    /**
     * Get the Device type contain in GAP local name for Polar Devices.
     *
     * @return String that for "Polar H7 20346EAB" will contain "H7"
     */
    public String getPolarDeviceType() {
        return polarDeviceType;
    }

    /**
     * @return Advertised local name <BR>
     */
    public String getName() {
        return name;
    }

    /**
     * @return polar device id in int
     */
    public long getPolarDeviceIdInt() {
        return polarDeviceIdInt;
    }

    /**
     * @return current map of advertisement data fields
     */
    public HashMap<BleUtils.AD_TYPE, byte[]> getAdvertisementData() {
        return new HashMap<>(advertisementData);
    }

    /**
     * @return map of all advertisement data including "history"
     */
    public HashMap<BleUtils.AD_TYPE, byte[]> getAdvertisementDataAll() {
        return new HashMap<>(advertisementDataAll);
    }

    /**
     * @return get current polar manufacturer hr sensor data fields, updated on every advertisement event
     */
    public BlePolarHrAdvertisement getPolarHrAdvertisement() {
        return polarHrAdvertisement;
    }

    /**
     * @return return last advertiset timestamp in unix timestamp
     */
    public long getAdvertisetTimeStamp() {
        return advertisetTimeStamp;
    }

    /**
     * @return median from 7 buffered rssi values
     */
    public int getMedianRssi() {
        return medianRssi;
    }

    /**
     * @return current rssi value
     */
    public int getRssi() {
        return rssi;
    }

    /**
     * @return last received advertisement event type, Note endpoint might not be able to determine the event type <BR>
     */
    public BleUtils.EVENT_TYPE getAdvertisementEventType() {
        return advertisementEventType;
    }

    /**
     * @return true if device is in "non" connectable mode
     */
    public boolean isNonConnectableAdvertisement() {
        return polarDeviceId.length() != 0 &&
                !(advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) ||
                        advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE));
    }

    /**
     * @param service in hex string format like "180D"
     * @return true if found
     */
    public boolean containsService(final String service) {
        if (advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) ||
                advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE)) {
            byte[] uuids = advertisementData.containsKey(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) ?
                    advertisementData.get(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE) :
                    advertisementData.get(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE);
            for (int i = 0; i < Objects.requireNonNull(uuids).length; i += 2) {
                String hexUUid = String.format("%02X%02X", uuids[i + 1], uuids[i]);
                if (hexUUid.equals(service)) {
                    return true;
                }
            }
        }
        return false;
    }
}

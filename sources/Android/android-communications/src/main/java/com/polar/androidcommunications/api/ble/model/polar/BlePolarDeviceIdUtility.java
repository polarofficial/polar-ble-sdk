package com.polar.androidcommunications.api.ble.model.polar;

public final class BlePolarDeviceIdUtility {

    private BlePolarDeviceIdUtility() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isValidDeviceId(final String deviceId) {
        if (deviceId == null) return false;
        if (deviceId.length() == 8) {
            return checkSumForDeviceId(Long.parseLong(deviceId, 16), 8) == (Long.parseLong(deviceId, 16) & 0x000000000000000FL);
        }
        return checkSumForDeviceId(Long.parseLong(deviceId, 16), deviceId.length()) != 0;
    }

    public static String assemblyFullPolarDeviceId(final String deviceId) {
        try {
            switch (deviceId.length()) {
                case 6: {
                    byte crc = checkSumForDeviceId(Long.parseLong(deviceId, 16), 6);
                    return deviceId + "1" + String.format("%01X", crc);
                }
                case 7: {
                    byte crc = checkSumForDeviceId(Long.parseLong(deviceId, 16), 7);
                    return deviceId + String.format("%01X", crc);
                }
                default: {
                    return deviceId;
                }
            }
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static byte checkSumForDeviceId(long deviceId, int width) {
        int siftOffset = 0;
        byte a2 = 0x01;
        switch (width) {
            case 8: {
                a2 = (byte) ((deviceId >> 4) & 0x0F);
                siftOffset = 8;
                break;
            }
            case 7: {
                a2 = (byte) ((deviceId) & 0x0F);
                siftOffset = 4;
                break;
            }
            case 6: {
                break;
            }
        }
        byte a3 = (byte) ((deviceId >> siftOffset) & 0x0F);
        byte a4 = (byte) ((deviceId >> siftOffset + 4) & 0x0F);
        byte a5 = (byte) ((deviceId >> siftOffset + 8) & 0x0F);
        byte a6 = (byte) ((deviceId >> siftOffset + 12) & 0x0F);
        byte a7 = (byte) ((deviceId >> siftOffset + 16) & 0x0F);
        byte a8 = (byte) ((deviceId >> siftOffset + 20) & 0x0F);
        return (byte) ((3 * (a2 + a4 + a6 + a8) + a3 + a5 + a7) % 16);
    }
}

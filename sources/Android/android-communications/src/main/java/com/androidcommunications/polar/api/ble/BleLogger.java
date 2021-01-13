package com.androidcommunications.polar.api.ble;

public class BleLogger {

    public interface BleLoggerInterface {
        void d(final String tag, final String msg);

        void e(final String tag, final String msg);

        void w(final String tag, final String msg);

        void i(final String tag, final String msg);
    }

    public static String byteArrayToHex(final byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a) {
            sb.append(String.format("%02X ", b & 0xff));
        }
        return sb.toString();
    }

    private static final BleLogger instance = new BleLogger();
    private BleLoggerInterface bleLoggerInterface = null;
    private final Object mutex = new Object();

    public static void setLoggerInterface(BleLoggerInterface loggerInterface) {
        synchronized (instance.mutex) {
            instance.bleLoggerInterface = loggerInterface;
        }
    }

    public static void d_hex(final String tag, final String msg, final byte[] data) {
        synchronized (instance.mutex) {
            if (instance.bleLoggerInterface != null) {
                instance.bleLoggerInterface.d(tag, msg + " HEX: " + byteArrayToHex(data));
            }
        }
    }

    public static void d(final String tag, final String msg) {
        synchronized (instance.mutex) {
            if (instance.bleLoggerInterface != null) {
                instance.bleLoggerInterface.d(tag, msg);
            }
        }
    }

    public static void e(String tag, String msg) {
        synchronized (instance.mutex) {
            if (instance.bleLoggerInterface != null) {
                instance.bleLoggerInterface.e(tag, msg);
            }
        }
    }

    public static void w(String tag, String msg) {
        synchronized (instance.mutex) {
            if (instance.bleLoggerInterface != null) {
                instance.bleLoggerInterface.w(tag, msg);
            }
        }
    }

    public static void i(String tag, String msg) {
        synchronized (instance.mutex) {
            if (instance.bleLoggerInterface != null) {
                instance.bleLoggerInterface.i(tag, msg);
            }
        }
    }
}

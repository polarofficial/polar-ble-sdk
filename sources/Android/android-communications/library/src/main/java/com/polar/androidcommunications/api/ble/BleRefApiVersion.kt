package com.polar.androidcommunications.api.ble;

import com.polar.androidcommunications.BuildConfig;

public class BleRefApiVersion {
    // just use simplified latest tag version
    public final static String VERSION_STRING = BuildConfig.GIT_VERSION;

    public static int major() {
        String[] components = BuildConfig.GIT_VERSION.split("\\.");
        if (components.length != 0) {
            return Integer.parseInt(components[0]);
        }
        return 0;
    }

    public static int minor() {
        String[] components = BuildConfig.GIT_VERSION.split("\\.");
        if (components.length > 1) {
            return Integer.parseInt(components[1]);
        }
        return 0;
    }

    public static int patch() {
        String[] components = BuildConfig.GIT_VERSION.split("\\.");
        if (components.length > 2) {
            return Integer.parseInt(components[2]);
        }
        return 0;
    }
}

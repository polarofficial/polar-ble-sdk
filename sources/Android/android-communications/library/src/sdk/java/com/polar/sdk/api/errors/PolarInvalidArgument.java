// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.errors;

/**
 * Invalid argument attempted
 */
public class PolarInvalidArgument extends Exception {
    public PolarInvalidArgument(String s) {
        super(s);
    }

    public PolarInvalidArgument() {
    }
}

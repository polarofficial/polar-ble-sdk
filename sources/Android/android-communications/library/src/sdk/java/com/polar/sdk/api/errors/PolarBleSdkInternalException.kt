// Copyright © 2021 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.errors

/**
 * Polar BLE SDK internal exception indicating something went wrong in SDK internal logic
 */
class PolarBleSdkInternalException(detailMessage: String) : Exception(detailMessage)
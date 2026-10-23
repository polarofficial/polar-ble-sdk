// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.errors

/**
 * Another SDK device-side API call is already in progress.
 */
class PolarSdkConcurrentApiCallException(detailMessage: String) : Exception(detailMessage)

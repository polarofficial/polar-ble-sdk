// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.errors

/**
 * Operation timed out before a result was received
 */
class PolarTimeoutException(detailMessage: String = "") : Exception(detailMessage)

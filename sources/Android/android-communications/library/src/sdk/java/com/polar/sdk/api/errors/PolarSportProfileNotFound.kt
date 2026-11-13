// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.errors

/**
 * No installed sport profile on the device matches the requested sport identifier.
 */
class PolarSportProfileNotFound(detailMessage: String = "") : Exception(detailMessage)

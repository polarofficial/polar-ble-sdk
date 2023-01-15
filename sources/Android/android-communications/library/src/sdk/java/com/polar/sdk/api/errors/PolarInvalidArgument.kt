// Copyright © 2019 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.errors

/**
 * Invalid argument attempted
 */
class PolarInvalidArgument(detailMessage: String = "") : Exception(detailMessage)

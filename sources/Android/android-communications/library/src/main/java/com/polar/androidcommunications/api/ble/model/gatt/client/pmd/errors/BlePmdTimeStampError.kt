package com.polar.androidcommunications.api.ble.exceptions

sealed class BlePmdTimeStampError(detailMessage: String) : Exception(detailMessage)
class TimeStampAndFrequencyZeroError(val detailMessage: String) : BlePmdTimeStampError(detailMessage)
class NegativeTimeStampError(val detailMessage: String) : BlePmdTimeStampError(detailMessage)
object SampleSizeMissingError : BlePmdTimeStampError("")
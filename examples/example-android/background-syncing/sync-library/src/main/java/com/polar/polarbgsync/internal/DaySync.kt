// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import com.polar.polarbgsync.api.SyncResult
import com.polar.polarbgsync.api.SyncableDataType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate

/**
 * Fetches [from]..[to] (inclusive) one day at a time, oldest first.
 *
 * A day counts as completed only when [fetchDay] returns normally (an empty list is a valid,
 * completed "no data" day) and the coroutine is still active afterwards. [onDayCompleted] is then
 * invoked so callers can checkpoint. Failures stop the loop and propagate, so incomplete days are
 * never checkpointed; cancellation is checked before and after each call because some SDK
 * calls swallow it.
 */
internal suspend fun syncDays(
    from: LocalDate,
    to: LocalDate,
    fetchDay: suspend (LocalDate) -> List<SyncResult>,
    onDayCompleted: suspend (LocalDate, List<SyncResult>) -> Unit
): List<SyncResult> {
    val all = mutableListOf<SyncResult>()
    var day = from
    while (!day.isAfter(to)) {
        currentCoroutineContext().ensureActive()
        val results = fetchDay(day)
        currentCoroutineContext().ensureActive()
        all.addAll(results)
        onDayCompleted(day, results)
        day = day.plusDays(1)
    }
    return all
}

/** Thrown after processing all entries when some could not be synced. */
internal class PartialSyncException(message: String, cause: Throwable?) : Exception(message, cause)

/** Next day still to fetch for an interrupted date-range run, and whether earlier days saved data. */
internal data class DayCursor(val nextDay: LocalDate, val hadData: Boolean)

/**
 * Transient (in-session, never persisted) resume cursors per device and data type. They include
 * empty days, so a retry in the same session continues after the last completed day instead of
 * refetching, while the persistent watermark stays untouched until the whole range succeeds.
 */
internal class DayCursors {
    private val cursors = mutableMapOf<Pair<String, SyncableDataType>, DayCursor>()

    @Synchronized fun get(deviceId: String, type: SyncableDataType): DayCursor? = cursors[deviceId to type]
    @Synchronized fun set(deviceId: String, type: SyncableDataType, cursor: DayCursor) { cursors[deviceId to type] = cursor }
    @Synchronized fun clear(deviceId: String, type: SyncableDataType) { cursors.remove(deviceId to type) }
}

/**
 * Runs a date-range sync with per-day fetching, resuming from [cursors] if present.
 * [persistCompleted] (watermark / last-sync date) is called with [to] only after the whole range
 * completed and some data was saved in this or an earlier interrupted run; on any failure the
 * persistent state is left unchanged and the cursor is kept for the retry.
 */
internal suspend fun syncDateRange(
    cursors: DayCursors,
    deviceId: String,
    type: SyncableDataType,
    from: LocalDate,
    to: LocalDate,
    fetchDay: suspend (LocalDate) -> List<SyncResult>,
    onResults: suspend (List<SyncResult>) -> Unit,
    persistCompleted: (LocalDate) -> Unit
): List<SyncResult> {
    val resume = cursors.get(deviceId, type)
    val start = resume?.nextDay?.takeIf { it.isAfter(from) } ?: from
    var hadData = resume?.hadData ?: false
    val all = syncDays(start, to, fetchDay) { day, dayResults ->
        hadData = hadData || dayResults.isNotEmpty()
        cursors.set(deviceId, type, DayCursor(day.plusDays(1), hadData))
        if (dayResults.isNotEmpty()) onResults(dayResults)
    }
    if (hadData) persistCompleted(to)
    cursors.clear(deviceId, type)
    return all
}

/**
 * Data types whose SDK range call lists and downloads every stored file regardless of the date
 * range (the range only filters after download in SDK 8.4.0), so per-day calls would repeat the
 * whole transfer once per day. They are fetched with a single range call instead.
 */
internal fun isSdkRangeScoped(type: SyncableDataType): Boolean =
    type == SyncableDataType.HR_SAMPLES_247 || type == SyncableDataType.PPI_SAMPLES_247

/** One SDK call for the whole range; persistent checkpoints only on success with data saved. */
internal suspend fun syncWholeRange(
    from: LocalDate,
    to: LocalDate,
    fetchRange: suspend (LocalDate, LocalDate) -> List<SyncResult>,
    onResults: suspend (List<SyncResult>) -> Unit,
    persistCompleted: (LocalDate) -> Unit
): List<SyncResult> {
    currentCoroutineContext().ensureActive()
    val results = fetchRange(from, to)
    currentCoroutineContext().ensureActive()
    if (results.isNotEmpty()) {
        onResults(results)
        persistCompleted(to)
    }
    return results
}

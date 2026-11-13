// Copyright © 2025 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import java.time.LocalDateTime

/**
 * Represents a live exercise session on a Polar device.
 */
class PolarExerciseSession {

    /**
     * Convenience names for a small subset of Polar sport identifiers, not a complete
     * list of sports supported by devices. Use numeric sport IDs for other sports.
     */
    enum class SportProfile(val id: Int) {
        UNKNOWN(0),
        RUNNING(1),
        CYCLING(2),
        OTHER_OUTDOOR(16);

        companion object {
            /**
             * Resolve [SportProfile] from integer id.
             * Falls back to [UNKNOWN] if no match is found.
             */
            fun fromId(id: Int): SportProfile = values().find { it.id == id } ?: UNKNOWN
        }
    }

    /**
     * Status of an exercise session.
     */
    enum class ExerciseStatus {
        NOT_STARTED,
        IN_PROGRESS,
        PAUSED,
        STOPPED,
        SYNC_REQUIRED
    }

    /**
     * High-level info of current session state.
     */
    data class ExerciseInfo(
        val status: ExerciseStatus,
        val sportProfile: SportProfile,
        val startTime: LocalDateTime? = null,
        /** Raw sport identifier, including IDs absent from [SportProfile]. Zero if unavailable. */
        val sportId: Long = sportProfile.id.toLong()
    )
}

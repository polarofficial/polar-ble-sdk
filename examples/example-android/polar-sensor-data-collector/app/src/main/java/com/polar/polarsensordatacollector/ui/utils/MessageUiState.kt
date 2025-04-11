package com.polar.polarsensordatacollector.ui.utils

import kotlin.random.Random


/**
 * Helper for message type UI changes
 *
 * [StateFlow] won't change the state if current state is the same as new state.
 * For example if the same message is wanted to show again on view with Snackbar or Toast
 */

class MessageUiState(val header: String, val description: String? = "") {
    private var random = Random.nextInt()
}

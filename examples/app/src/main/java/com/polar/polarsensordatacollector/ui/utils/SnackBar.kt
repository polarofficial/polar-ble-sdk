package com.polar.polarsensordatacollector.ui.utils

import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

fun showSnackBar(
    rootView: View,
    header: String,
    description: String = "",
    showAsError: Boolean = false,
    action: Pair<String, () -> Unit>? = null
) {
    val message = if (showAsError) {
        var errorMessage = "ERROR\n$header"
        if (description.isNotEmpty()) {
            errorMessage += "\n$description"
        }
        errorMessage

    } else {
        "$header\n$description"
    }

    val snackBar = Snackbar.make(rootView, message, Snackbar.LENGTH_INDEFINITE)
    val snackBarView = snackBar.view
    val textView = snackBarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    textView.maxLines = 5

    if (showAsError) {
        snackBar.view.setBackgroundColor(Color.YELLOW)
    }
    if (action != null) {
        snackBar.setAction(action.first) {
            action.second()
            snackBar.dismiss()
        }
    } else {
        snackBar.setAction("OK") {
            snackBar.dismiss()
        }
    }
    snackBar.show()
}
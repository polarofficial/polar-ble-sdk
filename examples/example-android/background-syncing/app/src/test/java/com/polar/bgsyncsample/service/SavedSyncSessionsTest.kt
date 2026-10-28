// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.bgsyncsample.service

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class SavedSyncSessionsTest {
    private val values = mutableMapOf<String, Any>()
    private val prefs = mock(SharedPreferences::class.java)
    private val editor = mock(SharedPreferences.Editor::class.java)

    init {
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Boolean>(1)
            editor
        }
        `when`(editor.putStringSet(anyString(), org.mockito.ArgumentMatchers.anySet<String>())).thenAnswer {
            values[it.getArgument(0)] = it.getArgument<Set<String>>(1).toSet()
            editor
        }
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            values[it.getArgument(0)] ?: it.getArgument<Boolean>(1)
        }
        `when`(prefs.getStringSet(anyString(), org.mockito.ArgumentMatchers.anySet<String>())).thenAnswer {
            values[it.getArgument(0)] ?: it.getArgument<Set<String>>(1)
        }
    }

    @Test
    fun freshInstallDoesNotRestoreUnknownCompanions() {
        assertFalse(SavedSyncSessions(prefs).shouldRestore("B5F8B5C3"))
    }

    @Test
    fun enabledSessionSurvivesProcessRestartAndRejectsUnselectedDevice() {
        SavedSyncSessions(prefs).save(setOf("B5F8B5C3"), true)

        val restored = SavedSyncSessions(prefs)
        assertTrue(restored.shouldRestore("b5f8b5c3"))
        assertFalse(restored.shouldRestore("UNKNOWN"))
    }

    @Test
    fun stopRetainsDevicesButPreventsPresenceRecoveryAndUiAutoStart() {
        val saved = SavedSyncSessions(prefs)
        saved.save(setOf("B5F8B5C3"), true)
        saved.save(setOf("B5F8B5C3"), false)

        val restored = SavedSyncSessions(prefs)
        assertTrue(restored.deviceIds.contains("B5F8B5C3"))
        assertFalse(restored.enabled)
        assertFalse(restored.shouldRestore("B5F8B5C3"))
    }

    @Test
    fun forgettingOneDeviceKeepsOtherEnabledSessions() {
        val saved = SavedSyncSessions(prefs)
        saved.save(setOf("B5F8B5C3", "12345678"), true)
        saved.save(setOf("12345678"), true)

        assertFalse(saved.shouldRestore("B5F8B5C3"))
        assertTrue(saved.shouldRestore("12345678"))
    }

    @Test
    fun forgettingLastDeviceDisablesRecoveryEvenIfEnabledWasRequested() {
        val saved = SavedSyncSessions(prefs)
        saved.save(setOf("B5F8B5C3"), true)
        saved.save(emptySet(), true)

        assertFalse(saved.enabled)
        assertFalse(saved.shouldRestore("B5F8B5C3"))
    }
}

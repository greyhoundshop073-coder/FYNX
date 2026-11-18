package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxGroupPermissionsGuardTest {
    @Test
    fun permissionsRemainDisabledUntilTheServerSnapshotLoads() {
        assertFalse(canEditGroupRemotePermissions(isAdmin = true, hasAccessToken = true, permissionsLoaded = false, permissionsSaving = false))
    }

    @Test
    fun nonAdminsAndTokenlessSessionsCannotChangeRemotePermissions() {
        assertFalse(canEditGroupRemotePermissions(isAdmin = false, hasAccessToken = true, permissionsLoaded = true, permissionsSaving = false))
        assertFalse(canEditGroupRemotePermissions(isAdmin = true, hasAccessToken = false, permissionsLoaded = true, permissionsSaving = false))
    }

    @Test
    fun loadedAdminPermissionsCanBeChangedWhenIdle() {
        assertTrue(canEditGroupRemotePermissions(isAdmin = true, hasAccessToken = true, permissionsLoaded = true, permissionsSaving = false))
    }

    @Test
    fun permissionControlsDisableWhileASaveIsInFlight() {
        assertFalse(canEditGroupRemotePermissions(isAdmin = true, hasAccessToken = true, permissionsLoaded = true, permissionsSaving = true))
    }
}

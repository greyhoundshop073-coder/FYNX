package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxMultiUserAuditRulesTest {
    @Test
    fun hidesOtherProfileSocialActionsWhenViewingOwnProfileFromHomeOrSearch() {
        assertFalse(shouldShowOtherProfileSocialActions("userA", "usera"))
        assertFalse(shouldShowOtherProfileSocialActions("@UserA", "usera"))
        assertTrue(shouldShowOtherProfileSocialActions("userB", "userA"))
        assertTrue(shouldShowOtherProfileSocialActions("userC", null))
    }

    @Test
    fun preventsCallingOwnAccountWhileAllowingPeerCalls() {
        assertTrue(isSelfCallTarget("@UserA", "usera"))
        assertFalse(canCallTarget("@UserA", "usera"))
        assertFalse(canCallTarget("   ", "usera"))
        assertFalse(isSelfCallTarget("@UserB", "usera"))
        assertTrue(canCallTarget("@UserB", "usera"))
        assertTrue(canCallTarget("userC", null))
    }

    @Test
    fun consumesOutgoingCallTriggerOnlyWhenTargetAndFlagArePresent() {
        assertTrue(shouldConsumeOutgoingCallTrigger(initialOutgoing = true, initialName = "userb"))
        assertFalse(shouldConsumeOutgoingCallTrigger(initialOutgoing = false, initialName = "userb"))
        assertFalse(shouldConsumeOutgoingCallTrigger(initialOutgoing = true, initialName = "  "))
        assertFalse(shouldConsumeOutgoingCallTrigger(initialOutgoing = true, initialName = null))
    }

    @Test
    fun resolvesAuthoritativeMediaEndpointForProfileAvatars() {
        assertEquals("/api/media/4219", resolveProfilePhotoMediaPath(" 4219 "))
        assertNull(resolveProfilePhotoMediaPath(null))
        assertNull(resolveProfilePhotoMediaPath("   "))
        assertFalse(resolveProfilePhotoMediaPath("4219")!!.contains("/api/social/media/"))
    }

    @Test
    fun mapsPrivacyStatusVisibilityToComposerAudienceAndRespectsExplicitOverride() {
        assertEquals("EVERYONE", privacyStatusVisibilityToAudienceName("Everyone"))
        assertEquals("FRIENDS", privacyStatusVisibilityToAudienceName("My friends"))
        assertEquals("PRIVATE", privacyStatusVisibilityToAudienceName("Nobody"))
        assertEquals("FRIENDS", effectiveStatusAudienceName("", "My friends"))
        assertEquals("PRIVATE", effectiveStatusAudienceName(null, "Nobody"))
        assertEquals("EVERYONE", effectiveStatusAudienceName("EVERYONE", "Nobody"))
        assertEquals("FRIENDS", effectiveStatusAudienceName("friends", "Everyone"))
    }

    @Test
    fun editProfilePhotoStateReflectsCrossDeviceRemoteAndExplicitRemoval() {
        assertTrue(hasEffectiveEditProfilePhoto(localPhotoUri = null, remotePhotoId = "901", photoRemoved = false))
        assertFalse(hasEffectiveEditProfilePhoto(localPhotoUri = null, remotePhotoId = "901", photoRemoved = true))
        assertTrue(hasEffectiveEditProfilePhoto(localPhotoUri = "content://media/1", remotePhotoId = null, photoRemoved = false))
        assertFalse(hasEffectiveEditProfilePhoto(localPhotoUri = null, remotePhotoId = null, photoRemoved = false))
    }
}

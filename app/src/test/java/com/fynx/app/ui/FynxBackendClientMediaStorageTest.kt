package com.fynx.app.ui

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxBackendClientMediaStorageTest {
    @Test
    fun acceptsOnlyHttpsSignedUrlsOnCloudflareR2Host() {
        assertTrue(FynxBackendClient.isTrustedR2SignedMediaUrl(URL("https://account-id.r2.cloudflarestorage.com/media/object?X-Amz-Signature=abc")))
    }
    @Test
    fun rejectsUnsignedR2Urls() {
        assertFalse(FynxBackendClient.isTrustedR2SignedMediaUrl(URL("https://account-id.r2.cloudflarestorage.com/media/object")))
    }
    @Test
    fun rejectsNonR2HostsEvenWhenTheyContainSignatureQuery() {
        assertFalse(FynxBackendClient.isTrustedR2SignedMediaUrl(URL("https://attacker.example/media/object?X-Amz-Signature=abc")))
    }
    @Test
    fun rejectsHttpR2Urls() {
        assertFalse(FynxBackendClient.isTrustedR2SignedMediaUrl(URL("http://account-id.r2.cloudflarestorage.com/media/object?X-Amz-Signature=abc")))
    }
    @Test
    fun rejectsUserInfoAndUnexpectedPorts() {
        assertFalse(FynxBackendClient.isTrustedR2SignedMediaUrl(URL("https://user@account-id.r2.cloudflarestorage.com/media/object?X-Amz-Signature=abc")))
        assertFalse(FynxBackendClient.isTrustedR2SignedMediaUrl(URL("https://account-id.r2.cloudflarestorage.com:8443/media/object?X-Amz-Signature=abc")))
    }
}

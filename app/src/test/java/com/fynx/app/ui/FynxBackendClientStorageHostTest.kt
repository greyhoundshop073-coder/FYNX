package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxBackendClientStorageHostTest {
    @Test
    fun allowsNeonBranchStorageEndpoint() {
        assertTrue(
            FynxBackendClient.isTrustedObjectStorageHost(
                "br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech"
            )
        )
    }

    @Test
    fun allowsCloudflareR2AccountEndpoint() {
        assertTrue(
            FynxBackendClient.isTrustedObjectStorageHost(
                "fynx-media.0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com"
            )
        )
    }

    @Test
    fun rejectsLookalikeAndUntrustedHosts() {
        listOf(
            "example.com",
            "localhost",
            "br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech.evil.example",
            "br-floral-shape-b5mbajl5.storage.c-7.us-east-2.neon.tech",
            "0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com.evil.example",
            "evil-r2.cloudflarestorage.com"
        ).forEach { host ->
            assertFalse("$host must not be trusted", FynxBackendClient.isTrustedObjectStorageHost(host))
        }
    }
}

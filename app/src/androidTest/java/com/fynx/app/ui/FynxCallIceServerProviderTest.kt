package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FynxCallIceServerProviderTest {
    @Test
    fun parsesOnlyCloudflareStunAndShortLivedTurnConfiguration() {
        val config = FynxCallIceServerProvider.parseResponse(
            """
            {
              "iceServers": [
                { "urls": ["stun:stun.cloudflare.com:3478"] },
                {
                  "urls": [
                    "turn:turn.cloudflare.com:3478?transport=udp",
                    "turns:turn.cloudflare.com:443?transport=tcp",
                    "turn:attacker.example:3478?transport=udp"
                  ],
                  "username": "temporary-user",
                  "credential": "temporary-password"
                }
              ],
              "expiresInSeconds": 3600,
              "refreshAfterSeconds": 2700
            }
            """.trimIndent()
        )

        assertEquals(2, config.servers.size)
        assertEquals(3600L, config.expiresInSeconds)
        assertEquals(2700L, config.refreshAfterSeconds)
    }

    @Test
    fun rejectsConfigurationWithoutACloudflareTurnRelay() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxCallIceServerProvider.parseResponse(
                """
                {
                  "iceServers": [
                    { "urls": ["stun:stun.cloudflare.com:3478"] },
                    {
                      "urls": ["turn:attacker.example:3478?transport=udp"],
                      "username": "temporary-user",
                      "credential": "temporary-password"
                    }
                  ],
                  "expiresInSeconds": 3600,
                  "refreshAfterSeconds": 2700
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun rejectsInvalidCredentialLifetime() {
        assertThrows(IllegalArgumentException::class.java) {
            FynxCallIceServerProvider.parseResponse(
                """
                {
                  "iceServers": [
                    {
                      "urls": ["turn:turn.cloudflare.com:3478?transport=udp"],
                      "username": "temporary-user",
                      "credential": "temporary-password"
                    }
                  ],
                  "expiresInSeconds": 120,
                  "refreshAfterSeconds": 60
                }
                """.trimIndent()
            )
        }
    }
}

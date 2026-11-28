package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Three-participant, backend-independent test of the production FYNX group mesh
 * engine. Three separate engines exchange real SDP and ICE through in-memory
 * signaling callbacks. Every pair must connect and each participant must receive
 * audio tracks from both other participants.
 *
 * This validates the native mesh fallback and roster-to-peer media path. It does
 * not claim Cloudflare SFU, backend membership authorization, TURN relay or FCM.
 */
class FynxGroupCallThreeParticipantLoopbackInstrumentationTest {

    @Test
    fun threeParticipantsConnectEveryMeshEdgeAndReceiveRemoteAudio() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "The emulator must grant microphone permission for the FYNX group-call test."
        }

        val users = listOf("101", "202", "303")
        val engines = ConcurrentHashMap<String, FynxGroupWebRtcCallEngine>()
        val connectedEdges = ConcurrentHashMap.newKeySet<String>()
        val remoteAudioByUser = users.associateWith { ConcurrentHashMap.newKeySet<String>() }
        val errors = ConcurrentLinkedQueue<String>()

        users.forEach { localUserId ->
            val engine = FynxGroupWebRtcCallEngine(
                context,
                FynxGroupWebRtcCallEngine.Callbacks(
                    onOffer = { peerId, sdp ->
                        engines[peerId]?.acceptOfferAndCreateAnswer(localUserId, sdp)
                            ?: errors.add("$localUserId could not route an offer to $peerId")
                    },
                    onAnswer = { peerId, sdp ->
                        engines[peerId]?.applyAnswer(localUserId, sdp)
                            ?: errors.add("$localUserId could not route an answer to $peerId")
                    },
                    onIceCandidate = { peerId, candidate: IceCandidate ->
                        engines[peerId]?.addRemoteIceCandidate(localUserId, candidate)
                            ?: errors.add("$localUserId could not route ICE to $peerId")
                    },
                    onLocalVideoTrack = {},
                    onRemoteVideoTrack = { _, _ -> },
                    onRemoteAudioTrack = { peerId, track ->
                        track.setEnabled(true)
                        remoteAudioByUser.getValue(localUserId).add(peerId)
                    },
                    onPeerState = { peerId, state ->
                        when (state) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED ->
                                connectedEdges.add("$localUserId:$peerId")
                            PeerConnection.IceConnectionState.FAILED ->
                                errors.add("ICE failed for $localUserId to $peerId")
                            else -> Unit
                        }
                    },
                    onError = { errors.add("$localUserId: $it") }
                )
            )
            engines[localUserId] = engine
        }

        try {
            users.forEach { engines.getValue(it).start(video = false) }
            users.forEach { local ->
                users.filter { it != local }.forEach { peer ->
                    assertTrue(
                        "FYNX must create one peer connection for $local to $peer",
                        engines.getValue(local).ensurePeer(peer)
                    )
                }
            }

            // One deterministic offer owner per mesh edge avoids offer glare:
            // 101 -> 202, 101 -> 303, and 202 -> 303.
            for (leftIndex in users.indices) {
                for (rightIndex in leftIndex + 1 until users.size) {
                    engines.getValue(users[leftIndex]).createOffer(users[rightIndex])
                }
            }

            withTimeout(60_000) {
                while (
                    connectedEdges.size < 6 ||
                    users.any { remoteAudioByUser.getValue(it).size < 2 }
                ) {
                    if (errors.isNotEmpty()) {
                        throw AssertionError("Group-call loopback errors: ${errors.joinToString()}")
                    }
                    delay(100)
                }
            }

            users.forEach { local ->
                val received = remoteAudioByUser.getValue(local)
                assertTrue("$local must receive audio from both other participants", received.containsAll(users.filter { it != local }))
                engines.getValue(local).setMicrophoneEnabled(false)
                engines.getValue(local).setMicrophoneEnabled(true)
                engines.getValue(local).setSpeakerEnabled(true)
            }
            assertTrue("All six directed mesh connections must be connected", connectedEdges.size == 6)
            assertTrue("No group media/signaling errors should be reported: ${errors.joinToString()}", errors.isEmpty())
            android.util.Log.i(
                "FYNX_GROUP_CALL_LOOPBACK",
                "PASS: three FYNX participants formed all three peer-to-peer media edges; all six directions reached ICE CONNECTED and each participant received both remote audio tracks"
            )
        } finally {
            engines.values.forEach { runCatching { it.disconnect() } }
        }
        Unit
    }
}

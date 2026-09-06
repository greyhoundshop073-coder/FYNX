package com.fynx.app.ui

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class FynxWebRtcCallEngine(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer> = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(), PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()),
    private val callbacks: CallCallbacks = CallCallbacks()
) : FynxCallMediaEngine {
    data class CallCallbacks(val onOffer:(String)->Unit={},val onAnswer:(String)->Unit={},val onIceCandidate:(IceCandidate)->Unit={},val onRemoteAudioTrack:(AudioTrack)->Unit={},val onRemoteVideoTrack:(VideoTrack)->Unit={},val onConnectionState:(PeerConnection.IceConnectionState)->Unit={},val onError:(String)->Unit={})
    private val appContext=context.applicationContext;private val audioRouter=FynxCallAudioRouter(appContext);private val factory:PeerConnectionFactory
    private var peerConnection:PeerConnection?=null;private var audioSource:AudioSource?=null;private var audioTrack:AudioTrack?=null;private var videoSource:VideoSource?=null;private var videoTrack:VideoTrack?=null;private var cameraCapturer:CameraVideoCapturer?=null;private var surfaceTextureHelper:SurfaceTextureHelper?=null;private val pendingRemoteCandidates=mutableListOf<IceCandidate>();private var remoteDescriptionSet=false
    init{PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions());factory=PeerConnectionFactory.builder().createPeerConnectionFactory()}
    override fun connect(session:FynxCallSession){disconnect();remoteDescriptionSet=false;pendingRemoteCandidates.clear();audioRouter.start(session.type==FynxCallType.VIDEO);peerConnection=factory.createPeerConnection(PeerConnection.RTCConfiguration(iceServers),object:PeerConnection.Observer{override fun onSignalingChange(s:PeerConnection.SignalingState)=Unit;override fun onIceConnectionChange(s:PeerConnection.IceConnectionState){callbacks.onConnectionState(s)};override fun onIceConnectionReceivingChange(r:Boolean)=Unit;override fun onIceGatheringChange(s:PeerConnection.IceGatheringState)=Unit;override fun onIceCandidate(c:IceCandidate){callbacks.onIceCandidate(c)};override fun onIceCandidatesRemoved(c:Array<out IceCandidate>)=Unit;override fun onAddStream(s:MediaStream){s.audioTracks.firstOrNull()?.let(callbacks.onRemoteAudioTrack);s.videoTracks.firstOrNull()?.let(callbacks.onRemoteVideoTrack)};override fun onRemoveStream(s:MediaStream)=Unit;override fun onDataChannel(c:org.webrtc.DataChannel)=Unit;override fun onRenegotiationNeeded()=Unit;override fun onAddTrack(r:org.webrtc.RtpReceiver,m:Array<out MediaStream>){when(val t=r.track()){is AudioTrack->callbacks.onRemoteAudioTrack(t);is VideoTrack->callbacks.onRemoteVideoTrack(t)}}});createLocalAudio();if(session.type==FynxCallType.VIDEO)createLocalVideo()}
    fun createOffer(){val pc=peerConnection?:return callbacks.onError("call media is not connected");pc.createOffer(object:SdpObserverAdapter(){override fun onCreateSuccess(d:SessionDescription){pc.setLocalDescription(object:SdpObserverAdapter(){override fun onSetSuccess(){callbacks.onOffer(d.description)};override fun onSetFailure(e:String){callbacks.onError(e)}},d)};override fun onCreateFailure(e:String){callbacks.onError(e)}},MediaConstraints())}
    fun acceptOfferAndCreateAnswer(sdp:String){val pc=peerConnection?:return callbacks.onError("call media is not connected");pc.setRemoteDescription(object:SdpObserverAdapter(){override fun onSetSuccess(){remoteDescriptionSet=true;flushRemoteCandidates(pc);pc.createAnswer(object:SdpObserverAdapter(){override fun onCreateSuccess(d:SessionDescription){pc.setLocalDescription(object:SdpObserverAdapter(){override fun onSetSuccess(){callbacks.onAnswer(d.description)};override fun onSetFailure(e:String){callbacks.onError(e)}},d)};override fun onCreateFailure(e:String){callbacks.onError(e)}},MediaConstraints())};override fun onSetFailure(e:String){callbacks.onError(e)}},SessionDescription(SessionDescription.Type.OFFER,sdp))}
    fun applyAnswer(sdp:String){val pc=peerConnection?:return callbacks.onError("call media is not connected");pc.setRemoteDescription(object:SdpObserverAdapter(){override fun onSetSuccess(){remoteDescriptionSet=true;flushRemoteCandidates(pc)};override fun onSetFailure(e:String){callbacks.onError(e)}},SessionDescription(SessionDescription.Type.ANSWER,sdp))}
    fun addRemoteIceCandidate(c:IceCandidate){val pc=peerConnection?:return callbacks.onError("call media is not connected");if(!remoteDescriptionSet){pendingRemoteCandidates+=c;return};if(!pc.addIceCandidate(c))callbacks.onError("failed to add remote ICE candidate")}
    private fun flushRemoteCandidates(pc:PeerConnection){val p=pendingRemoteCandidates.toList();pendingRemoteCandidates.clear();p.forEach{if(!pc.addIceCandidate(it))callbacks.onError("failed to add remote ICE candidate")}}
    private fun createLocalAudio(){audioSource=factory.createAudioSource(MediaConstraints());audioTrack=factory.createAudioTrack("fynx-audio",audioSource);audioTrack?.setEnabled(true);audioTrack?.let{peerConnection?.addTrack(it)}}
    private fun createLocalVideo(){val e=Camera2Enumerator(appContext);val n=e.deviceNames.firstOrNull{e.isFrontFacing(it)}?:e.deviceNames.firstOrNull()?:return;cameraCapturer=e.createCapturer(n,null);videoSource=factory.createVideoSource(false);videoTrack=factory.createVideoTrack("fynx-video",videoSource);videoTrack?.setEnabled(true);videoTrack?.let{peerConnection?.addTrack(it)};val egl=org.webrtc.EglBase.create();surfaceTextureHelper=SurfaceTextureHelper.create("FYNX-Camera",egl.eglBaseContext);cameraCapturer?.initialize(surfaceTextureHelper,appContext,videoSource?.capturerObserver);cameraCapturer?.startCapture(1280,720,30);egl.release()}
    override fun setMicrophoneEnabled(enabled:Boolean){audioTrack?.setEnabled(enabled)};override fun setCameraEnabled(enabled:Boolean){videoTrack?.setEnabled(enabled)};override fun switchCamera(){cameraCapturer?.switchCamera(null)};override fun setSpeakerEnabled(enabled:Boolean){audioRouter.setSpeakerEnabled(enabled)}
    override fun disconnect(){runCatching{cameraCapturer?.stopCapture()};cameraCapturer?.dispose();cameraCapturer=null;surfaceTextureHelper?.dispose();surfaceTextureHelper=null;peerConnection?.close();peerConnection?.dispose();peerConnection=null;pendingRemoteCandidates.clear();remoteDescriptionSet=false;audioTrack?.dispose();audioSource?.dispose();videoTrack?.dispose();videoSource?.dispose();audioTrack=null;audioSource=null;videoTrack=null;videoSource=null;audioRouter.stop()}
    private open class SdpObserverAdapter:SdpObserver{override fun onCreateSuccess(d:SessionDescription)=Unit;override fun onSetSuccess()=Unit;override fun onCreateFailure(e:String)=Unit;override fun onSetFailure(e:String)=Unit}
}

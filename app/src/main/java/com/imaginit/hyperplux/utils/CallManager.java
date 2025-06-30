package com.imaginit.hyperplux.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.models.CallData;
import com.imaginit.hyperplux.models.ChatMessage;
import com.imaginit.hyperplux.models.Result;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manager class for audio and video calls using WebRTC
 */
public class CallManager {
    private static final String TAG = "CallManager";
    private static final String CHAT_ROOMS_COLLECTION = "chatRooms";
    private static final String MESSAGES_COLLECTION = "messages";
    private static final String FIREBASE_DB_CALL_PATH = "calls";
    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String LOCAL_STREAM_ID = "ARDAMSl0";
    private static final int MAX_CALL_DURATION_SECONDS = 60 * 60; // 1 hour

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase database;
    private final AnalyticsTracker analyticsTracker;

    // WebRTC fields
    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoCapturer videoCapturer;
    private PeerConnection peerConnection;
    private MediaStream localStream;
    private List<PeerConnection.IceServer> iceServers;
    private DataChannel dataChannel;

    // Call state
    private CallData currentCall;
    private String currentCallId;
    private long callStartTime;
    private boolean isCallActive = false;
    private boolean isVideoEnabled = false;
    private boolean isAudioEnabled = true;
    private boolean isSpeakerEnabled = true;
    private ValueEventListener callEventListener;
    private DatabaseReference callReference;
    private Ringtone ringtone;
    private MediaPlayer disconnectSound;

    // Live data for call status updates
    private final MutableLiveData<CallState> callStateData = new MutableLiveData<>();

    // Singleton instance
    private static CallManager instance;

    /**
     * Get the singleton instance of CallManager
     */
    public static synchronized CallManager getInstance() {
        if (instance == null) {
            instance = new CallManager();
        }
        return instance;
    }

    private CallManager() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        database = FirebaseDatabase.getInstance();
        analyticsTracker = AnalyticsTracker.getInstance();

        // Initialize ice servers (STUN/TURN)
        iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
    }

    /**
     * Initialize WebRTC components
     */
    public void initializeWebRTC(Context context, boolean useCamera) {
        // Initialize EGL context
        eglBase = EglBase.create();

        // Initialize PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions initOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initOptions);

        // Create encoder/decoder factories
        DefaultVideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                eglBase.getEglBaseContext());

        // Create the factory
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        // Create audio source and track
        MediaConstraints audioConstraints = new MediaConstraints();
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);

        // Initialize video if needed
        if (useCamera) {
            initVideoCapture(context);
        }

        // Initialize sound effects
        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(context, ringtoneUri);
        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());

        try {
            disconnectSound = MediaPlayer.create(context, R.raw.call_disconnect);
        } catch (Exception e) {
            Log.e(TAG, "Error loading disconnect sound", e);
        }
    }

    /**
     * Initialize video capture
     */
    private void initVideoCapture(Context context) {
        // Create video capturer
        videoCapturer = createVideoCapturer(context);
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create video capturer");
            return;
        }

        // Create video source and track
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());

        // Start capturing with camera resolution
        videoCapturer.startCapture(1280, 720, 30);

        localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
    }

    /**
     * Create appropriate video capturer based on device capabilities
     */
    private VideoCapturer createVideoCapturer(Context context) {
        if (Camera2Enumerator.isSupported(context)) {
            return createCamera2Capturer(context);
        } else {
            return createCamera1Capturer();
        }
    }

    /**
     * Create Camera2 video capturer (newer devices)
     */
    private VideoCapturer createCamera2Capturer(Context context) {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        return createCameraCapturer(enumerator);
    }

    /**
     * Create Camera1 video capturer (older devices)
     */
    private VideoCapturer createCamera1Capturer() {
        CameraEnumerator enumerator = new Camera1Enumerator(false);
        return createCameraCapturer(enumerator);
    }

    /**
     * Common method to create camera capturer from enumerator
     */
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        // Try to find front-facing camera first
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }

        // If no front facing camera, try back camera
        for (String deviceName : enumerator.getDeviceNames()) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }

        return null;
    }

    /**
     * Set up local and remote video renderers
     * @param localView SurfaceViewRenderer for local video
     * @param remoteView SurfaceViewRenderer for remote video
     */
    public void setupVideoRenderers(SurfaceViewRenderer localView, SurfaceViewRenderer remoteView) {
        if (localView != null) {
            localView.init(eglBase.getEglBaseContext(), null);
            localView.setEnableHardwareScaler(true);
            localView.setMirror(true);

            if (localVideoTrack != null) {
                localVideoTrack.addSink(localView);
            }
        }

        if (remoteView != null) {
            remoteView.init(eglBase.getEglBaseContext(), null);
            remoteView.setEnableHardwareScaler(true);
            remoteView.setMirror(false);
        }
    }

    /**
     * Create a peer connection
     */
    private void createPeerConnection() {
        // Create peer connection configuration
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.enableDtlsSrtp = true;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        // Create peer connection
        peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig, new PeerConnectionObserver());

        // Create local media stream
        localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);

        // Add audio track
        if (localAudioTrack != null) {
            localStream.addTrack(localAudioTrack);
        }

        // Add video track if video call
        if (isVideoEnabled && localVideoTrack != null) {
            localStream.addTrack(localVideoTrack);
        }

        // Add stream to peer connection
        if (peerConnection != null) {
            peerConnection.addStream(localStream);
        }

        // Create data channel for signaling
        if (peerConnection != null) {
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            dataChannel = peerConnection.createDataChannel("dataChannel", init);
            dataChannel.registerObserver(new DataChannelObserver());
        }
    }

    /**
     * Place outgoing call
     * @param chatRoomId ID of the chat room
     * @param recipientId ID of the call recipient
     * @param isVideo Whether this is a video call
     * @return LiveData with the call result
     */
    public LiveData<Result<CallData>> placeCall(String chatRoomId, String recipientId, boolean isVideo) {
        MutableLiveData<Result<CallData>> resultLiveData = new MutableLiveData<>();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            resultLiveData.setValue(new Result.Error<>(new Exception("Not authenticated")));
            return resultLiveData;
        }

        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        // Set up call state
        isVideoEnabled = isVideo;
        isAudioEnabled = true;
        isSpeakerEnabled = isVideo; // Default speaker on for video calls, off for audio calls

        // Generate call ID
        currentCallId = database.getReference(FIREBASE_DB_CALL_PATH).push().getKey();

        // Create call data
        CallData callData = new CallData();
        callData.setCallId(currentCallId);
        callData.setChatRoomId(chatRoomId);
        callData.setCallerId(currentUser.getUid());
        callData.setRecipientId(recipientId);
        callData.setStartTime(new Date());
        callData.setStatus(CallData.STATUS_RINGING);
        callData.setVideo(isVideo);

        // Save current call
        currentCall = callData;

        // Create peer connection
        createPeerConnection();

        // Create the offer
        if (peerConnection != null) {
            MediaConstraints constraints = new MediaConstraints();
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
            constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo ? "true" : "false"));

            peerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    // Set local description
                    peerConnection.setLocalDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                        }

                        @Override
                        public void onSetSuccess() {
                            // Store call data with offer in Firebase
                            callData.setOffer(peerConnection.getLocalDescription().description);
                            DatabaseReference callRef = database.getReference(FIREBASE_DB_CALL_PATH)
                                    .child(currentCallId);
                            callRef.setValue(callData);

                            // Listen for call status changes
                            setupCallListener(currentCallId);

                            // Update chat room with active call
                            updateChatRoomCallStatus(chatRoomId, currentCallId, true);

                            // Save call start time
                            callStartTime = System.currentTimeMillis();

                            // Send call request message
                            sendCallRequestMessage(chatRoomId, isVideo, currentCallId);

                            // Update call state
                            callStateData.postValue(new CallState(CallState.State.CALLING, null));

                            // Return success
                            resultLiveData.setValue(new Result.Success<>(callData));

                            // Track outgoing call
                            Map<String, Object> params = new HashMap<>();
                            params.put("call_type", isVideo ? "video" : "audio");
                            params.put("direction", "outgoing");
                            analyticsTracker.logEvent("call_started", params);
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            endCall(CallData.STATUS_ERROR);
                            resultLiveData.setValue(new Result.Error<>(
                                    new Exception("Failed to set local description: " + s)));
                        }

                        @Override
                        public void onSetFailure(String s) {
                            endCall(CallData.STATUS_ERROR);
                            resultLiveData.setValue(new Result.Error<>(
                                    new Exception("Failed to set local description: " + s)));
                        }
                    }, sessionDescription);
                }

                @Override
                public void onSetSuccess() {
                }

                @Override
                public void onCreateFailure(String s) {
                    endCall(CallData.STATUS_ERROR);
                    resultLiveData.setValue(new Result.Error<>(
                            new Exception("Failed to create offer: " + s)));
                }

                @Override
                public void onSetFailure(String s) {
                }
            }, constraints);
        } else {
            endCall(CallData.STATUS_ERROR);
            resultLiveData.setValue(new Result.Error<>(
                    new Exception("Failed to create peer connection")));
        }

        return resultLiveData;
    }

    /**
     * Answer incoming call
     * @param callId ID of the call to answer
     * @return LiveData with the call result
     */
    public LiveData<Result<CallData>> answerCall(String callId) {
        MutableLiveData<Result<CallData>> resultLiveData = new MutableLiveData<>();

        if (!NetworkMonitor.getInstance().isNetworkAvailable()) {
            resultLiveData.setValue(new Result.Error<>(new Exception("No internet connection")));
            return resultLiveData;
        }

        // Stop ringtone
        if (ringtone.isPlaying()) {
            ringtone.stop();
        }

        // Get call data
        database.getReference(FIREBASE_DB_CALL_PATH).child(callId)
                .get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        CallData callData = task.getResult().getValue(CallData.class);
                        if (callData != null) {
                            // Update call data
                            callData.setStatus(CallData.STATUS_ANSWERED);
                            callData.setAnswerTime(new Date());

                            // Save current call
                            currentCallId = callId;
                            currentCall = callData;
                            isVideoEnabled = callData.isVideo();
                            isAudioEnabled = true;
                            isSpeakerEnabled = callData.isVideo();

                            // Create peer connection
                            createPeerConnection();

                            // Set remote description (offer)
                            if (peerConnection != null && callData.getOffer() != null) {
                                SessionDescription offerSdp = new SessionDescription(
                                        SessionDescription.Type.OFFER, callData.getOffer());

                                peerConnection.setRemoteDescription(new SdpObserver() {
                                    @Override
                                    public void onCreateSuccess(SessionDescription sessionDescription) {
                                    }

                                    @Override
                                    public void onSetSuccess() {
                                        // Create answer
                                        MediaConstraints constraints = new MediaConstraints();
                                        constraints.mandatory.add(
                                                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                                        constraints.mandatory.add(
                                                new MediaConstraints.KeyValuePair("OfferToReceiveVideo",
                                                        callData.isVideo() ? "true" : "false"));

                                        peerConnection.createAnswer(new SdpObserver() {
                                            @Override
                                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                                // Set local description
                                                peerConnection.setLocalDescription(new SdpObserver() {
                                                    @Override
                                                    public void onCreateSuccess(SessionDescription description) {
                                                    }

                                                    @Override
                                                    public void onSetSuccess() {
                                                        // Update call with answer
                                                        callData.setAnswer(
                                                                peerConnection.getLocalDescription().description);
                                                        callData.setStatus(CallData.STATUS_IN_PROGRESS);

                                                        database.getReference(FIREBASE_DB_CALL_PATH)
                                                                .child(callId).setValue(callData);

                                                        // Listen for call status changes
                                                        setupCallListener(callId);

                                                        // Save call start time
                                                        callStartTime = System.currentTimeMillis();

                                                        // Update call state
                                                        callStateData.postValue(
                                                                new CallState(CallState.State.CONNECTED, null));

                                                        // Mark call as active
                                                        isCallActive = true;

                                                        // Return success
                                                        resultLiveData.setValue(new Result.Success<>(callData));

                                                        // Track call answered
                                                        Map<String, Object> params = new HashMap<>();
                                                        params.put("call_type", callData.isVideo() ? "video" : "audio");
                                                        params.put("direction", "incoming");
                                                        analyticsTracker.logEvent("call_answered", params);
                                                    }

                                                    @Override
                                                    public void onCreateFailure(String s) {
                                                        endCall(CallData.STATUS_ERROR);
                                                        resultLiveData.setValue(new Result.Error<>(
                                                                new Exception("Failed to set local description: " + s)));
                                                    }

                                                    @Override
                                                    public void onSetFailure(String s) {
                                                        endCall(CallData.STATUS_ERROR);
                                                        resultLiveData.setValue(new Result.Error<>(
                                                                new Exception("Failed to set local description: " + s)));
                                                    }
                                                }, sessionDescription);
                                            }

                                            @Override
                                            public void onSetSuccess() {
                                            }

                                            @Override
                                            public void onCreateFailure(String s) {
                                                endCall(CallData.STATUS_ERROR);
                                                resultLiveData.setValue(new Result.Error<>(
                                                        new Exception("Failed to create answer: " + s)));
                                            }

                                            @Override
                                            public void onSetFailure(String s) {
                                            }
                                        }, constraints);
                                    }

                                    @Override
                                    public void onCreateFailure(String s) {
                                    }

                                    @Override
                                    public void onSetFailure(String s) {
                                        endCall(CallData.STATUS_ERROR);
                                        resultLiveData.setValue(new Result.Error<>(
                                                new Exception("Failed to set remote description: " + s)));
                                    }
                                }, offerSdp);
                            } else {
                                endCall(CallData.STATUS_ERROR);
                                resultLiveData.setValue(new Result.Error<>(
                                        new Exception("Invalid call data or peer connection")));
                            }
                        } else {
                            resultLiveData.setValue(new Result.Error<>(new Exception("Call not found")));
                        }
                    } else {
                        resultLiveData.setValue(new Result.Error<>(
                                new Exception("Failed to get call data: " +
                                        (task.getException() != null ? task.getException().getMessage() : "Unknown error"))));
                    }
                });

        return resultLiveData;
    }

    /**
     * Decline incoming call
     * @param callId ID of the call to decline
     */
    public void declineCall(String callId) {
        // Stop ringtone
        if (ringtone.isPlaying()) {
            ringtone.stop();
        }

        // Update call status
        database.getReference(FIREBASE_DB_CALL_PATH).child(callId)
                .child("status").setValue(CallData.STATUS_DECLINED);

        // Track call declined
        analyticsTracker.logEvent("call_declined", null);
    }

    /**
     * End the current call
     * @param status The end status (completed, missed, etc.)
     */
    public void endCall(String status) {
        if (currentCallId == null) return;

        // Play disconnect sound
        if (disconnectSound != null) {
            try {
                disconnectSound.start();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error playing disconnect sound", e);
            }
        }

        // Calculate call duration
        long callDuration = 0;
        if (callStartTime > 0) {
            callDuration = System.currentTimeMillis() - callStartTime;
        }

        // Update call data
        if (currentCall != null) {
            currentCall.setStatus(status);
            currentCall.setEndTime(new Date());
            currentCall.setDuration(callDuration);


            // Update call in Firebase
            database.getReference(FIREBASE_DB_CALL_PATH)
                    .child(currentCallId).setValue(currentCall);

            // Update chat room call status
            if (currentCall.getChatRoomId() != null) {
                updateChatRoomCallStatus(currentCall.getChatRoomId(), null, false);

                // Send call info message with duration
                if (status.equals(CallData.STATUS_COMPLETED)) {
                    sendCallInfoMessage(currentCall.getChatRoomId(),
                            currentCall.isVideo(),
                            status,
                            formatCallDuration(callDuration));
                }
            }
        }

        // Stop listening for call updates
        if (callReference != null && callEventListener != null) {
            callReference.removeEventListener(callEventListener);
            callEventListener = null;
            callReference = null;
        }

        // Clean up WebRTC
        cleanupWebRTC();

        // Reset call state
        currentCallId = null;
        currentCall = null;
        callStartTime = 0;
        isCallActive = false;

        // Update UI state
        callStateData.postValue(new CallState(CallState.State.ENDED, null));

        // Track call ended
        Map<String, Object> params = new HashMap<>();
        params.put("status", status);
        if (callDuration > 0) {
            params.put("duration_seconds", TimeUnit.MILLISECONDS.toSeconds(callDuration));
        }
        analyticsTracker.logEvent("call_ended", params);
    }

    /**
     * Set up listener for call status changes
     */
    private void setupCallListener(String callId) {
        callReference = database.getReference(FIREBASE_DB_CALL_PATH).child(callId);
        callEventListener = callReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                CallData callData = snapshot.getValue(CallData.class);
                if (callData == null) return;

                currentCall = callData;

                switch (callData.getStatus()) {
                    case CallData.STATUS_RINGING:
                        callStateData.postValue(new CallState(CallState.State.RINGING, null));
                        break;

                    case CallData.STATUS_ANSWERED:
                        callStateData.postValue(new CallState(CallState.State.CONNECTING, null));

                        // Handle answer SDP if we're the caller
                        if (auth.getCurrentUser() != null &&
                                auth.getCurrentUser().getUid().equals(callData.getCallerId()) &&
                                callData.getAnswer() != null && peerConnection != null) {

                            SessionDescription answerSdp = new SessionDescription(
                                    SessionDescription.Type.ANSWER, callData.getAnswer());

                            peerConnection.setRemoteDescription(new SdpObserver() {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {
                                }

                                @Override
                                public void onSetSuccess() {
                                    // Call connected
                                    callData.setStatus(CallData.STATUS_IN_PROGRESS);
                                    database.getReference(FIREBASE_DB_CALL_PATH)
                                            .child(callId).child("status").setValue(CallData.STATUS_IN_PROGRESS);

                                    // Mark call as active
                                    isCallActive = true;
                                }

                                @Override
                                public void onCreateFailure(String s) {
                                }

                                @Override
                                public void onSetFailure(String s) {
                                    Log.e(TAG, "Failed to set remote description: " + s);
                                    endCall(CallData.STATUS_ERROR);
                                }
                            }, answerSdp);
                        }
                        break;

                    case CallData.STATUS_IN_PROGRESS:
                        callStateData.postValue(new CallState(CallState.State.CONNECTED, null));
                        break;

                    case CallData.STATUS_COMPLETED:
                    case CallData.STATUS_MISSED:
                    case CallData.STATUS_DECLINED:
                    case CallData.STATUS_ERROR:
                        if (callData.getEndTime() == null) {
                            callData.setEndTime(new Date());

                            if (callStartTime > 0) {
                                long duration = System.currentTimeMillis() - callStartTime;
                                callData.setDuration(duration);
                            }

                            database.getReference(FIREBASE_DB_CALL_PATH).child(callId).setValue(callData);
                        }

                        // End the call from our side if it was ended remotely
                        if (isCallActive) {
                            cleanupWebRTC();
                            isCallActive = false;
                            callStateData.postValue(new CallState(CallState.State.ENDED, null));
                        }
                        break;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Call listener cancelled", error.toException());
                endCall(CallData.STATUS_ERROR);
            }
        });
    }

    /**
     * Toggle video on/off during call
     * @return True if video is now enabled, false otherwise
     */
    public boolean toggleVideo() {
        if (localVideoTrack != null) {
            isVideoEnabled = !isVideoEnabled;
            localVideoTrack.setEnabled(isVideoEnabled);

            // Track video toggle
            Map<String, Object> params = new HashMap<>();
            params.put("enabled", isVideoEnabled);
            analyticsTracker.logEvent("call_video_toggled", params);

            return isVideoEnabled;
        }
        return false;
    }

    /**
     * Toggle audio mute during call
     * @return True if audio is now enabled, false otherwise
     */
    public boolean toggleAudio() {
        if (localAudioTrack != null) {
            isAudioEnabled = !isAudioEnabled;
            localAudioTrack.setEnabled(isAudioEnabled);

            // Track audio toggle
            Map<String, Object> params = new HashMap<>();
            params.put("enabled", isAudioEnabled);
            analyticsTracker.logEvent("call_audio_toggled", params);

            return isAudioEnabled;
        }
        return false;
    }

    /**
     * Toggle speaker phone during call
     * @param context Context to get audio manager
     * @return True if speaker is now enabled, false otherwise
     */
    public boolean toggleSpeaker(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            isSpeakerEnabled = !isSpeakerEnabled;
            audioManager.setSpeakerphoneOn(isSpeakerEnabled);

            // Track speaker toggle
            Map<String, Object> params = new HashMap<>();
            params.put("enabled", isSpeakerEnabled);
            analyticsTracker.logEvent("call_speaker_toggled", params);

            return isSpeakerEnabled;
        }
        return false;
    }

    /**
     * Switch camera during video call
     */
    public void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) videoCapturer).switchCamera(null);

            // Track camera switch
            analyticsTracker.logEvent("call_camera_switched", null);
        }
    }

    /**
     * Get current call state updates
     */
    public LiveData<CallState> getCallState() {
        return callStateData;
    }

    /**
     * Check if a call is currently active
     */
    public boolean isCallActive() {
        return isCallActive;
    }

    /**
     * Check if video is currently enabled
     */
    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }

    /**
     * Check if audio is currently enabled
     */
    public boolean isAudioEnabled() {
        return isAudioEnabled;
    }

    /**
     * Check if speaker is currently enabled
     */
    public boolean isSpeakerEnabled() {
        return isSpeakerEnabled;
    }

    /**
     * Play ringtone for incoming call
     */
    public void playRingtone() {
        if (ringtone != null && !ringtone.isPlaying()) {
            ringtone.play();
        }
    }

    /**
     * Stop ringtone
     */
    public void stopRingtone() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    /**
     * Update the chat room with active call information
     */
    private void updateChatRoomCallStatus(String chatRoomId, String callId, boolean hasActiveCall) {
        DocumentReference chatRoomRef = firestore.collection(CHAT_ROOMS_COLLECTION).document(chatRoomId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("hasActiveCall", hasActiveCall);
        updates.put("activeCallId", callId);

        chatRoomRef.update(updates).addOnFailureListener(e ->
                Log.e(TAG, "Error updating chat room call status", e));
    }

    /**
     * Send a call request message to the chat
     */
    private void sendCallRequestMessage(String chatRoomId, boolean isVideo, String callId) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        // Create call request message
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", currentUser.getUid());
        messageData.put("type", ChatMessage.TYPE_CALL_REQUEST);
        messageData.put("timestamp", FieldValue.serverTimestamp());
        messageData.put("read", false);
        messageData.put("videoCall", isVideo);
        messageData.put("callId", callId);
        messageData.put("callStatus", "requesting");

        // Add message to chat room
        firestore.collection(CHAT_ROOMS_COLLECTION)
                .document(chatRoomId)
                .collection(MESSAGES_COLLECTION)
                .add(messageData)
                .addOnFailureListener(e -> Log.e(TAG, "Error sending call request message", e));

        // Update chat room last message and time
        Map<String, Object> chatRoomUpdates = new HashMap<>();
        chatRoomUpdates.put("lastMessage", isVideo ? "📹 Video call" : "📞 Audio call");
        chatRoomUpdates.put("lastMessageTime", new Date());

        firestore.collection(CHAT_ROOMS_COLLECTION)
                .document(chatRoomId)
                .update(chatRoomUpdates)
                .addOnFailureListener(e -> Log.e(TAG, "Error updating chat room", e));
    }

    /**
     * Send a call info message to the chat
     */
    private void sendCallInfoMessage(String chatRoomId, boolean isVideo, String status, String duration) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        // Create call info message
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", currentUser.getUid());
        messageData.put("type", ChatMessage.TYPE_CALL_INFO);
        messageData.put("timestamp", FieldValue.serverTimestamp());
        messageData.put("read", false);
        messageData.put("videoCall", isVideo);
        messageData.put("callStatus", status);
        messageData.put("callDuration", duration);

        // Add message to chat room
        firestore.collection(CHAT_ROOMS_COLLECTION)
                .document(chatRoomId)
                .collection(MESSAGES_COLLECTION)
                .add(messageData)
                .addOnFailureListener(e -> Log.e(TAG, "Error sending call info message", e));

        // Update chat room last message and time
        Map<String, Object> chatRoomUpdates = new HashMap<>();

        String lastMessage;
        if (status.equals(CallData.STATUS_COMPLETED)) {
            lastMessage = (isVideo ? "📹 Video call" : "📞 Audio call") + " • " + duration;
        } else if (status.equals(CallData.STATUS_MISSED)) {
            lastMessage = (isVideo ? "📹 Missed video call" : "📞 Missed audio call");
        } else if (status.equals(CallData.STATUS_DECLINED)) {
            lastMessage = (isVideo ? "📹 Declined video call" : "📞 Declined audio call");
        } else {
            lastMessage = (isVideo ? "📹 Video call ended" : "📞 Audio call ended");
        }

        chatRoomUpdates.put("lastMessage", lastMessage);
        chatRoomUpdates.put("lastMessageTime", new Date());

        firestore.collection(CHAT_ROOMS_COLLECTION)
                .document(chatRoomId)
                .update(chatRoomUpdates)
                .addOnFailureListener(e -> Log.e(TAG, "Error updating chat room", e));
    }

    /**
     * Format call duration into readable string
     */
    private String formatCallDuration(long durationMillis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;
        long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
        }
    }

    /**
     * Clean up WebRTC resources
     */
    private void cleanupWebRTC() {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(false);
        }

        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(false);
        }

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop video capturer", e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }

        if (dataChannel != null) {
            dataChannel.close();
            dataChannel = null;
        }

        if (localStream != null) {
            localStream = null;
        }

        localVideoTrack = null;
        localAudioTrack = null;
    }

    /**
     * Release all resources
     */
    public void release() {
        cleanupWebRTC();

        if (ringtone != null) {
            ringtone.stop();
        }

        if (disconnectSound != null) {
            disconnectSound.release();
            disconnectSound = null;
        }

        if (callReference != null && callEventListener != null) {
            callReference.removeEventListener(callEventListener);
            callEventListener = null;
            callReference = null;
        }

        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
    }

    /**
     * Class for tracking call state changes
     */
    public static class CallState {
        public enum State {
            IDLE,
            CALLING,
            RINGING,
            CONNECTING,
            CONNECTED,
            ENDED
        }

        private final State state;
        private final Exception error;

        public CallState(State state, Exception error) {
            this.state = state;
            this.error = error;
        }

        public State getState() {
            return state;
        }

        public Exception getError() {
            return error;
        }
    }

    /**
     * Observer for peer connection events
     */
    private class PeerConnectionObserver implements PeerConnection.Observer {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);

            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    iceConnectionState == PeerConnection.IceConnectionState.FAILED ||
                    iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {

                if (isCallActive) {
                    callStateData.postValue(new CallState(CallState.State.ENDED,
                            new Exception("Connection lost")));
                    endCall(CallData.STATUS_ERROR);
                }
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.d(TAG, "onIceConnectionReceivingChange: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.d(TAG, "onIceCandidate: " + iceCandidate);

            // In a production app, we would send this to the other peer through signaling
            // For simplicity, we're using Firebase as our signaling mechanism
            if (currentCallId != null) {
                String userId = auth.getCurrentUser().getUid();
                String candidateKey = userId.equals(currentCall.getCallerId()) ?
                        "callerCandidates" : "recipientCandidates";

                Map<String, Object> candidate = new HashMap<>();
                candidate.put("sdpMid", iceCandidate.sdpMid);
                candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                candidate.put("sdp", iceCandidate.sdp);

                database.getReference(FIREBASE_DB_CALL_PATH)
                        .child(currentCallId)
                        .child(candidateKey)
                        .push()
                        .setValue(candidate);
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "onIceCandidatesRemoved: " + iceCandidates.length);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream: " + mediaStream.getId());

            // This is used in older WebRTC implementations
            // We would attach the remote video track to our video view here
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream: " + mediaStream.getId());
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel: " + dataChannel.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(TAG, "onAddTrack");

            // This is called when we receive audio/video tracks from the peer
            // We would attach these to our views in a real implementation
        }
    }

    /**
     * Observer for data channel events
     */
    private class DataChannelObserver implements DataChannel.Observer {
        @Override
        public void onBufferedAmountChange(long l) {
            Log.d(TAG, "onBufferedAmountChange: " + l);
        }

        @Override
        public void onStateChange() {
            Log.d(TAG, "onStateChange: " + dataChannel.state());
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            Log.d(TAG, "onMessage: " + buffer.toString());

            // Handle messages sent through data channel
            // This could be used for additional signaling during the call
        }
    }
}
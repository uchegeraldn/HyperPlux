package com.imaginit.hyperplux.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentVideoCallBinding;
import com.imaginit.hyperplux.models.CallData;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.CallManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fragment for video calling
 */
public class VideoCallFragment extends Fragment {
    private static final String TAG = "VideoCallFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int MICROPHONE_PERMISSION_CODE = 101;

    private FragmentVideoCallBinding binding;
    private CallManager callManager;
    private AnalyticsTracker analyticsTracker;

    private String chatRoomId;
    private String recipientId;
    private String recipientName;
    private String recipientPhoto;
    private String callId;
    private boolean isOutgoing;
    private boolean permissionsGranted = false;

    // Call timer
    private long callStartTimeMillis;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (callStartTimeMillis > 0) {
                long elapsedMillis = System.currentTimeMillis() - callStartTimeMillis;
                binding.callDurationText.setText(formatCallDuration(elapsedMillis));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentVideoCallBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Keep screen on during call
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Initialize managers
            callManager = CallManager.getInstance();
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Get call parameters from arguments
            extractCallParameters();

            // Validate call parameters
            if (chatRoomId == null || recipientId == null) {
                Toast.makeText(requireContext(), "Missing call information", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(view).popBackStack();
                return;
            }

            // Setup UI
            setupUI();

            // Set up call controls
            setupCallControls();

            // Set up incoming/outgoing call UI
            setupCallMode();

            // Observe call state changes
            observeCallState();

            // Track screen view
            trackScreenView();
        } catch (Exception e) {
            Log.e(TAG, "Error in onViewCreated: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing video call", Toast.LENGTH_SHORT).show();
            navigateBack();
        }
    }

    private void extractCallParameters() {
        try {
            if (getArguments() != null) {
                chatRoomId = getArguments().getString("chatRoomId");
                recipientId = getArguments().getString("recipientId");
                recipientName = getArguments().getString("recipientName");
                recipientPhoto = getArguments().getString("recipientPhoto");
                callId = getArguments().getString("callId");
                isOutgoing = getArguments().getBoolean("isOutgoing", true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting call parameters: " + e.getMessage());
        }
    }

    private void setupUI() {
        try {
            binding.callerNameText.setText(recipientName != null ? recipientName : "Unknown User");

            if (recipientPhoto != null && !recipientPhoto.isEmpty()) {
                Glide.with(requireContext())
                        .load(recipientPhoto)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_image_placeholder))
                        .circleCrop()
                        .into(binding.callerImage);
            } else {
                binding.callerImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            binding.callStatusText.setText(isOutgoing ? R.string.calling : R.string.incoming_call);
            binding.callTypeIcon.setImageResource(R.drawable.ic_videocam);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up UI: " + e.getMessage());
        }
    }

    private void setupCallMode() {
        try {
            if (isOutgoing) {
                binding.incomingCallControls.setVisibility(View.GONE);
                binding.callControls.setVisibility(View.VISIBLE);

                // Check permissions and start call
                checkPermissionsAndStartCall();
            } else {
                binding.incomingCallControls.setVisibility(View.VISIBLE);
                binding.callControls.setVisibility(View.GONE);

                // Play ringtone for incoming call
                if (callManager != null) {
                    callManager.playRingtone();
                }

                // Set up incoming call buttons
                binding.answerButton.setOnClickListener(v -> {
                    try {
                        performHapticFeedback(v);
                        checkPermissionsAndAnswerCall();
                    } catch (Exception e) {
                        Log.e(TAG, "Error answering call: " + e.getMessage());
                    }
                });

                binding.declineButton.setOnClickListener(v -> {
                    try {
                        performHapticFeedback(v);
                        if (callManager != null && callId != null) {
                            callManager.declineCall(callId);
                        }
                        navigateBack();
                    } catch (Exception e) {
                        Log.e(TAG, "Error declining call: " + e.getMessage());
                        navigateBack();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up call mode: " + e.getMessage());
        }
    }

    private void setupCallControls() {
        try {
            binding.endCallButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (callManager != null) {
                        callManager.endCall(CallData.CallStatus.COMPLETED.getValue());
                    }
                    navigateBack();
                } catch (Exception e) {
                    Log.e(TAG, "Error ending call: " + e.getMessage());
                    navigateBack();
                }
            });

            binding.toggleVideoButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (callManager != null) {
                        boolean enabled = callManager.toggleVideo();
                        binding.toggleVideoButton.setImageResource(
                                enabled ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);

                        // Update local video preview visibility
                        binding.localVideoView.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling video: " + e.getMessage());
                }
            });

            binding.toggleMicButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (callManager != null) {
                        boolean enabled = callManager.toggleAudio();
                        binding.toggleMicButton.setImageResource(
                                enabled ? R.drawable.ic_mic : R.drawable.ic_mic_off);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling mic: " + e.getMessage());
                }
            });

            binding.toggleSpeakerButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (callManager != null) {
                        boolean enabled = callManager.toggleSpeaker(requireContext());
                        binding.toggleSpeakerButton.setImageResource(
                                enabled ? R.drawable.ic_volume_up : R.drawable.ic_volume_down);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error toggling speaker: " + e.getMessage());
                }
            });

            binding.switchCameraButton.setOnClickListener(v -> {
                try {
                    performHapticFeedback(v);
                    if (callManager != null) {
                        callManager.switchCamera();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error switching camera: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up call controls: " + e.getMessage());
        }
    }

    private void observeCallState() {
        try {
            if (callManager != null) {
                callManager.getCallState().observe(getViewLifecycleOwner(), callState -> {
                    try {
                        if (callState == null) return;

                        switch (callState.getState()) {
                            case IDLE:
                                break;

                            case CALLING:
                                binding.callStatusText.setText(R.string.calling);
                                binding.callStatusContainer.setVisibility(View.VISIBLE);
                                break;

                            case RINGING:
                                binding.callStatusText.setText(R.string.ringing);
                                binding.callStatusContainer.setVisibility(View.VISIBLE);
                                break;

                            case CONNECTING:
                                binding.callStatusText.setText(R.string.connecting);
                                binding.callStatusContainer.setVisibility(View.VISIBLE);
                                break;

                            case CONNECTED:
                                binding.callStatusContainer.setVisibility(View.GONE);
                                binding.callControls.setVisibility(View.VISIBLE);
                                binding.incomingCallControls.setVisibility(View.GONE);
                                binding.callDurationText.setVisibility(View.VISIBLE);

                                // Start call timer
                                startCallTimer();
                                break;

                            case ENDED:
                                // Exit call screen
                                stopCallTimer();
                                navigateBack();
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling call state change: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error observing call state: " + e.getMessage());
        }
    }

    private void trackScreenView() {
        try {
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("is_video", true);
                params.put("is_outgoing", isOutgoing);
                analyticsTracker.trackScreenView("VideoCall", "VideoCallFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error tracking screen view: " + e.getMessage());
        }
    }

    private void checkPermissionsAndStartCall() {
        try {
            String[] requiredPermissions = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            };

            if (hasPermissions(requiredPermissions)) {
                permissionsGranted = true;
                startCall();
            } else {
                requestPermissions(requiredPermissions, CAMERA_PERMISSION_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions: " + e.getMessage());
            navigateBack();
        }
    }

    private void checkPermissionsAndAnswerCall() {
        try {
            String[] requiredPermissions = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            };

            if (hasPermissions(requiredPermissions)) {
                permissionsGranted = true;
                answerCall();
            } else {
                requestPermissions(requiredPermissions, MICROPHONE_PERMISSION_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions: " + e.getMessage());
            navigateBack();
        }
    }

    private boolean hasPermissions(String[] permissions) {
        try {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions: " + e.getMessage());
            return false;
        }
    }

    private void requestPermissions(String[] permissions, int requestCode) {
        try {
            ActivityCompat.requestPermissions(requireActivity(), permissions, requestCode);
            Toast.makeText(requireContext(), R.string.camera_microphone_permission_rationale, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error requesting permissions: " + e.getMessage());
            navigateBack();
        }
    }

    private void startCall() {
        try {
            if (callManager == null) return;

            // Initialize WebRTC
            callManager.initializeWebRTC(requireContext(), true);

            // Set up video renderers
            callManager.setupVideoRenderers(binding.localVideoView, binding.remoteVideoView);

            // Set audio mode
            setAudioMode(true);

            // Place the call (modified to work without Result wrapper)
            callManager.placeCall(chatRoomId, recipientId, true).observe(getViewLifecycleOwner(), callData -> {
                if (callData == null) {
                    Toast.makeText(requireContext(), "Failed to start call", Toast.LENGTH_SHORT).show();
                    navigateBack();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error starting call: " + e.getMessage());
            Toast.makeText(requireContext(), "Error starting call", Toast.LENGTH_SHORT).show();
            navigateBack();
        }
    }

    private void answerCall() {
        try {
            if (callManager == null || callId == null) return;

            // Initialize WebRTC
            callManager.initializeWebRTC(requireContext(), true);

            // Set up video renderers
            callManager.setupVideoRenderers(binding.localVideoView, binding.remoteVideoView);

            // Set audio mode
            setAudioMode(true);

            // Answer the call (modified to work without Result wrapper)
            callManager.answerCall(callId).observe(getViewLifecycleOwner(), callData -> {
                if (callData == null) {
                    Toast.makeText(requireContext(), "Failed to answer call", Toast.LENGTH_SHORT).show();
                    navigateBack();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error answering call: " + e.getMessage());
            Toast.makeText(requireContext(), "Error answering call", Toast.LENGTH_SHORT).show();
            navigateBack();
        }
    }

    private void setAudioMode(boolean speakerOn) {
        try {
            AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(speakerOn);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting audio mode: " + e.getMessage());
        }
    }

    private void startCallTimer() {
        try {
            callStartTimeMillis = System.currentTimeMillis();
            binding.callDurationText.setText("00:00");
            timerHandler.postDelayed(timerRunnable, 1000);
        } catch (Exception e) {
            Log.e(TAG, "Error starting call timer: " + e.getMessage());
        }
    }

    private void stopCallTimer() {
        try {
            timerHandler.removeCallbacks(timerRunnable);
            callStartTimeMillis = 0;
        } catch (Exception e) {
            Log.e(TAG, "Error stopping call timer: " + e.getMessage());
        }
    }

    private String formatCallDuration(long durationMillis) {
        try {
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis) % 60;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis) % 60;
            long hours = TimeUnit.MILLISECONDS.toHours(durationMillis);

            if (hours > 0) {
                return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error formatting call duration: " + e.getMessage());
            return "00:00";
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void navigateBack() {
        try {
            Navigation.findNavController(binding.getRoot()).popBackStack();
        } catch (Exception e) {
            Log.e(TAG, "Error navigating back: " + e.getMessage());
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        try {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                permissionsGranted = true;

                if (requestCode == CAMERA_PERMISSION_CODE) {
                    startCall();
                } else if (requestCode == MICROPHONE_PERMISSION_CODE) {
                    answerCall();
                }
            } else {
                Toast.makeText(requireContext(), R.string.permissions_required, Toast.LENGTH_SHORT).show();
                navigateBack();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling permission result: " + e.getMessage());
            navigateBack();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            // If we're pausing without permissions granted, end the call (user denied permissions)
            if (!permissionsGranted && isOutgoing && callManager != null) {
                callManager.endCall(CallData.CallStatus.ERROR.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        try {
            // Stop timer
            stopCallTimer();

            // Remove screen on flag
            if (getActivity() != null) {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            // Reset audio mode
            setAudioMode(false);
            AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroyView: " + e.getMessage());
        }

        binding = null;
    }
}
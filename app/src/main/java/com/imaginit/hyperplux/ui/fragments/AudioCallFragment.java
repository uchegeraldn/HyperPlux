package com.imaginit.hyperplux.ui.fragments;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.imaginit.hyperplux.databinding.FragmentAudioCallBinding;
import com.imaginit.hyperplux.models.CallData;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.CallManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Fragment for audio calling
 */
public class AudioCallFragment extends Fragment {
    private static final String TAG = "AudioCallFragment";
    private static final int MICROPHONE_PERMISSION_CODE = 101;

    private FragmentAudioCallBinding binding;
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
        binding = FragmentAudioCallBinding.inflate(inflater, container, false);

        try {
            // Keep screen on during call
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            // Log error but continue
            android.util.Log.e(TAG, "Error setting screen flags: " + e.getMessage());
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            callManager = CallManager.getInstance();
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Get call parameters from arguments
            if (getArguments() != null) {
                chatRoomId = getArguments().getString("chatRoomId");
                recipientId = getArguments().getString("recipientId");
                recipientName = getArguments().getString("recipientName");
                recipientPhoto = getArguments().getString("recipientPhoto");
                callId = getArguments().getString("callId");
                isOutgoing = getArguments().getBoolean("isOutgoing", true);
            }

            if (chatRoomId == null || recipientId == null) {
                Toast.makeText(requireContext(), "Missing call information", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(view).popBackStack();
                return;
            }

            // Setup UI
            binding.callerNameText.setText(recipientName != null ? recipientName : "Unknown User");

            if (recipientPhoto != null && !recipientPhoto.isEmpty()) {
                Glide.with(requireContext())
                        .load(recipientPhoto)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person))
                        .circleCrop()
                        .into(binding.callerImage);
            } else {
                binding.callerImage.setImageResource(R.drawable.ic_person);
            }

            binding.callStatusText.setText(isOutgoing ? R.string.calling : R.string.incoming_call);
            binding.callTypeIcon.setImageResource(R.drawable.ic_phone);

            // Set up call controls
            setupCallControls();

            // Set up incoming/outgoing call UI
            if (isOutgoing) {
                binding.incomingCallControls.setVisibility(View.GONE);
                binding.callControls.setVisibility(View.VISIBLE);

                // Check permissions and start call
                checkPermissionsAndStartCall();
            } else {
                binding.incomingCallControls.setVisibility(View.VISIBLE);
                binding.callControls.setVisibility(View.GONE);

                // Play ringtone for incoming call
                callManager.playRingtone();

                // Set up incoming call buttons
                binding.answerButton.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    checkPermissionsAndAnswerCall();
                });

                binding.declineButton.setOnClickListener(v -> {
                    performHapticFeedback(v);
                    callManager.declineCall(callId);
                    Navigation.findNavController(v).popBackStack();
                });
            }

            // Observe call state changes
            callManager.getCallState().observe(getViewLifecycleOwner(), callState -> {
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
                        // Stop timer
                        stopCallTimer();

                        // Exit call screen
                        Navigation.findNavController(binding.getRoot()).popBackStack();
                        break;
                }
            });

            // Track screen view
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("is_video", false);
                params.put("is_outgoing", isOutgoing);
                analyticsTracker.trackScreenView("AudioCall", params);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error initializing call screen: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Navigation.findNavController(view).popBackStack();
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void setupCallControls() {
        binding.endCallButton.setOnClickListener(v -> {
            performHapticFeedback(v);
            try {
                callManager.endCall(CallData.CallStatus.COMPLETED.getValue());
                Navigation.findNavController(v).popBackStack();
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error ending call: " + e.getMessage());
                Navigation.findNavController(v).popBackStack();
            }
        });

        binding.toggleMicButton.setOnClickListener(v -> {
            performHapticFeedback(v);
            try {
                boolean enabled = callManager.toggleAudio();
                binding.toggleMicButton.setImageResource(
                        enabled ? R.drawable.ic_mic : R.drawable.ic_mic_off);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error toggling microphone: " + e.getMessage());
            }
        });

        binding.toggleSpeakerButton.setOnClickListener(v -> {
            performHapticFeedback(v);
            try {
                boolean enabled = callManager.toggleSpeaker(requireContext());
                binding.toggleSpeakerButton.setImageResource(
                        enabled ? R.drawable.ic_volume_up : R.drawable.ic_volume_down);
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error toggling speaker: " + e.getMessage());
            }
        });
    }

    private void checkPermissionsAndStartCall() {
        String[] requiredPermissions = {
                android.Manifest.permission.RECORD_AUDIO
        };

        if (hasPermissions(requiredPermissions)) {
            permissionsGranted = true;
            startCall();
        } else {
            requestPermissions(requiredPermissions, MICROPHONE_PERMISSION_CODE);
        }
    }

    private void checkPermissionsAndAnswerCall() {
        String[] requiredPermissions = {
                android.Manifest.permission.RECORD_AUDIO
        };

        if (hasPermissions(requiredPermissions)) {
            permissionsGranted = true;
            answerCall();
        } else {
            requestPermissions(requiredPermissions, MICROPHONE_PERMISSION_CODE);
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
            android.util.Log.e(TAG, "Error checking permissions: " + e.getMessage());
            return false;
        }
    }

    private void startCall() {
        try {
            // Initialize WebRTC
            callManager.initializeWebRTC(requireContext(), false);

            // Set audio mode
            AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(false);
            }

            // Place the call
            callManager.placeCall(chatRoomId, recipientId, false).observe(getViewLifecycleOwner(), callData -> {
                if (callData == null) {
                    Toast.makeText(requireContext(), "Failed to start call", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(binding.getRoot()).popBackStack();
                }
            });
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error starting call: " + e.getMessage());
            Toast.makeText(requireContext(), "Error starting call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).popBackStack();
        }
    }

    private void answerCall() {
        try {
            // Initialize WebRTC
            callManager.initializeWebRTC(requireContext(), false);

            // Set audio mode
            AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setSpeakerphoneOn(false);
            }

            // Answer the call
            callManager.answerCall(callId).observe(getViewLifecycleOwner(), callData -> {
                if (callData == null) {
                    Toast.makeText(requireContext(), "Failed to answer call", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(binding.getRoot()).popBackStack();
                }
            });
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error answering call: " + e.getMessage());
            Toast.makeText(requireContext(), "Error answering call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).popBackStack();
        }
    }

    private void startCallTimer() {
        callStartTimeMillis = System.currentTimeMillis();
        binding.callDurationText.setText("00:00");
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopCallTimer() {
        timerHandler.removeCallbacks(timerRunnable);
        callStartTimeMillis = 0;
    }

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            permissionsGranted = true;

            if (requestCode == MICROPHONE_PERMISSION_CODE) {
                if (isOutgoing) {
                    startCall();
                } else {
                    answerCall();
                }
            }
        } else {
            Toast.makeText(requireContext(), R.string.permissions_required, Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).popBackStack();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // If we're pausing without permissions granted, end the call (user denied permissions)
        if (!permissionsGranted && isOutgoing) {
            try {
                callManager.endCall(CallData.CallStatus.ERROR.getValue());
            } catch (Exception e) {
                android.util.Log.e(TAG, "Error ending call: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        try {
            // Stop timer
            stopCallTimer();

            // Remove screen on flag
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Reset audio mode
            AudioManager audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(false);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error in onDestroyView: " + e.getMessage());
        }

        binding = null;
    }
}
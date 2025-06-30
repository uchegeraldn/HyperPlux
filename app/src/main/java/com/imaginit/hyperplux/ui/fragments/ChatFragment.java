package com.imaginit.hyperplux.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.imaginit.hyperplux.R;
import com.imaginit.hyperplux.databinding.FragmentChatBinding;
import com.imaginit.hyperplux.models.ChatMessage;
import com.imaginit.hyperplux.models.ChatRoom;
import com.imaginit.hyperplux.ui.adapters.ChatMessageAdapter;
import com.imaginit.hyperplux.utils.AnalyticsTracker;
import com.imaginit.hyperplux.utils.NetworkMonitor;
import com.imaginit.hyperplux.viewmodels.ChatViewModel;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment for chat conversation
 */
public class ChatFragment extends Fragment {
    private static final String TAG = "ChatFragment";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_GALLERY_IMAGE = 2;
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;

    private FragmentChatBinding binding;
    private ChatViewModel viewModel;
    private ChatMessageAdapter adapter;
    private String currentPhotoPath;
    private AnalyticsTracker analyticsTracker;
    private ChatRoom chatRoom;
    private String currentUserId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Initialize ViewModel
            viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

            // Initialize analytics tracker
            analyticsTracker = AnalyticsTracker.getInstance(requireContext());

            // Get current user safely
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show();
                Navigation.findNavController(view).popBackStack();
                return;
            }

            currentUserId = currentUser.getUid();

            // Get chat room from arguments
            if (getArguments() != null && getArguments().containsKey("chatRoomId")) {
                String chatRoomId = getArguments().getString("chatRoomId");
                if (chatRoomId == null || chatRoomId.isEmpty()) {
                    throw new IllegalArgumentException("Invalid chat room ID");
                }

                viewModel.getChatRoom(chatRoomId).observe(getViewLifecycleOwner(), chatRoomResult -> {
                    if (chatRoomResult != null) {
                        chatRoom = chatRoomResult;
                        setupUI();
                    } else {
                        Toast.makeText(requireContext(), "Error loading chat", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(view).popBackStack();
                    }
                });
            } else {
                throw new IllegalArgumentException("Chat room ID not provided");
            }

            // Track screen view
            if (analyticsTracker != null) {
                analyticsTracker.trackScreenView("Chat", "ChatFragment");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing chat: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initializing chat: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Navigation.findNavController(view).popBackStack();
        }
    }

    private void setupUI() {
        try {
            // Set up toolbar
            String otherUserName = chatRoom.getOtherParticipantName(currentUserId);
            binding.toolbarTitle.setText(otherUserName != null ? otherUserName : "Chat");

            String otherUserPhoto = chatRoom.getOtherParticipantPhoto(currentUserId);
            if (otherUserPhoto != null && !otherUserPhoto.isEmpty()) {
                // Load profile photo using Glide
                Glide.with(requireContext())
                        .load(otherUserPhoto)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person))
                        .circleCrop()
                        .into(binding.userProfileImage);
            } else {
                binding.userProfileImage.setImageResource(R.drawable.ic_person);
            }

            binding.backButton.setOnClickListener(v -> {
                performHapticFeedback(v);
                Navigation.findNavController(v).popBackStack();
            });

            binding.audioCallButton.setOnClickListener(v -> {
                performHapticFeedback(v);
                initiateCall(false);
            });

            binding.videoCallButton.setOnClickListener(v -> {
                performHapticFeedback(v);
                initiateCall(true);
            });

            // Set up message list
            adapter = new ChatMessageAdapter(currentUserId, chatRoom.getId(), viewModel, getViewLifecycleOwner());
            binding.messagesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.messagesRecyclerView.setAdapter(adapter);

            // Load messages
            viewModel.getMessages(chatRoom.getId()).observe(getViewLifecycleOwner(), messages -> {
                if (messages != null) {
                    adapter.submitList(messages);
                    if (!messages.isEmpty()) {
                        binding.messagesRecyclerView.scrollToPosition(messages.size() - 1);
                        binding.emptyStateContainer.setVisibility(View.GONE);
                    } else {
                        binding.emptyStateContainer.setVisibility(View.VISIBLE);
                    }
                } else {
                    adapter.submitList(new ArrayList<>());
                    binding.emptyStateContainer.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), "Error loading messages", Toast.LENGTH_SHORT).show();
                }
            });

            // Set up message input
            binding.messageInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Show/hide send button based on text
                    boolean hasText = s.toString().trim().length() > 0;
                    binding.sendButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
                    binding.attachmentButton.setVisibility(hasText ? View.GONE : View.VISIBLE);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            binding.sendButton.setOnClickListener(v -> {
                performHapticFeedback(v);
                sendTextMessage();
            });

            binding.attachmentButton.setOnClickListener(v -> {
                performHapticFeedback(v);
                showAttachmentOptions();
            });

            // Monitor network state
            NetworkMonitor networkMonitor = NetworkMonitor.getInstance(requireContext());
            if (networkMonitor != null) {
                networkMonitor.getNetworkAvailability().observe(getViewLifecycleOwner(), isConnected -> {
                    binding.offlineIndicator.setVisibility(isConnected ? View.GONE : View.VISIBLE);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up UI: " + e.getMessage());
            Toast.makeText(requireContext(), "Error setting up chat UI", Toast.LENGTH_SHORT).show();
        }
    }

    private void performHapticFeedback(View view) {
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            // Ignore haptic feedback errors
        }
    }

    private void sendTextMessage() {
        try {
            String text = binding.messageInput.getText().toString().trim();
            if (text.isEmpty()) return;

            NetworkMonitor networkMonitor = NetworkMonitor.getInstance(requireContext());
            if (networkMonitor != null && !networkMonitor.isNetworkAvailable()) {
                Toast.makeText(requireContext(), R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.sendTextMessage(chatRoom.getId(), text).observe(getViewLifecycleOwner(), message -> {
                if (message != null) {
                    binding.messageInput.setText("");
                    performHapticFeedback(binding.getRoot());
                } else {
                    Toast.makeText(requireContext(), "Error sending message", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error sending message: " + e.getMessage());
            Toast.makeText(requireContext(), "Error sending message", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAttachmentOptions() {
        try {
            binding.attachmentOptionsContainer.setVisibility(
                    binding.attachmentOptionsContainer.getVisibility() == View.VISIBLE ?
                            View.GONE : View.VISIBLE);

            binding.cameraOption.setOnClickListener(v -> {
                performHapticFeedback(v);
                binding.attachmentOptionsContainer.setVisibility(View.GONE);
                checkCameraPermissionAndDispatch();
            });

            binding.galleryOption.setOnClickListener(v -> {
                performHapticFeedback(v);
                binding.attachmentOptionsContainer.setVisibility(View.GONE);
                checkStoragePermissionAndOpenGallery();
            });

            binding.assetOption.setOnClickListener(v -> {
                performHapticFeedback(v);
                binding.attachmentOptionsContainer.setVisibility(View.GONE);

                // Navigate to asset selection screen
                try {
                    Bundle args = new Bundle();
                    args.putString("chatRoomId", chatRoom.getId());
                    Navigation.findNavController(v)
                            .navigate(R.id.action_chatFragment_to_assetSelectionFragment, args);
                } catch (Exception e) {
                    Log.e(TAG, "Error navigating to asset selection: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error opening asset selection", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error showing attachment options: " + e.getMessage());
        }
    }

    private void checkCameraPermissionAndDispatch() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(), android.Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                requestPermissions(
                        new String[]{android.Manifest.permission.CAMERA},
                        CAMERA_PERMISSION_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking camera permission: " + e.getMessage());
            Toast.makeText(requireContext(), "Error accessing camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkStoragePermissionAndOpenGallery() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                requestPermissions(
                        new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                        STORAGE_PERMISSION_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage permission: " + e.getMessage());
            Toast.makeText(requireContext(), "Error accessing storage", Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchTakePictureIntent() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                // Create the file where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    Toast.makeText(requireContext(), R.string.error_creating_file, Toast.LENGTH_SHORT).show();
                }

                // Continue only if the file was successfully created
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(requireContext(),
                            "com.imaginit.hyperplux.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error taking picture: " + e.getMessage());
            Toast.makeText(requireContext(), "Error opening camera", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getFilesDir();
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQUEST_GALLERY_IMAGE);
        } catch (Exception e) {
            Log.e(TAG, "Error opening gallery: " + e.getMessage());
            Toast.makeText(requireContext(), "Error opening gallery", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (resultCode != android.app.Activity.RESULT_OK) return;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                if (currentPhotoPath != null) {
                    // Process and upload captured image
                    processAndUploadImage(Uri.fromFile(new File(currentPhotoPath)));
                }
            } else if (requestCode == REQUEST_GALLERY_IMAGE && data != null) {
                // Process and upload selected image
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    processAndUploadImage(selectedImageUri);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing activity result: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    private void processAndUploadImage(Uri imageUri) {
        try {
            // Show loading state
            binding.progressOverlay.setVisibility(View.VISIBLE);

            // Upload image and send message directly
            viewModel.uploadImageAndSendMessage(chatRoom.getId(), imageUri)
                    .observe(getViewLifecycleOwner(), message -> {
                        binding.progressOverlay.setVisibility(View.GONE);

                        if (message != null) {
                            performHapticFeedback(binding.getRoot());
                        } else {
                            Toast.makeText(requireContext(), "Error sending image", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            binding.progressOverlay.setVisibility(View.GONE);
            Log.e(TAG, "Error processing and uploading image: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing image", Toast.LENGTH_SHORT).show();
        }
    }

    private void initiateCall(boolean isVideoCall) {
        try {
            NetworkMonitor networkMonitor = NetworkMonitor.getInstance(requireContext());
            if (networkMonitor != null && !networkMonitor.isNetworkAvailable()) {
                Toast.makeText(requireContext(), R.string.no_internet_connection_call, Toast.LENGTH_SHORT).show();
                return;
            }

            // Navigate to call screen
            String otherUserId = chatRoom.getOtherParticipantId(currentUserId);
            String otherUserName = chatRoom.getOtherParticipantName(currentUserId);
            String otherUserPhoto = chatRoom.getOtherParticipantPhoto(currentUserId);

            Bundle args = new Bundle();
            args.putString("chatRoomId", chatRoom.getId());
            args.putString("recipientId", otherUserId);
            args.putString("recipientName", otherUserName);
            args.putString("recipientPhoto", otherUserPhoto);
            args.putBoolean("isVideoCall", isVideoCall);
            args.putBoolean("isOutgoing", true);

            Navigation.findNavController(binding.getRoot())
                    .navigate(isVideoCall ?
                                    R.id.action_chatFragment_to_videoCallFragment :
                                    R.id.action_chatFragment_to_audioCallFragment,
                            args);

            // Track call initiated
            if (analyticsTracker != null) {
                Map<String, Object> params = new HashMap<>();
                params.put("call_type", isVideoCall ? "video" : "audio");
                analyticsTracker.trackEvent("call_initiated", params);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initiating call: " + e.getMessage());
            Toast.makeText(requireContext(), "Error initiating call", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            if (requestCode == CAMERA_PERMISSION_CODE) {
                dispatchTakePictureIntent();
            } else if (requestCode == STORAGE_PERMISSION_CODE) {
                openGallery();
            }
        } else {
            int messageResId = (requestCode == CAMERA_PERMISSION_CODE) ?
                    R.string.camera_permission_denied : R.string.storage_permission_denied;
            Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Hide attachment options when leaving fragment
        if (binding != null) {
            binding.attachmentOptionsContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}